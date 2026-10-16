package org.spellingcreator.board.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StencilFrameTest {
    @Test
    fun handleSitsInsideTheRightEdgeAndLettersStopBeforeIt() {
        val frame = StencilFrame(width = 1000f, height = 750f)
        val handle = assertNotNull(frame.handle)
        assertTrue(handle.right < frame.width)
        assertTrue(handle.right > frame.width - frame.margin * 2)
        assertTrue(frame.hitWidth < handle.left)
        assertTrue(handle.top > 0f && handle.bottom < frame.height)
    }

    @Test
    fun fitFillsTheAreaWhenItsProportionsAreReasonable() {
        val box = StencilFrame.fit(availableWidth = 1300f, availableHeight = 1000f)
        assertEquals(1300f, box.width)
        assertEquals(1000f, box.height)
        assertEquals(0f, box.left)
        assertEquals(0f, box.top)
    }

    @Test
    fun fitClampsVeryWideAndVeryTallAreasAndCentersTheSlab() {
        val wide = StencilFrame.fit(availableWidth = 2000f, availableHeight = 800f)
        assertEquals(800f, wide.height)
        assertEquals(1200f, wide.width)
        assertEquals(400f, wide.left)
        assertEquals(0f, wide.top)

        val tall = StencilFrame.fit(availableWidth = 1000f, availableHeight = 2000f)
        assertEquals(1000f, tall.width)
        assertEquals(1000f, tall.height)
        assertEquals(500f, tall.top)
    }

    @Test
    fun withoutAHandleTheLettersUseTheWholeWidth() {
        val frame = StencilFrame(width = 1000f, height = 750f, hasHandle = false)
        assertNull(frame.handle)
        assertEquals(1000f, frame.hitWidth)
    }

    @Test
    fun touchesOnTheHandleMissAndTouchesInTheMarginSnap() {
        val frame = StencilFrame(width = 1000f, height = 750f)
        val geometry =
            BoardGeometry(BoardLayouts.alphabet, frame.hitWidth, frame.height, gap = 0f, padding = frame.margin)
        val handle = assertNotNull(frame.handle)
        assertNull(geometry.cellAt(handle.left + handle.width / 2, handle.top + handle.height / 2))
        assertEquals("A", geometry.cellAt(frame.margin / 2, frame.margin / 2)?.key?.label)
    }
}
