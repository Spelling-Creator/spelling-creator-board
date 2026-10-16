package org.spellingcreator.board.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.spellingcreator.board.history.PreferencesHistoryStore
import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.lessons.HubClient
import org.spellingcreator.board.lessons.HubLinks
import org.spellingcreator.board.lessons.LessonSummary
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

    val session: BoardSession =
        BoardSession(
            settings = store.load(),
            listener = listener,
            historyStore = PreferencesHistoryStore(application),
        )

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

    // Lessons from a Spelling Creator hub. Read only, no account: the hub's public endpoints.
    private val hub = HubClient()

    var lessonListing: List<LessonSummary>? by mutableStateOf(null)
        private set
    var lessonError: String? by mutableStateOf(null)
        private set
    var lessonLoading: Boolean by mutableStateOf(false)
        private set

    fun loadLessonListing() {
        lessonLoading = true
        lessonError = null
        hub.listLessons(session.settings.hubUrl) { list, error ->
            lessonLoading = false
            lessonListing = list
            lessonError = error
        }
    }

    /** Opens a lesson from a hub link or a bare id. [onOpened] runs once it is on the board. */
    fun openLesson(
        input: String,
        onOpened: () -> Unit,
    ) {
        val address = HubLinks.parse(input, session.settings.hubUrl)
        if (address == null) {
            lessonError = "Paste a lesson link or id from the hub."
            return
        }
        lessonLoading = true
        lessonError = null
        hub.fetchLesson(address) { lesson, error ->
            lessonLoading = false
            lessonError = error
            if (lesson != null) {
                session.startLesson(lesson)
                onOpened()
            }
        }
    }

    fun endLesson() = session.endLesson()

    fun nextStep() = session.nextStep()

    fun previousStep() = session.previousStep()

    fun submitAnswer() = session.submitAnswer()

    fun speakStep() = session.speakStep()

    fun stopSpeaking() = session.stopSpeaking()

    val lessonAnswersText: String get() = session.lessonAnswersText

    fun recordSentence() = session.recordSentence()

    fun speakHistoryEntry(id: String) = session.speakHistoryEntry(id)

    fun restoreHistoryEntry(id: String) = session.restoreHistoryEntry(id)

    fun deleteHistoryEntry(id: String) = session.deleteHistoryEntry(id)

    fun clearHistory() = session.clearHistory()

    val historyText: String get() = session.historyText
}
