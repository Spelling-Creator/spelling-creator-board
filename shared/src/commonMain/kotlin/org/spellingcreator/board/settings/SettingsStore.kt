package org.spellingcreator.board.settings

import kotlinx.serialization.json.Json

/** Saves [BoardSettings] in the platform's preference store as one JSON string. */
expect class SettingsStore {
    fun load(): BoardSettings

    fun save(settings: BoardSettings)
}

internal object SettingsCodec {
    const val KEY = "board-settings"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: BoardSettings): String = json.encodeToString(BoardSettings.serializer(), settings)

    fun decode(text: String?): BoardSettings {
        if (text.isNullOrBlank()) return BoardSettings()
        return runCatching { json.decodeFromString(BoardSettings.serializer(), text) }.getOrDefault(BoardSettings())
    }
}
