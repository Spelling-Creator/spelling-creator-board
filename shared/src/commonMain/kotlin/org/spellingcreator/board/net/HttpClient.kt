package org.spellingcreator.board.net

/** The outcome of one GET. [body] is set on any HTTP response; [error] when the request never completed. */
data class HttpResult(
    val status: Int,
    val body: String?,
    val error: String?,
) {
    val isSuccess: Boolean get() = error == null && status in 200..299
}

/** A small GET-only client over the platform's networking. The callback runs on the main thread. */
expect class HttpClient() {
    fun get(
        url: String,
        callback: (HttpResult) -> Unit,
    )
}
