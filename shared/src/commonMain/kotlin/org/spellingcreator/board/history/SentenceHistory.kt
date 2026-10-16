package org.spellingcreator.board.history

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The sentences a speller has finished, newest first, kept on the device.
 *
 * Privacy comes first: the partner can delete one entry or all of them at any time, and turning
 * history off in settings deletes everything as well. Nothing is recorded while it is off.
 */
class SentenceHistory(
    private val store: HistoryStore,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    var entries: List<HistoryEntry> = store.load()
        private set

    private var lastRecordedText: String? = entries.firstOrNull()?.text

    /** Adds a sentence unless it is blank or the same as the one recorded last. Returns whether it was added. */
    @OptIn(ExperimentalUuidApi::class)
    fun record(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == lastRecordedText) return false
        val entry = HistoryEntry(id = Uuid.random().toString(), text = trimmed, timeMillis = epochMillis())
        entries = (listOf(entry) + entries).take(maxEntries)
        lastRecordedText = trimmed
        store.save(entries)
        return true
    }

    fun entry(id: String): HistoryEntry? = entries.firstOrNull { it.id == id }

    fun remove(id: String) {
        val remaining = entries.filterNot { it.id == id }
        if (remaining.size == entries.size) return
        entries = remaining
        lastRecordedText = remaining.firstOrNull()?.text
        store.save(entries)
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries = emptyList()
        lastRecordedText = null
        store.save(entries)
    }

    /** Every sentence as one block of text, oldest first, for sharing. */
    fun asText(): String = entries.asReversed().joinToString("\n") { it.text }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
