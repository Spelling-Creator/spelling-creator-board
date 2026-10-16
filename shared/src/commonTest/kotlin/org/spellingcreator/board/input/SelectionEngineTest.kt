package org.spellingcreator.board.input

import org.spellingcreator.board.layout.BoardGeometry
import org.spellingcreator.board.layout.BoardLayouts
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionEngineTest {
    private val geometry = BoardGeometry(BoardLayouts.alphabet, width = 500f, height = 500f)
    private val a = geometry.cell("letter-A")!!
    private val b = geometry.cell("letter-B")!!

    private fun event(
        phase: PointerPhase,
        x: Float,
        y: Float,
        time: Long,
        pointer: Long = 1,
        kind: PointerKind = PointerKind.FINGER,
    ) = PointerEvent(pointer, phase, x, y, time, kind)

    @Test
    fun touchDownCommitsImmediately() {
        val engine = SelectionEngine()
        val down = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        assertEquals(SelectionKind.COMMITTED, down.kind)
        assertEquals("A", down.key?.label)
        val up = engine.handle(event(PointerPhase.UP, a.centerX, a.centerY, 50), geometry)
        assertEquals(SelectionKind.RELEASED, up.kind)
    }

    @Test
    fun sameKeyInsideRefractoryWindowIsARepeat() {
        val engine = SelectionEngine(refractoryMillis = 400)
        engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        engine.handle(event(PointerPhase.UP, a.centerX, a.centerY, 50), geometry)
        val again = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 200), geometry)
        assertEquals(SelectionKind.REPEATED, again.kind)
        engine.handle(event(PointerPhase.UP, a.centerX, a.centerY, 250), geometry)
        val later = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 700), geometry)
        assertEquals(SelectionKind.COMMITTED, later.kind)
    }

    @Test
    fun aDifferentKeyIsNeverARepeat() {
        val engine = SelectionEngine()
        engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        engine.handle(event(PointerPhase.UP, a.centerX, a.centerY, 10), geometry)
        val other = engine.handle(event(PointerPhase.DOWN, b.centerX, b.centerY, 20), geometry)
        assertEquals(SelectionKind.COMMITTED, other.kind)
        assertEquals("B", other.key?.label)
    }

    @Test
    fun firstTouchWins() {
        val engine = SelectionEngine()
        engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0, pointer = 1), geometry)
        val second = engine.handle(event(PointerPhase.DOWN, b.centerX, b.centerY, 5, pointer = 2), geometry)
        assertEquals(SelectionKind.IGNORED, second.kind)
        val secondUp = engine.handle(event(PointerPhase.UP, b.centerX, b.centerY, 30, pointer = 2), geometry)
        assertEquals(SelectionKind.IGNORED, secondUp.kind)
        val firstUp = engine.handle(event(PointerPhase.UP, a.centerX, a.centerY, 40, pointer = 1), geometry)
        assertEquals(SelectionKind.RELEASED, firstUp.kind)
    }

    @Test
    fun stylusOnlyIgnoresFingers() {
        val engine = SelectionEngine(stylusOnly = true)
        val finger = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        assertEquals(SelectionKind.IGNORED, finger.kind)
        val stylus =
            engine.handle(
                event(PointerPhase.DOWN, a.centerX, a.centerY, 0, kind = PointerKind.STYLUS),
                geometry,
            )
        assertEquals(SelectionKind.COMMITTED, stylus.kind)
    }

    @Test
    fun touchUpCommitsWhereTheFingerLifts() {
        val engine = SelectionEngine(commitMode = CommitMode.ON_TOUCH_UP)
        val down = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        assertEquals(SelectionKind.PRESSED, down.kind)
        val slide = engine.handle(event(PointerPhase.MOVE, b.centerX, b.centerY, 20), geometry)
        assertEquals(SelectionKind.PRESSED, slide.kind)
        assertEquals("B", slide.key?.label)
        val up = engine.handle(event(PointerPhase.UP, b.centerX, b.centerY, 40), geometry)
        assertEquals(SelectionKind.COMMITTED, up.kind)
        assertEquals("B", up.key?.label)
    }

    @Test
    fun cancelReleasesWithoutCommitting() {
        val engine = SelectionEngine(commitMode = CommitMode.ON_TOUCH_UP)
        engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 0), geometry)
        val cancel = engine.handle(event(PointerPhase.CANCEL, a.centerX, a.centerY, 10), geometry)
        assertEquals(SelectionKind.RELEASED, cancel.kind)
        val next = engine.handle(event(PointerPhase.DOWN, a.centerX, a.centerY, 20, pointer = 2), geometry)
        assertEquals(SelectionKind.PRESSED, next.kind)
    }
}
