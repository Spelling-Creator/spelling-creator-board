package org.spellingcreator.board.history

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where the history lives. The apps use [PreferencesHistoryStore]; tests use [InMemoryHistoryStore]. */
interface HistoryStore {
    fun load(): List<HistoryEntry>

    fun save(entries: List<HistoryEntry>)
}

/** Keeps the history in the platform's preference store as one JSON string. Nothing leaves the device. */
expect class PreferencesHistoryStore : HistoryStore {
    override fun load(): List<HistoryEntry>

    override fun save(entries: List<HistoryEntry>)
}

class InMemoryHistoryStore : HistoryStore {
    private var entries: List<HistoryEntry> = emptyList()

    override fun load(): List<HistoryEntry> = entries

    override fun save(entries: List<HistoryEntry>) {
        this.entries = entries
    }
}

/** Milliseconds since the Unix epoch, from the platform clock. */
expect fun epochMillis(): Long

internal object HistoryCodec {
    const val KEY = "board-history"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(HistoryEntry.serializer())

    fun encode(entries: List<HistoryEntry>): String = json.encodeToString(serializer, entries)

    fun decode(text: String?): List<HistoryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
    }
}
