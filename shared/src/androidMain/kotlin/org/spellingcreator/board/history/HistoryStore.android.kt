package org.spellingcreator.board.history

import android.content.Context
import android.content.SharedPreferences

actual class PreferencesHistoryStore(
    context: Context,
) : HistoryStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("board", Context.MODE_PRIVATE)

    actual override fun load(): List<HistoryEntry> = HistoryCodec.decode(prefs.getString(HistoryCodec.KEY, null))

    actual override fun save(entries: List<HistoryEntry>) {
        prefs.edit().putString(HistoryCodec.KEY, HistoryCodec.encode(entries)).apply()
    }
}

actual fun epochMillis(): Long = System.currentTimeMillis()
