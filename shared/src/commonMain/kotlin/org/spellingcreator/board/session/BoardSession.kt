package org.spellingcreator.board.session

import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.input.SelectionEngine
import org.spellingcreator.board.input.SelectionKind
import org.spellingcreator.board.layout.BoardGeometry
import org.spellingcreator.board.layout.BoardLayout
import org.spellingcreator.board.layout.BoardLayouts
import org.spellingcreator.board.settings.BoardSettings
import org.spellingcreator.board.text.SpelledText

/** A snapshot of everything a board view needs to draw. */
data class BoardState(
    val layout: BoardLayout,
    val text: String,
    val currentWord: String,
    val pressedKeyId: String?,
    val settings: BoardSettings,
)

/** Implemented by the platform UI. Both callbacks arrive on the thread that drove the session. */
interface BoardSessionListener {
    fun onStateChanged(state: BoardState)

    fun onFeedback(feedback: Feedback)
}

/**
 * The one object a platform UI talks to.
 *
 * Feed it the board's size, raw pointer events and the control buttons; it keeps the sentence,
 * decides what each touch means, and tells the listener what to show, say and buzz. Not thread safe:
 * drive it from the UI thread.
 */
class BoardSession(
    settings: BoardSettings = BoardSettings(),
    private val listener: BoardSessionListener,
) {
    var settings: BoardSettings = settings
        private set

    var layout: BoardLayout = BoardLayouts.byId(settings.layoutId)
        private set

    var geometry: BoardGeometry? = null
        private set

    private val engine =
        SelectionEngine(
            commitMode = settings.commitMode,
            refractoryMillis = settings.refractoryMillis,
            stylusOnly = settings.stylusOnly,
        )
    private val spelled = SpelledText()
    private var pressedKeyId: String? = null

    val state: BoardState
        get() =
            BoardState(
                layout = layout,
                text = spelled.text,
                currentWord = spelled.currentWord,
                pressedKeyId = pressedKeyId,
                settings = settings,
            )

    fun updateSettings(newSettings: BoardSettings) {
        settings = newSettings
        engine.commitMode = newSettings.commitMode
        engine.refractoryMillis = newSettings.refractoryMillis
        engine.stylusOnly = newSettings.stylusOnly
        if (layout.id != newSettings.layoutId) {
            layout = BoardLayouts.byId(newSettings.layoutId)
            engine.reset()
            pressedKeyId = null
            geometry?.let { resize(it.width, it.height, it.gap, it.padding) }
        }
        publish()
    }

    fun selectLayout(layoutId: String) = updateSettings(settings.copy(layoutId = layoutId))

    /** Call whenever the board view's size changes. Units are whatever the view uses for touches. */
    fun resize(
        width: Float,
        height: Float,
        gap: Float = 0f,
        padding: Float = 0f,
    ) {
        geometry = BoardGeometry(layout, width, height, gap, padding)
    }

    fun handlePointer(event: PointerEvent) {
        val geometry = geometry ?: return
        val selection = engine.handle(event, geometry)
        when (selection.kind) {
            SelectionKind.NONE, SelectionKind.IGNORED -> return
            SelectionKind.COMMITTED -> {
                val key = selection.key ?: return
                pressedKeyId = null
                spelled.append(key)
                publish()
                emit(Feedback(FeedbackKind.KEY_COMMITTED, keyId = key.id, haptic = HapticKind.CONFIRM))
                if (settings.letterSounds) emit(Feedback(FeedbackKind.SAY, text = key.spoken, interrupt = true))
            }
            SelectionKind.REPEATED -> {
                val key = selection.key ?: return
                emit(Feedback(FeedbackKind.KEY_REPEATED, keyId = key.id, haptic = HapticKind.REJECT))
            }
            SelectionKind.PRESSED -> {
                pressedKeyId = selection.key?.id
                publish()
                emit(Feedback(FeedbackKind.KEY_PRESSED, keyId = pressedKeyId))
            }
            SelectionKind.RELEASED -> {
                if (pressedKeyId != null) {
                    pressedKeyId = null
                    publish()
                }
                emit(Feedback(FeedbackKind.KEY_RELEASED))
            }
            SelectionKind.OFF_BOARD -> {
                pressedKeyId = null
                publish()
                emit(Feedback(FeedbackKind.OFF_BOARD, haptic = HapticKind.REJECT))
            }
        }
    }

    /** Ends the word and reads it back. */
    fun space() {
        val word = spelled.space()
        publish()
        if (word != null && settings.speakWords) emit(Feedback(FeedbackKind.SAY, text = word, interrupt = true))
    }

    fun backspace() {
        val removed = spelled.backspace() ?: return
        publish()
        emit(Feedback(FeedbackKind.DELETED, text = removed, haptic = HapticKind.CONFIRM))
    }

    fun clear() {
        if (spelled.isEmpty) return
        spelled.clear()
        publish()
        emit(Feedback(FeedbackKind.CLEARED, haptic = HapticKind.CONFIRM))
    }

    /** Reads the whole sentence aloud. */
    fun speak() {
        val text = spelled.text.trim()
        if (text.isEmpty()) return
        emit(Feedback(FeedbackKind.SAY, text = text, interrupt = true))
    }

    private fun publish() = listener.onStateChanged(state)

    private fun emit(feedback: Feedback) {
        if (feedback.haptic != null && !settings.haptics) {
            listener.onFeedback(feedback.copy(haptic = null))
        } else {
            listener.onFeedback(feedback)
        }
    }
}
