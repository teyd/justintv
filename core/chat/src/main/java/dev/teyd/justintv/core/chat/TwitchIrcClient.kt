package dev.teyd.justintv.core.chat

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the socket reports, already split into IRC lines. */
sealed interface IrcEvent {
    data object Connected : IrcEvent

    data class Line(
        val message: IrcMessage,
    ) : IrcEvent
}

/**
 * Anonymous, read-only Twitch chat over IRC on a WebSocket.
 *
 * Anonymous means a `justinfan` nick: no account, no token. Sending messages arrives with
 * login, on a different connection type.
 */
class TwitchIrcClient(
    baseClient: OkHttpClient,
    private val url: String = "wss://irc-ws.chat.twitch.tv:443",
    private val nick: () -> String = { "justinfan${(10_000..99_999).random()}" },
) {
    /**
     * The shared client has a 15 second read timeout, which would drop a quiet chat. A socket
     * needs no read timeout; liveness is checked with protocol pings instead.
     */
    private val client =
        baseClient
            .newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    /** Connects, joins [login], and emits events until the connection ends or fails. */
    fun events(login: String): Flow<IrcEvent> =
        callbackFlow {
            val channel = "#${login.lowercase()}"
            val socket =
                client.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                            webSocket.send("PASS SCHMOOZE")
                            webSocket.send("NICK ${nick()}")
                            webSocket.send("JOIN $channel")
                            trySend(IrcEvent.Connected)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            for (line in text.split("\r\n")) {
                                val message = IrcParser.parse(line) ?: continue
                                if (message.command == "PING") {
                                    webSocket.send("PONG :${message.trailing ?: "tmi.twitch.tv"}")
                                } else {
                                    trySend(IrcEvent.Line(message))
                                }
                            }
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(code, reason)
                            close()
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            close(t)
                        }
                    },
                )
            awaitClose { socket.close(1000, null) }
        }

    /**
     * Sends one message on its own socket.
     *
     * This is not the read connection. The anonymous `justinfan` login cannot send, and the
     * user token must not be attached to it.
     */
    suspend fun sendMessage(
        channelLogin: String,
        nick: String,
        accessToken: String,
        text: String,
    ) {
        val body = text.replace('\n', ' ').trim()
        if (body.isEmpty()) return
        if (body.length > MAX_MESSAGE_LENGTH) {
            throw ChatSendException("Message is too long")
        }
        val channel = "#${channelLogin.lowercase()}"
        withTimeout(SEND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val socket =
                    client.newWebSocket(
                        Request.Builder().url(url).build(),
                        object : WebSocketListener() {
                            private var sent = false

                            override fun onOpen(
                                webSocket: WebSocket,
                                response: Response,
                            ) {
                                webSocket.send("PASS oauth:$accessToken")
                                webSocket.send("NICK ${nick.lowercase()}")
                                webSocket.send("JOIN $channel")
                            }

                            override fun onMessage(
                                webSocket: WebSocket,
                                text: String,
                            ) {
                                for (line in text.split("\r\n")) {
                                    val message = IrcParser.parse(line) ?: continue
                                    if (message.command == "PING") {
                                        webSocket.send("PONG :${message.trailing ?: "tmi.twitch.tv"}")
                                    }
                                    val notice = message.trailing.orEmpty()
                                    if (notice.contains("authentication failed", ignoreCase = true) ||
                                        notice.contains("Login unsuccessful", ignoreCase = true)
                                    ) {
                                        finish(webSocket, ChatSendException("Twitch refused the login for chat"))
                                        return
                                    }
                                    if (!sent && (message.command == "001" || message.command == "JOIN" || message.command == "366")) {
                                        sent = true
                                        val ok = webSocket.send("PRIVMSG $channel :$body")
                                        finish(
                                            webSocket,
                                            if (ok) null else ChatSendException("Could not send"),
                                        )
                                        return
                                    }
                                }
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                t: Throwable,
                                response: Response?,
                            ) {
                                finish(webSocket, t)
                            }

                            private fun finish(
                                webSocket: WebSocket,
                                error: Throwable?,
                            ) {
                                webSocket.close(1000, null)
                                if (!continuation.isActive) return
                                if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
                            }
                        },
                    )
                continuation.invokeOnCancellation { socket.cancel() }
            }
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 500
        const val SEND_TIMEOUT_MS = 8_000L
    }
}

class ChatSendException(
    message: String,
) : Exception(message)
