package dev.teyd.justintv.core.chat

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What the socket reports, already split into IRC lines. */
sealed interface IrcEvent {
    data object Connected : IrcEvent
    data class Line(val message: IrcMessage) : IrcEvent
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
    private val client = baseClient.newBuilder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Connects, joins [login], and emits events until the connection ends or fails. */
    fun events(login: String): Flow<IrcEvent> = callbackFlow {
        val channel = "#${login.lowercase()}"
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                    webSocket.send("PASS SCHMOOZE")
                    webSocket.send("NICK ${nick()}")
                    webSocket.send("JOIN $channel")
                    trySend(IrcEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    for (line in text.split("\r\n")) {
                        val message = IrcParser.parse(line) ?: continue
                        if (message.command == "PING") {
                            webSocket.send("PONG :${message.trailing ?: "tmi.twitch.tv"}")
                        } else {
                            trySend(IrcEvent.Line(message))
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }
            },
        )
        awaitClose { socket.close(1000, null) }
    }
}
