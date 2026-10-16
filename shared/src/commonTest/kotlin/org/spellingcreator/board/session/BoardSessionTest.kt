package org.spellingcreator.board.session

import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.input.PointerPhase
import org.spellingcreator.board.settings.BoardSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardSessionTest {
    private val feedback = mutableListOf<Feedback>()
    private var state: BoardState? = null
    private val session =
        BoardSession(
            settings = BoardSettings(),
            listener =
                object : BoardSessionListener {
                    override fun onStateChanged(state: BoardState) {
                        this@BoardSessionTest.state = state
                    }

                    override fun onFeedback(feedback: Feedback) {
                        this@BoardSessionTest.feedback += feedback
                    }
                },
        ).also { it.resize(500f, 500f) }

    private fun tap(
        keyId: String,
        time: Long,
    ) {
        val cell = session.geometry!!.cell(keyId)!!
        session.handlePointer(PointerEvent(1, PointerPhase.DOWN, cell.centerX, cell.centerY, time))
        session.handlePointer(PointerEvent(1, PointerPhase.UP, cell.centerX, cell.centerY, time + 30))
    }

    @Test
    fun spellingAWordSaysEachLetterThenTheWord() {
        tap("letter-C", 0)
        tap("letter-A", 1000)
        tap("letter-T", 2000)
        session.space()
        assertEquals("CAT ", state?.text)
        assertEquals(listOf("c", "a", "t", "CAT"), feedback.filter { it.kind == FeedbackKind.SAY }.map { it.text })
        assertEquals(3, feedback.count { it.kind == FeedbackKind.KEY_COMMITTED })
    }

    @Test
    fun repeatedKeyIsNoticedButNotSpoken() {
        tap("letter-E", 0)
        tap("letter-E", 100)
        assertEquals("E", state?.text)
        assertEquals(1, feedback.count { it.kind == FeedbackKind.KEY_REPEATED })
        assertEquals(1, feedback.count { it.kind == FeedbackKind.SAY })
    }

    @Test
    fun switchingLayoutsKeepsTheSentence() {
        tap("letter-H", 0)
        session.selectLayout("math")
        assertEquals("math", state?.layout?.id)
        assertEquals("H", state?.text)
        tap("digit-7", 1000)
        assertEquals("H7", state?.text)
    }

    @Test
    fun hapticsCanBeTurnedOff() {
        session.updateSettings(BoardSettings(haptics = false))
        tap("letter-B", 0)
        assertNull(feedback.first { it.kind == FeedbackKind.KEY_COMMITTED }.haptic)
    }

    @Test
    fun backspaceAndClearReportWhatHappened() {
        tap("letter-N", 0)
        tap("letter-O", 1000)
        session.backspace()
        assertEquals("N", state?.text)
        assertEquals("O", feedback.last { it.kind == FeedbackKind.DELETED }.text)
        session.clear()
        assertEquals("", state?.text)
        assertEquals(FeedbackKind.CLEARED, feedback.last().kind)
    }
}
