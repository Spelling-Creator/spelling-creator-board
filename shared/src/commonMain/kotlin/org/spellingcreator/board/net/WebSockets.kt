package org.spellingcreator.board.net

/** One open WebSocket. */
interface WebSocketConnection {
    fun send(text: String)

    fun close()
}

/** Events from a WebSocket. Every callback runs on the main thread. */
interface WebSocketEvents {
    fun onOpen()

    fun onMessage(text: String)

    /** The socket closed, by either side. [reason] is the close reason if one was given. */
    fun onClosed(reason: String?)

    /** The connection failed or dropped. */
    fun onFailure(message: String)
}

/** Opens WebSockets. The platform actuals keep the connection alive with pings on their own. */
interface WebSocketFactory {
    fun connect(
        url: String,
        events: WebSocketEvents,
    ): WebSocketConnection
}

expect fun platformWebSockets(): WebSocketFactory
