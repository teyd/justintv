package dev.teyd.justintv.core.network

import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Shared test setup for the identity and session tests. The production URLs are constants, so
 * instead of a MockWebServer the client gets a scripted interceptor and a same-thread
 * dispatcher: every call completes inline, which keeps coroutine tests deterministic.
 *
 * This is the only place that constructs [TwitchIdentityApi] and [HelixClient]; when their shape changes, fix it here.
 */
internal class FakeTwitch {
    class Reply(
        val code: Int = 200,
        val body: String = "",
        val failure: IOException? = null,
    )

    class Recorded(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    val requests = mutableListOf<Recorded>()
    private val routes = mutableListOf<Pair<String, ArrayDeque<Reply>>>()

    /** Script replies for URLs containing [route] (host + path). The last reply repeats. */
    fun on(
        route: String,
        vararg replies: Reply,
    ) {
        routes += route to ArrayDeque(replies.toList())
    }

    fun on(
        route: String,
        code: Int,
        body: String = "",
    ) = on(route, Reply(code, body))

    fun requestsTo(route: String) = requests.filter { route in it.url }

    private val http = client()
    val api: TwitchIdentityApi = TwitchIdentityApi(http)
    val helix: HelixClient = HelixClient(http)

    private fun client(): OkHttpClient =
        OkHttpClient
            .Builder()
            .dispatcher(Dispatcher(InlineExecutor()))
            .addInterceptor(::answer)
            .build()

    private fun answer(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += record(request)
        val key = request.url.host + request.url.encodedPath
        val queue =
            routes.lastOrNull { key.contains(it.first) }?.second
                ?: throw IOException("Unscripted request: ${request.method} ${request.url}")
        val reply = if (queue.size > 1) queue.removeFirst() else queue.first()
        reply.failure?.let { throw it }
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(reply.code)
            .message("fake")
            .body(reply.body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun record(request: Request) =
        Recorded(
            method = request.method,
            url = request.url.toString(),
            headers = request.headers.toMap(),
            body = request.body?.let { Buffer().also { buffer -> it.writeTo(buffer) }.readUtf8() }.orEmpty(),
        )
}

private class InlineExecutor : AbstractExecutorService() {
    private var down = false

    override fun execute(command: Runnable) = command.run()

    override fun shutdown() {
        down = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        down = true
        return mutableListOf()
    }

    override fun isShutdown() = down

    override fun isTerminated() = down

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ) = true
}

internal class FakeVault(
    initial: StoredSession? = null,
) : TokenVault {
    var session: StoredSession? = initial
        private set
    var clears = 0
        private set
    var saves = 0
        private set

    override suspend fun load() = session

    override suspend fun save(session: StoredSession) {
        saves++
        this.session = session
    }

    override suspend fun clear() {
        clears++
        session = null
    }
}

internal const val TEST_CLIENT_ID = "test-client"

internal fun storedSession(
    expiresInMs: Long = 3_600_000L,
    refreshToken: String = "refresh-1",
    scopes: List<String> = listOf("user:read:follows"),
) = StoredSession(
    accessToken = "access-1",
    refreshToken = refreshToken,
    expiresAtEpochMs = System.currentTimeMillis() + expiresInMs,
    userId = "42",
    login = "viewer",
    displayName = "Viewer",
    scopes = scopes,
)

internal fun userJson(
    id: String = "42",
    login: String = "viewer",
    displayName: String? = "Viewer",
    avatar: String? = null,
): String {
    val fields =
        listOfNotNull(
            "\"id\":\"$id\"",
            "\"login\":\"$login\"",
            displayName?.let { "\"display_name\":\"$it\"" },
            avatar?.let { "\"profile_image_url\":\"$it\"" },
        )
    return "{${fields.joinToString(",")}}"
}

internal fun usersJson(vararg users: String) = """{"data":[${users.joinToString(",")}]}"""

internal fun streamJson(
    id: String,
    userId: String,
    login: String,
    name: String? = null,
) = """
    {"id":"$id","user_id":"$userId","user_login":"$login",
     ${name?.let { "\"user_name\":\"$it\"," }.orEmpty()}
     "title":"Playing","viewer_count":7,"game_name":"Chess","language":"en",
     "started_at":"2026-01-01T00:00:00Z",
     "thumbnail_url":"https://img/{width}x{height}.jpg"}
    """.trimIndent()

internal fun streamsJson(vararg streams: String) = """{"data":[${streams.joinToString(",")}]}"""

internal fun tokenJson(
    access: String,
    refresh: String = "",
    expiresIn: Int = 14_000,
    scopes: String = "",
): String {
    val scopeField = if (scopes.isBlank()) "" else ""","scope":[${scopes.split(" ").joinToString(",") { "\"$it\"" }}]"""
    return """{"access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn$scopeField}"""
}
