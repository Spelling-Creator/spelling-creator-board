package org.spellingcreator.board.session

import org.spellingcreator.board.history.HistoryEntry
import org.spellingcreator.board.history.HistoryStore
import org.spellingcreator.board.history.InMemoryHistoryStore
import org.spellingcreator.board.history.SentenceHistory
import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.input.SelectionEngine
import org.spellingcreator.board.input.SelectionKind
import org.spellingcreator.board.layout.BoardGeometry
import org.spellingcreator.board.layout.BoardLayout
import org.spellingcreator.board.layout.BoardLayouts
import org.spellingcreator.board.lessons.Lesson
import org.spellingcreator.board.lessons.LessonRun
import org.spellingcreator.board.lessons.LessonStepKind
import org.spellingcreator.board.lessons.LessonSteps
import org.spellingcreator.board.settings.BoardSettings
import org.spellingcreator.board.text.SpelledText

/** A snapshot of everything a board view needs to draw. */
data class BoardState(
    val layout: BoardLayout,
    val text: String,
    val currentWord: String,
    val pressedKeyId: String?,
    val settings: BoardSettings,
    /** Finished sentences, newest first. Empty while history is off. */
    val history: List<HistoryEntry>,
    /** The open lesson's current step, or null when no lesson is open. */
    val lesson: LessonView? = null,
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
    historyStore: HistoryStore = InMemoryHistoryStore(),
) {
    private val history = SentenceHistory(historyStore)

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
    private var run: LessonRun? = null

    val state: BoardState
        get() =
            BoardState(
                layout = layout,
                text = spelled.text,
                currentWord = spelled.currentWord,
                pressedKeyId = pressedKeyId,
                settings = settings,
                history = history.entries,
                lesson = lessonView(),
            )

    private fun lessonView(): LessonView? {
        val run = run ?: return null
        val step = run.current ?: return null
        return LessonView(
            lessonId = run.lesson.id,
            title = run.lesson.title,
            author = run.lesson.author,
            stepIndex = run.index,
            stepCount = run.steps.size,
            kind = step.kind,
            sectionName = step.sectionName,
            blocks = step.blocks,
            question = step.question,
            questionNumber = step.questionNumber,
            answer = run.answer(step),
            answeredCount = run.answeredCount,
            questionCount = run.questionCount,
            hasPrevious = run.hasPrevious,
            hasNext = run.hasNext,
        )
    }

    /** Opens a lesson at its first step. A lesson with nothing in it is ignored. */
    fun startLesson(lesson: Lesson) {
        val next = LessonRun(lesson)
        if (next.steps.isEmpty()) return
        run = next
        publish()
    }

    fun endLesson() {
        if (run == null) return
        run = null
        stopSpeaking()
        publish()
    }

    /** Moving between steps cuts off a step being read aloud, so the voice never runs ahead of the screen. */
    fun nextStep() {
        if (run?.next() == true) {
            stopSpeaking()
            publish()
        }
    }

    fun previousStep() {
        if (run?.previous() == true) {
            stopSpeaking()
            publish()
        }
    }

    /** Stops anything being spoken: a step, a word, a sentence. */
    fun stopSpeaking() {
        emit(Feedback(FeedbackKind.STOP_SPEAKING))
    }

    /**
     * Hands the sentence on the board to the lesson as the answer to the current question, keeps
     * it in the history, clears the board and moves on. On a content step it just moves on.
     */
    fun submitAnswer() {
        val run = run ?: return
        val step = run.current ?: return
        if (step.kind == LessonStepKind.QUESTION) {
            val answer = spelled.text.trim()
            if (answer.isEmpty()) return
            run.record(step, answer)
            recordSentence()
            spelled.clear()
            emit(Feedback(FeedbackKind.CLEARED, haptic = HapticKind.CONFIRM))
        }
        run.next()
        stopSpeaking()
        publish()
    }

    /** Reads the current step aloud: the section name, then its material or its question. */
    fun speakStep() {
        val step = run?.current ?: return
        val text = LessonSteps.speech(step)
        if (text.isNotBlank()) emit(Feedback(FeedbackKind.SAY, text = text, interrupt = true))
    }

    /** Every answer given so far, as "question" then "answer" lines, for sharing. */
    val lessonAnswersText: String
        get() {
            val run = run ?: return ""
            return run.steps
                .filter { it.kind == LessonStepKind.QUESTION && run.answer(it) != null }
                .joinToString("\n\n") { step -> "${step.question?.prompt.orEmpty()}\n${run.answer(step).orEmpty()}" }
        }

    /** Every kept sentence as one block of text, oldest first, for sharing. */
    val historyText: String get() = history.asText()

    fun updateSettings(newSettings: BoardSettings) {
        settings = newSettings
        engine.commitMode = newSettings.commitMode
        engine.refractoryMillis = newSettings.refractoryMillis
        engine.stylusOnly = newSettings.stylusOnly
        // Turning history off is a privacy choice, so it also deletes what was kept.
        if (!newSettings.keepHistory) history.clear()
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

    /** Clears the board. The sentence goes into the history first, so Clear never loses it. */
    fun clear() {
        if (spelled.isEmpty) return
        recordSentence()
        spelled.clear()
        publish()
        emit(Feedback(FeedbackKind.CLEARED, haptic = HapticKind.CONFIRM))
    }

    /** Reads the whole sentence aloud and keeps it in the history. */
    fun speak() {
        val text = spelled.text.trim()
        if (text.isEmpty()) return
        recordSentence()
        emit(Feedback(FeedbackKind.SAY, text = text, interrupt = true))
    }

    /**
     * Keeps the current sentence in the history, if history is on. Speak and Clear call this, and
     * the apps call it when the sentence is shared or copied. The same sentence is not kept twice
     * in a row, so speaking and then clearing records it once.
     */
    fun recordSentence() {
        if (!settings.keepHistory) return
        if (history.record(spelled.text)) publish()
    }

    fun speakHistoryEntry(id: String) {
        val entry = history.entry(id) ?: return
        emit(Feedback(FeedbackKind.SAY, text = entry.text, interrupt = true))
    }

    /** Puts a kept sentence back on the board, replacing whatever is there. */
    fun restoreHistoryEntry(id: String) {
        val entry = history.entry(id) ?: return
        spelled.replace(entry.text)
        publish()
    }

    fun deleteHistoryEntry(id: String) {
        history.remove(id)
        publish()
    }

    fun clearHistory() {
        history.clear()
        publish()
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
