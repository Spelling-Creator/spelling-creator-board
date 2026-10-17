package org.spellingcreator.board.lessons

import org.spellingcreator.board.net.WebSocketConnection
import org.spellingcreator.board.net.WebSocketEvents
import org.spellingcreator.board.net.WebSocketFactory
import org.spellingcreator.board.session.BoardSession
import org.spellingcreator.board.session.BoardSessionListener
import org.spellingcreator.board.session.BoardState
import org.spellingcreator.board.session.Feedback
import org.spellingcreator.board.session.FeedbackKind

enum class PracticeLinkStatus {
    /** Not linked to anything. */
    OFF,

    /** Connecting to the hub. */
    CONNECTING,

    /** Connected; the host has not opened a lesson yet. */
    WAITING,

    /** Following the host's lesson. */
    LINKED,

    /** The connection failed or the host ended the session. [PracticeLinkState.message] says why. */
    FAILED,
}

data class PracticeLinkState(
    val status: PracticeLinkStatus = PracticeLinkStatus.OFF,
    val hubUrl: String = "",
    val code: String = "",
    val message: String? = null,
) {
    val isActive: Boolean get() =
        status == PracticeLinkStatus.CONNECTING ||
            status == PracticeLinkStatus.WAITING ||
            status == PracticeLinkStatus.LINKED
}

/**
 * Keeps the board in step with a Practice page on a computer, over the hub's letterboard session.
 *
 * The host (the Practice page) owns the lesson and the step. The board follows: when the host
 * moves, the board moves; when the board's sentence changes, the host's answer box shows it; when
 * the partner taps Done on the board, the host records the answer and moves on. The board may also
 * ask to move a step, and the host answers with the state everyone should be on.
 *
 * Listens to the session like a view does, so nothing in [BoardSession] knows about sockets.
 */
class PracticeLink(
    private val session: BoardSession,
    private val sockets: WebSocketFactory,
    /** Loads a lesson the host opened; the apps pass the hub client's `fetchLesson`. */
    private val fetchLesson: (LessonAddress, (Lesson?, String?) -> Unit) -> Unit,
    private val onStateChanged: (PracticeLinkState) -> Unit,
) : BoardSessionListener {
    var state: PracticeLinkState = PracticeLinkState()
        private set

    private var connection: WebSocketConnection? = null
    private var lastText: String = ""
    private var lastStep: Int = -1
    private var lastLessonId: String? = null

    /** Which lesson the host has open and the step it is on, so a late lesson fetch lands on the right step. */
    private var hostLessonId: String? = null
    private var hostStep: Int = 0

    /** True while a remote step is being applied, so the echo is not sent back as a request. */
    private var applyingRemote = false

    fun join(
        hubUrl: String,
        code: String,
    ) {
        leave()
        val base = HubLinks.normalizeBaseUrl(hubUrl)
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            update(
                PracticeLinkState(
                    PracticeLinkStatus.FAILED,
                    base,
                    trimmed,
                    "Enter the session code from the Practice page.",
                ),
            )
            return
        }
        session.addListener(this)
        update(PracticeLinkState(PracticeLinkStatus.CONNECTING, base, trimmed))
        connection =
            sockets.connect(
                sessionUrl(base, trimmed),
                object : WebSocketEvents {
                    override fun onOpen() {
                        send(PracticeMessage(PracticeMessage.JOIN, client = "board"))
                    }

                    override fun onMessage(text: String) {
                        PracticeMessage.decode(text)?.let { handle(it) }
                    }

                    override fun onClosed(reason: String?) {
                        if (state.isActive) finish(reason ?: "The session ended.")
                    }

                    override fun onFailure(message: String) {
                        if (state.isActive) finish("Could not stay connected: $message")
                    }
                },
            )
    }

    fun leave() {
        connection?.close()
        connection = null
        session.removeListener(this)
        hostLessonId = null
        if (state.status != PracticeLinkStatus.OFF) update(PracticeLinkState())
    }

    private fun finish(message: String) {
        connection = null
        session.removeListener(this)
        update(state.copy(status = PracticeLinkStatus.FAILED, message = message))
    }

    private fun handle(message: PracticeMessage) {
        when (message.type) {
            PracticeMessage.HELLO, PracticeMessage.STATE -> applyState(message)
            PracticeMessage.REMOVED -> {
                connection?.close()
                finish(message.message ?: "The host ended the session.")
            }
            PracticeMessage.ERROR -> {
                connection?.close()
                finish(message.message ?: "The hub refused the session.")
            }
        }
    }

    private fun applyState(message: PracticeMessage) {
        val lessonId = message.lessonId
        hostLessonId = lessonId
        hostStep = message.step ?: hostStep
        if (lessonId == null) {
            if (session.state.lesson != null) session.endLesson()
            update(state.copy(status = PracticeLinkStatus.WAITING, message = null))
            return
        }
        update(state.copy(status = PracticeLinkStatus.LINKED, message = null))
        val open = session.state.lesson
        if (open?.lessonId == lessonId) {
            moveTo(hostStep)
            // The host's answer box is the board's own words echoed back; take it only onto an empty board.
            if (session.state.text.isEmpty() && !message.text.isNullOrEmpty()) session.replaceText(message.text)
            return
        }
        fetchLesson(LessonAddress(state.hubUrl, lessonId)) { lesson, error ->
            if (hostLessonId != lessonId) return@fetchLesson
            if (lesson == null) {
                update(state.copy(message = error ?: "Could not load the host's lesson."))
                return@fetchLesson
            }
            applyingRemote = true
            session.startLesson(lesson)
            applyingRemote = false
            moveTo(hostStep)
        }
    }

    private fun moveTo(step: Int) {
        applyingRemote = true
        session.goToStep(step)
        applyingRemote = false
        lastStep = session.state.lesson?.stepIndex ?: -1
    }

    // Session events: the board's side of the conversation.

    override fun onStateChanged(state: BoardState) {
        if (!this.state.isActive) return
        if (state.text != lastText) {
            lastText = state.text
            send(PracticeMessage(PracticeMessage.TEXT, text = state.text))
        }
        val step = state.lesson?.stepIndex ?: -1
        val lessonId = state.lesson?.lessonId
        val lessonChanged = lessonId != lastLessonId
        lastLessonId = lessonId
        if (step != lastStep) {
            lastStep = step
            // The move after an answer is the host's to make; it answers the `answer` with a `state`.
            val hostsMove = answerJustSent
            answerJustSent = false
            if (!applyingRemote && !lessonChanged && !hostsMove && step >= 0) {
                send(PracticeMessage(PracticeMessage.STEP, step = step))
            }
        }
    }

    override fun onFeedback(feedback: Feedback) {
        if (!state.isActive) return
        if (feedback.kind == FeedbackKind.ANSWER_SUBMITTED) {
            answerJustSent = true
            send(PracticeMessage(PracticeMessage.ANSWER, text = feedback.text.orEmpty()))
        }
    }

    /** Set between sending an `answer` and the session's own advance, so that advance is not echoed as a `step`. */
    private var answerJustSent = false

    private fun send(message: PracticeMessage) {
        connection?.send(PracticeMessage.encode(message))
    }

    private fun update(next: PracticeLinkState) {
        state = next
        onStateChanged(next)
    }

    companion object {
        /** The WebSocket the hub serves a letterboard session on. */
        fun sessionUrl(
            baseUrl: String,
            code: String,
        ): String {
            val base = HubLinks.normalizeBaseUrl(baseUrl)
            val ws =
                when {
                    base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                    base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                    else -> base
                }
            return "$ws/practice/$code"
        }
    }
}
