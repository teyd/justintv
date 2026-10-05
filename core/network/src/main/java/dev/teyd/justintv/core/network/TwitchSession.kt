package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.LiveStream
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the account screen renders. The token is not part of this. */
sealed interface AuthState {
    data object LoggedOut : AuthState
    data class Pending(val userCode: String, val verificationUri: String) : AuthState
    data class LoggedIn(
        val userId: String,
        val login: String,
        val displayName: String,
        val canChat: Boolean = false,
    ) : AuthState
    data class Failed(val message: String) : AuthState
}

/**
 * Device-code sign-in for the whole process.
 *
 * The process owns the poll: it has to survive leaving the account screen, and a newer [start]
 * or [logout] cancels the one in flight. Playback never asks this class for a token.
 */
class TwitchSession(
    private val api: TwitchIdentityApi,
    private val vault: TokenVault,
    private val clientId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshLock = Mutex()
    private var pollJob: Job? = null
    private var stored: StoredSession? = null

    init {
        scope.launch { restore() }
    }

    fun start(scopes: String = TwitchIdentityApi.SCOPES) {
        pollJob?.cancel()
        pollJob = scope.launch {
            if (clientId.isBlank()) {
                _state.value = AuthState.Failed("This build has no Twitch client ID.")
                return@launch
            }
            try {
                val code = requestDeviceCode(scopes)
                _state.value = AuthState.Pending(code.userCode, code.verificationUri)
                val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
                var interval = code.intervalSeconds
                while (System.currentTimeMillis() < deadline) {
                    delay(interval * 1000L)
                    when (val poll = api.pollDeviceCode(clientId, scopes, code.deviceCode)) {
                        DevicePoll.Pending -> Unit
                        DevicePoll.SlowDown -> interval += SLOW_DOWN_SECONDS
                        is DevicePoll.Rejected -> {
                            _state.value = AuthState.Failed(poll.message)
                            return@launch
                        }
                        is DevicePoll.Granted -> {
                            finish(poll.grant)
                            return@launch
                        }
                    }
                }
                _state.value = AuthState.Failed("That code expired. Try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AuthState.Failed(signInFailure(e))
            }
        }
    }

    /** Re-consent so the token can send chat. Follows stay included. */
    fun grantChat() = start(TwitchIdentityApi.CHAT_SCOPES)

    /** A fresh access token for the signed-in user, or null when logged out. */
    suspend fun accessToken(): String? = freshAccessToken()

    fun logout() {
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            vault.clear()
            stored = null
            _state.value = AuthState.LoggedOut
        }
    }

    /** Live channels the signed-in user follows. Refreshes the token once on a 401. */
    suspend fun followedStreams(): List<LiveStream> {
        val token = freshAccessToken() ?: throw IdentityException("Not signed in")
        val userId = stored?.userId ?: throw IdentityException("Not signed in")
        return try {
            api.followedStreams(clientId, token, userId)
        } catch (_: UnauthorizedException) {
            val refreshed = refreshLocked() ?: throw IdentityException("Sign-in expired")
            api.followedStreams(clientId, refreshed, userId)
        }
    }

    private suspend fun requestDeviceCode(scopes: String): DeviceCode {
        var last: Exception? = null
        repeat(DEVICE_CODE_ATTEMPTS) { attempt ->
            try {
                return api.requestDeviceCode(clientId, scopes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (attempt == DEVICE_CODE_ATTEMPTS - 1 || !isDnsFailure(e)) throw e
                delay(DNS_RETRY_MS)
            }
        }
        throw last ?: IdentityException("Could not sign in")
    }

    private suspend fun restore() {
        val saved = vault.load() ?: return
        stored = saved
        _state.value = loggedIn(saved.userId, saved.login, saved.displayName, saved.scopes)
        try {
            freshAccessToken()
            val user = api.currentUser(clientId, stored?.accessToken ?: return)
            saveUser(user, stored?.refreshToken.orEmpty(), stored?.expiresAtEpochMs ?: 0L)
        } catch (_: UnauthorizedException) {
            if (refreshLocked() == null) drop()
        } catch (_: Exception) {
            // Keep the saved account on a network miss. The next Helix call can refresh.
        }
    }

    private suspend fun finish(grant: TokenGrant) {
        val user = api.currentUser(clientId, grant.accessToken)
        val expiresAt = System.currentTimeMillis() + grant.expiresInSeconds * 1000L
        saveUser(user, grant.refreshToken, expiresAt, grant.accessToken, grant.scopes)
    }

    private suspend fun saveUser(
        user: TwitchUser,
        refreshToken: String,
        expiresAtEpochMs: Long,
        accessToken: String = stored?.accessToken.orEmpty(),
        scopes: List<String> = stored?.scopes.orEmpty(),
    ) {
        val session = StoredSession(
            accessToken = accessToken,
            refreshToken = refreshToken.ifBlank { stored?.refreshToken.orEmpty() },
            expiresAtEpochMs = expiresAtEpochMs,
            userId = user.id,
            login = user.login,
            displayName = user.displayName,
            scopes = scopes,
        )
        vault.save(session)
        stored = session
        _state.value = loggedIn(user.id, user.login, user.displayName, scopes)
    }

    private fun loggedIn(userId: String, login: String, displayName: String, scopes: List<String>) =
        AuthState.LoggedIn(
            userId = userId,
            login = login,
            displayName = displayName,
            canChat = scopes.any { it.equals("chat:edit", ignoreCase = true) },
        )

    private suspend fun freshAccessToken(): String? {
        val current = stored ?: vault.load()?.also { stored = it } ?: return null
        if (current.expiresAtEpochMs - System.currentTimeMillis() > REFRESH_EARLY_MS) {
            return current.accessToken
        }
        return refreshLocked()
    }

    private suspend fun refreshLocked(): String? = refreshLock.withLock {
        val current = stored ?: return null
        if (current.refreshToken.isBlank()) {
            drop()
            return null
        }
        try {
            val grant = api.refresh(clientId, current.refreshToken)
            val expiresAt = System.currentTimeMillis() + grant.expiresInSeconds * 1000L
            val scopes = grant.scopes.ifEmpty { current.scopes }
            val updated = current.copy(
                accessToken = grant.accessToken,
                refreshToken = grant.refreshToken.ifBlank { current.refreshToken },
                expiresAtEpochMs = expiresAt,
                scopes = scopes,
            )
            vault.save(updated)
            stored = updated
            _state.value = loggedIn(updated.userId, updated.login, updated.displayName, scopes)
            updated.accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            drop()
            null
        }
    }

    private suspend fun drop() {
        vault.clear()
        stored = null
        _state.value = AuthState.LoggedOut
    }

    private companion object {
        const val REFRESH_EARLY_MS = 60_000L
        const val SLOW_DOWN_SECONDS = 5
        const val DEVICE_CODE_ATTEMPTS = 3
        const val DNS_RETRY_MS = 700L

        fun isDnsFailure(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (current is UnknownHostException) return true
                val message = current.message.orEmpty()
                if (message.contains("Unable to resolve host", ignoreCase = true) ||
                    message.contains("No address associated", ignoreCase = true)
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun signInFailure(error: Exception): String = if (isDnsFailure(error)) {
            "Couldn't reach Twitch. Check the connection and try again."
        } else {
            error.message ?: "Could not sign in"
        }
    }
}
