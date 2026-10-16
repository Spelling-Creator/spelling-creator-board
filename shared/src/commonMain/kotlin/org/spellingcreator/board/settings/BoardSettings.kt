package org.spellingcreator.board.settings

import kotlinx.serialization.Serializable
import org.spellingcreator.board.input.CommitMode

/** Per-speller preferences. Everything here is a plain value so both apps can show it in a native settings screen. */
@Serializable
data class BoardSettings(
    val layoutId: String = "alphabet",
    val style: BoardStyle = BoardStyle.STENCIL,
    val commitMode: CommitMode = CommitMode.ON_TOUCH_DOWN,
    /** How long after choosing a key the same key is treated as an accidental repeat. */
    val refractoryMillis: Long = 400,
    val stylusOnly: Boolean = false,
    val letterSounds: Boolean = true,
    val speakWords: Boolean = true,
    val haptics: Boolean = true,
    /** A platform voice identifier, or null to use the best voice installed for the current language. */
    val voiceId: String? = null,
) {
    companion object {
        const val MIN_REFRACTORY_MILLIS: Long = 0
        const val MAX_REFRACTORY_MILLIS: Long = 1500
    }
}
