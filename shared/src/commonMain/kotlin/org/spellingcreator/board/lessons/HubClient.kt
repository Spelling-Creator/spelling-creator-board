package org.spellingcreator.board.lessons

import org.spellingcreator.board.net.HttpClient

/**
 * Talks to a Spelling Creator hub's public lesson endpoints, read only and with no account:
 * `GET /lessons` for the listing and `GET /lessons/:id` for one lesson. Callbacks run on the main
 * thread with either a value or a message to show.
 */
class HubClient(
    private val http: HttpClient = HttpClient(),
) {
    fun listLessons(
        baseUrl: String,
        callback: (List<LessonSummary>?, String?) -> Unit,
    ) {
        http.get(HubLinks.listingUrl(baseUrl)) { result ->
            when {
                result.error != null -> callback(null, "Could not reach the hub: ${result.error}")
                !result.isSuccess -> callback(null, "The hub answered with status ${result.status}.")
                else -> callback(LessonParser.parseListing(result.body.orEmpty()), null)
            }
        }
    }

    fun fetchLesson(
        address: LessonAddress,
        callback: (Lesson?, String?) -> Unit,
    ) {
        http.get(HubLinks.lessonUrl(address)) { result ->
            when {
                result.error != null -> callback(null, "Could not reach the hub: ${result.error}")
                result.status == 404 -> callback(null, "No lesson with that id on the hub.")
                !result.isSuccess -> callback(null, "The hub answered with status ${result.status}.")
                else -> {
                    val lesson = LessonParser.parseLesson(result.body.orEmpty())
                    if (lesson == null) callback(null, "That did not look like a lesson.") else callback(lesson, null)
                }
            }
        }
    }
}
