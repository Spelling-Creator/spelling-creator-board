package org.spellingcreator.board.settings

import android.content.Context
import android.content.SharedPreferences

actual class SettingsStore(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("board", Context.MODE_PRIVATE)

    actual fun load(): BoardSettings = SettingsCodec.decode(prefs.getString(SettingsCodec.KEY, null))

    actual fun save(settings: BoardSettings) {
        prefs.edit().putString(SettingsCodec.KEY, SettingsCodec.encode(settings)).apply()
    }
}
