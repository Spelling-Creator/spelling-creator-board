package org.spellingcreator.board.lessons

import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.input.PointerPhase
import org.spellingcreator.board.net.WebSocketConnection
import org.spellingcreator.board.net.WebSocketEvents
import org.spellingcreator.board.net.WebSocketFactory
import org.spellingcreator.board.session.BoardSession
import org.spellingcreator.board.session.BoardSessionListener
import org.spellingcreator.board.session.BoardState
import org.spellingcreator.board.session.Feedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A socket the test drives by hand: it records what the board sends and lets the test play the host. */
private class FakeSockets : WebSocketFactory {
    var url: String? = null
    var events: WebSocketEvents? = null
    val sent = mutableListOf<PracticeMessage>()
    var closed = false

    override fun connect(
        url: String,
        events: WebSocketEvents,
    ): WebSocketConnection {
        this.url = url
        this.events = events
        return object : WebSocketConnection {
            override fun send(text: String) {
                sent += assertNotNull(PracticeMessage.decode(text))
            }

            override fun close() {
                closed = true
            }
        }
    }

    fun host(message: PracticeMessage) = events!!.onMessage(PracticeMessage.encode(message))
}

class PracticeLinkTest {
    private var boardState: BoardState? = null
    private val session =
        BoardSession(
            listener =
                object : BoardSessionListener {
                    override fun onStateChanged(state: BoardState) {
                        boardState = state
                    }

                    override fun onFeedback(feedback: Feedback) = Unit
                },
        ).also { it.resize(500f, 500f) }
    private val sockets = FakeSockets()
    private val fetched = mutableListOf<LessonAddress>()
    private val linkStates = mutableListOf<PracticeLinkState>()
    private val link =
        PracticeLink(
            session = session,
            sockets = sockets,
            fetchLesson = { address, callback ->
                fetched += address
                callback(LessonParser.parseLesson(LESSON_JSON), null)
            },
            onStateChanged = { linkStates += it },
        )

    private fun tap(keyId: String) {
        val cell = session.geometry!!.cell(keyId)!!
        session.handlePointer(PointerEvent(1, PointerPhase.DOWN, cell.centerX, cell.centerY, 0))
        session.handlePointer(PointerEvent(1, PointerPhase.UP, cell.centerX, cell.centerY, 30))
    }

    @Test
    fun joinsOverTheHubsSocketAndAnnouncesItself() {
        link.join("spellingcreator.org", " ABC123 ")
        assertEquals("wss://spellingcreator.org/practice/ABC123", sockets.url)
        assertEquals(PracticeLinkStatus.CONNECTING, link.state.status)
        sockets.events!!.onOpen()
        assertEquals(listOf(PracticeMessage("join", client = "board")), sockets.sent)
    }

    @Test
    fun followsTheHostsLessonAndStep() {
        link.join("https://spellingcreator.org", "ABC123")
        sockets.events!!.onOpen()
        sockets.host(PracticeMessage("hello"))
        assertEquals(PracticeLinkStatus.WAITING, link.state.status)
        sockets.host(PracticeMessage("state", lessonId = "4ad249e0", step = 2))
        assertEquals(PracticeLinkStatus.LINKED, link.state.status)
        assertEquals(listOf(LessonAddress("https://spellingcreator.org", "4ad249e0")), fetched)
        assertEquals("4ad249e0", boardState?.lesson?.lessonId)
        assertEquals(2, boardState?.lesson?.stepIndex)
        // A remote move is applied, not echoed back as a request.
        sockets.host(PracticeMessage("state", lessonId = "4ad249e0", step = 3))
        assertEquals(3, boardState?.lesson?.stepIndex)
        assertTrue(sockets.sent.none { it.type == "step" })
    }

    @Test
    fun sendsTheSentenceAnswersAndLocalMoves() {
        link.join("https://spellingcreator.org", "ABC123")
        sockets.events!!.onOpen()
        sockets.host(PracticeMessage("state", lessonId = "4ad249e0", step = 1))
        sockets.sent.clear()
        tap("letter-C")
        tap("letter-A")
        assertEquals(listOf("C", "CA"), sockets.sent.filter { it.type == "text" }.map { it.text })
        session.submitAnswer()
        assertEquals("CA", sockets.sent.first { it.type == "answer" }.text)
        assertEquals("", sockets.sent.last { it.type == "text" }.text)
        // The advance after Done is the host's call, so it is not echoed as a step request.
        assertTrue(sockets.sent.none { it.type == "step" })
        session.previousStep()
        assertEquals(listOf(1), sockets.sent.filter { it.type == "step" }.map { it.step })
    }

    @Test
    fun hostClosingOrRemovingEndsTheLink() {
        link.join("https://spellingcreator.org", "ABC123")
        sockets.events!!.onOpen()
        sockets.host(PracticeMessage("state", lessonId = "4ad249e0", step = 0))
        sockets.host(PracticeMessage("removed", message = "Session over."))
        assertEquals(PracticeLinkStatus.FAILED, link.state.status)
        assertEquals("Session over.", link.state.message)
        assertTrue(sockets.closed)
        // The lesson stays on the board so the partner can carry on alone.
        assertEquals("4ad249e0", boardState?.lesson?.lessonId)
        link.leave()
        assertEquals(PracticeLinkStatus.OFF, link.state.status)
    }

    @Test
    fun anEmptyCodeIsRefusedBeforeConnecting() {
        link.join("https://spellingcreator.org", "   ")
        assertEquals(PracticeLinkStatus.FAILED, link.state.status)
        assertNull(sockets.url)
    }
}
