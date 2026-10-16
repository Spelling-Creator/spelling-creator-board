package org.spellingcreator.board.net

import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class HttpClient {
    actual fun get(
        url: String,
        callback: (HttpResult) -> Unit,
    ) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            callback(HttpResult(status = 0, body = null, error = "That is not a valid address."))
            return
        }
        val request = NSMutableURLRequest(uRL = nsUrl)
        request.setValue("application/json", forHTTPHeaderField = "Accept")
        request.setTimeoutInterval(20.0)
        val task =
            NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                val result =
                    if (error != null) {
                        HttpResult(status = 0, body = null, error = error.localizedDescription)
                    } else {
                        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                        val body = data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
                        HttpResult(status = status, body = body, error = null)
                    }
                dispatch_async(dispatch_get_main_queue()) { callback(result) }
            }
        task.resume()
    }
}
