package org.spellingcreator.board.net

import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class FoundationWebSockets : WebSocketFactory {
    override fun connect(
        url: String,
        events: WebSocketEvents,
    ): WebSocketConnection {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            dispatch_async(dispatch_get_main_queue()) { events.onFailure("That is not a valid address.") }
            return object : WebSocketConnection {
                override fun send(text: String) = Unit

                override fun close() = Unit
            }
        }
        val task = NSURLSession.sharedSession.webSocketTaskWithURL(nsUrl)
        val connection = FoundationConnection(task, events)
        connection.start()
        return connection
    }
}

private class FoundationConnection(
    private val task: NSURLSessionWebSocketTask,
    private val events: WebSocketEvents,
) : WebSocketConnection {
    private var open = false
    private var pinger: NSTimer? = null

    fun start() {
        task.resume()
        // Foundation has no open callback; the first successful receive, or a ping answered, means we are in.
        receive()
        task.sendPingWithPongReceiveHandler { error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (error != null) {
                    fail(error.localizedDescription)
                } else {
                    markOpen()
                }
            }
        }
    }

    private fun markOpen() {
        if (open) return
        open = true
        events.onOpen()
        pinger =
            NSTimer.scheduledTimerWithTimeInterval(25.0, repeats = true) {
                task.sendPingWithPongReceiveHandler { error ->
                    if (error != null) dispatch_async(dispatch_get_main_queue()) { fail(error.localizedDescription) }
                }
            }
    }

    private fun receive() {
        task.receiveMessageWithCompletionHandler { message, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (error != null) {
                    fail(error.localizedDescription)
                    return@dispatch_async
                }
                markOpen()
                message?.string?.let { events.onMessage(it) }
                receive()
            }
        }
    }

    private fun fail(message: String) {
        if (pinger == null && !open) {
            events.onFailure(message)
            return
        }
        stopPinging()
        if (task.closeCode == NSURLSessionWebSocketCloseCodeNormalClosure) {
            events.onClosed(null)
        } else {
            events.onFailure(message)
        }
        open = false
    }

    private fun stopPinging() {
        pinger?.invalidate()
        pinger = null
    }

    override fun send(text: String) {
        task.sendMessage(NSURLSessionWebSocketMessage(text)) { }
    }

    override fun close() {
        stopPinging()
        task.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
        open = false
    }
}

actual fun platformWebSockets(): WebSocketFactory = FoundationWebSockets()
