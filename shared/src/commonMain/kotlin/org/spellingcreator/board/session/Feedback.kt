package org.spellingcreator.board.session

enum class FeedbackKind {
    /** A key went into the sentence. Flash the cell, say the letter, tap the haptic. */
    KEY_COMMITTED,

    /** The same key again too soon. Show it was noticed, but say nothing. */
    KEY_REPEATED,

    /** Touch-up mode: the finger is resting on this key. */
    KEY_PRESSED,

    /** The finger lifted without choosing anything new. Clear any pressed highlight. */
    KEY_RELEASED,

    /** A touch landed on the view but off the board. */
    OFF_BOARD,

    /** A character was removed. */
    DELETED,

    /** The sentence was cleared. */
    CLEARED,

    /** Speak [Feedback.text] aloud, cutting off anything still playing when [Feedback.interrupt] is set. */
    SAY,

    /** Stop whatever is being spoken, now. */
    STOP_SPEAKING,
}

enum class HapticKind {
    CONFIRM,
    REJECT,
}

/**
 * One thing the UI should do right now. Flat on purpose so Swift can switch on [kind] without casts.
 *
 * @property keyId The key this is about, for highlight kinds.
 * @property text What to speak, for [FeedbackKind.SAY].
 * @property haptic Which haptic to play, if any.
 */
data class Feedback(
    val kind: FeedbackKind,
    val keyId: String? = null,
    val text: String? = null,
    val interrupt: Boolean = false,
    val haptic: HapticKind? = null,
)
