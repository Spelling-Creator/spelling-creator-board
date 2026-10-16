package org.spellingcreator.board.lessons

/** Where a lesson lives: the hub to ask and the lesson's id on it. */
data class LessonAddress(
    val baseUrl: String,
    val id: String,
)

/**
 * Reads what a partner pastes into the lesson field. A hub link like
 * `https://spellingcreator.org/hub/<id>` or `.../hub/<id>/practice` names its own hub, so a link
 * from a self-hosted instance opens there even when settings point elsewhere. A bare id uses the
 * configured hub.
 */
object HubLinks {
    const val DEFAULT_HUB_URL = "https://spellingcreator.org"

    /** A hub URL as settings store it: scheme and host, no trailing slash, `https` assumed if missing. */
    fun normalizeBaseUrl(input: String): String {
        var url = input.trim().trimEnd('/')
        if (url.isEmpty()) return DEFAULT_HUB_URL
        if (!url.contains("://")) url = "https://$url"
        return url
    }

    fun parse(
        input: String,
        defaultBaseUrl: String,
    ): LessonAddress? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val base = normalizeBaseUrl(defaultBaseUrl)
        if (!text.contains("://") && !text.contains('/')) return LessonAddress(base, text)
        val withScheme = if (text.contains("://")) text else "https://$text"
        val schemeEnd = withScheme.indexOf("://") + 3
        val pathStart = withScheme.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: withScheme.length
        val origin = withScheme.substring(0, pathStart)
        val path = withScheme.substring(pathStart).substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        val hubIndex = segments.indexOf("hub")
        val id =
            when {
                hubIndex >= 0 && hubIndex + 1 < segments.size -> segments[hubIndex + 1]
                segments.size == 1 -> segments[0]
                else -> return null
            }
        return LessonAddress(baseUrl = origin, id = id)
    }

    fun lessonUrl(address: LessonAddress): String = "${address.baseUrl}/lessons/${address.id}"

    fun listingUrl(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/lessons"
}
