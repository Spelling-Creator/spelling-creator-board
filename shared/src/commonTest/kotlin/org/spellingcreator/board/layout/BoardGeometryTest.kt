package org.spellingcreator.board.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardGeometryTest {
    private val geometry = BoardGeometry(BoardLayouts.alphabet, width = 600f, height = 500f, gap = 10f, padding = 20f)

    @Test
    fun rowsSpreadAcrossTheFullWidth() {
        val firstRow = geometry.cells.filter { it.row == 0 }
        val lastRow = geometry.cells.filter { it.row == 4 }
        assertEquals(5, firstRow.size)
        assertEquals(6, lastRow.size)
        assertEquals(20f, firstRow.first().left)
        assertEquals(580f, firstRow.last().right, absoluteTolerance = 0.01f)
        assertEquals(580f, lastRow.last().right, absoluteTolerance = 0.01f)
    }

    @Test
    fun pointInsideACellFindsIt() {
        val cell = geometry.cellAt(x = 30f, y = 30f)
        assertEquals("A", cell?.key?.label)
        assertEquals("Z", geometry.cellAt(x = 590f, y = 490f)?.key?.label)
    }

    @Test
    fun gapsAndPaddingSnapToTheNearestKey() {
        assertEquals("A", geometry.cellAt(x = 0f, y = 0f)?.key?.label)
        val a = geometry.cell("letter-A")!!
        assertEquals("A", geometry.cellAt(x = a.right + 2f, y = a.centerY)?.key?.label)
        assertEquals("B", geometry.cellAt(x = a.right + 8f, y = a.centerY)?.key?.label)
    }

    @Test
    fun outsideTheBoardMisses() {
        assertNull(geometry.cellAt(x = -1f, y = 10f))
        assertNull(geometry.cellAt(x = 10f, y = 501f))
    }
}
