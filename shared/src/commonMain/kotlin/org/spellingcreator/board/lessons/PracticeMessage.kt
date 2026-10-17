package org.spellingcreator.board.lessons

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One message on a letterboard session, in either direction. JSON text frames, one object each,
 * told apart by [type]. Every field but [type] is optional so a newer side can add fields without
 * breaking an older one. The protocol is written up in `docs/live-sync.md`.
 */
@Serializable
data class PracticeMessage(
    val type: String,
    /** `join`: what is joining. Always "board" from here. */
    val client: String? = null,
    /** `hello`, `state`: the hub lesson id the host has open, or null when none is open. */
    val lessonId: String? = null,
    /** `state`, `step`: the step index, from 0, in the walkthrough `LessonSteps` builds. */
    val step: Int? = null,
    /** `state`: the text in the host's answer box; `text` and `answer`: the board's sentence. */
    val text: String? = null,
    /** `removed`, `error`: what to tell the partner. */
    val message: String? = null,
) {
    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }

        fun encode(message: PracticeMessage): String = json.encodeToString(serializer(), message)

        /** Null for anything that is not one of ours; a session must survive a stray frame. */
        fun decode(text: String): PracticeMessage? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        // Board to host.
        const val JOIN = "join"
        const val TEXT = "text"
        const val ANSWER = "answer"
        const val STEP = "step"

        // Host to board.
        const val HELLO = "hello"
        const val STATE = "state"
        const val REMOVED = "removed"
        const val ERROR = "error"
    }
}
