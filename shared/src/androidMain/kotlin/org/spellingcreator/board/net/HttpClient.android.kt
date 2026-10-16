package org.spellingcreator.board.net

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

actual class HttpClient {
    private val main = Handler(Looper.getMainLooper())

    actual fun get(
        url: String,
        callback: (HttpResult) -> Unit,
    ) {
        thread(name = "board-http") {
            val result =
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 20_000
                        connection.setRequestProperty("Accept", "application/json")
                        val status = connection.responseCode
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader()?.use { it.readText() }
                        HttpResult(status = status, body = body, error = null)
                    } finally {
                        connection.disconnect()
                    }
                }.getOrElse { HttpResult(status = 0, body = null, error = it.message ?: "Could not reach the hub.") }
            main.post { callback(result) }
        }
    }
}
