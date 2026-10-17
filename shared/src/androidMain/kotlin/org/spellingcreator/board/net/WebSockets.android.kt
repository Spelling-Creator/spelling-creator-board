package org.spellingcreator.board.net

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

private val client: OkHttpClient by lazy {
    OkHttpClient
        .Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
}

private class OkHttpWebSockets : WebSocketFactory {
    private val main = Handler(Looper.getMainLooper())

    override fun connect(
        url: String,
        events: WebSocketEvents,
    ): WebSocketConnection {
        val socket =
            client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        main.post { events.onOpen() }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        main.post { events.onMessage(text) }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        main.post { events.onClosed(reason.ifBlank { null }) }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        val message =
                            response?.let { "${it.code} ${it.message}".trim() }?.ifBlank { null } ?: t.message
                                ?: "Connection failed."
                        main.post { events.onFailure(message) }
                    }
                },
            )
        return object : WebSocketConnection {
            override fun send(text: String) {
                socket.send(text)
            }

            override fun close() {
                socket.close(1000, "left")
            }
        }
    }
}

actual fun platformWebSockets(): WebSocketFactory = OkHttpWebSockets()
