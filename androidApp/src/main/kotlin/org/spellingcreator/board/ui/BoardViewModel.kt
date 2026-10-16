package org.spellingcreator.board.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.session.BoardSession
import org.spellingcreator.board.session.BoardSessionListener
import org.spellingcreator.board.session.BoardState
import org.spellingcreator.board.session.Feedback
import org.spellingcreator.board.settings.BoardSettings
import org.spellingcreator.board.settings.SettingsStore

/**
 * Owns the shared [BoardSession] so the sentence survives rotation. Feedback is handed to whatever
 * the screen registers in [feedbackSink], which is where sound, haptics and flashes happen.
 */
class BoardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    private val listener =
        object : BoardSessionListener {
            override fun onStateChanged(state: BoardState) {
                mutableState.value = state
            }

            override fun onFeedback(feedback: Feedback) {
                feedbackSink?.invoke(feedback)
            }
        }

    val session: BoardSession = BoardSession(settings = store.load(), listener = listener)

    private val mutableState: MutableState<BoardState> = mutableStateOf(session.state)
    val state: State<BoardState> = mutableState

    var feedbackSink: ((Feedback) -> Unit)? = null

    fun resize(
        width: Float,
        height: Float,
        gap: Float,
        padding: Float,
    ) = session.resize(width, height, gap, padding)

    fun onPointer(event: PointerEvent) = session.handlePointer(event)

    fun updateSettings(settings: BoardSettings) {
        session.updateSettings(settings)
        store.save(settings)
    }

    fun selectLayout(id: String) = updateSettings(session.settings.copy(layoutId = id))

    fun space() = session.space()

    fun backspace() = session.backspace()

    fun clear() = session.clear()

    fun speak() = session.speak()
}
