package org.spellingcreator.board.history

import kotlinx.serialization.Serializable

/** One sentence the speller finished, with when it was finished. */
@Serializable
data class HistoryEntry(
    val id: String,
    val text: String,
    /** Milliseconds since the Unix epoch. */
    val timeMillis: Long,
)
