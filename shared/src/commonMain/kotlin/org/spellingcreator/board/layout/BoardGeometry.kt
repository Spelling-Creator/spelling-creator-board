package org.spellingcreator.board.layout

import kotlin.math.floor

/** Where one key is drawn, in the same units as the size the geometry was built with. */
data class CellRect(
    val key: BoardKey,
    val row: Int,
    val column: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2
}

/**
 * Lays a [BoardLayout] out in a rectangle and answers which key a point lands on.
 *
 * The whole rectangle is live: a touch in the gap between two keys, or in the padding, snaps to the
 * nearest key, so the speller never gets silence from a touch that was clearly on the board. Only a
 * point outside the rectangle misses.
 */
class BoardGeometry(
    val layout: BoardLayout,
    val width: Float,
    val height: Float,
    val gap: Float = 0f,
    val padding: Float = 0f,
) {
    private val rowCount = layout.rows.size
    private val innerWidth = (width - padding * 2).coerceAtLeast(0f)
    private val innerHeight = (height - padding * 2).coerceAtLeast(0f)
    private val rowPitch = if (rowCount == 0) 0f else (innerHeight + gap) / rowCount
    private val rowHeight = (rowPitch - gap).coerceAtLeast(0f)

    val cells: List<CellRect> =
        layout.rows.flatMapIndexed { rowIndex, row ->
            val pitch = columnPitch(row.size)
            row.mapIndexed { columnIndex, key ->
                CellRect(
                    key = key,
                    row = rowIndex,
                    column = columnIndex,
                    left = padding + columnIndex * pitch,
                    top = padding + rowIndex * rowPitch,
                    width = (pitch - gap).coerceAtLeast(0f),
                    height = rowHeight,
                )
            }
        }

    fun cell(keyId: String): CellRect? = cells.firstOrNull { it.key.id == keyId }

    /** The key under a point, snapping gaps and padding to the nearest key. Null outside the board. */
    fun cellAt(
        x: Float,
        y: Float,
    ): CellRect? {
        if (rowCount == 0 || x < 0f || y < 0f || x > width || y > height) return null
        // Shifting by half a gap splits each gap down the middle, so a touch snaps to the nearer key.
        val rowIndex = indexFor(y - padding + gap / 2, rowPitch, rowCount)
        val row = layout.rows[rowIndex]
        if (row.isEmpty()) return null
        val columnIndex = indexFor(x - padding + gap / 2, columnPitch(row.size), row.size)
        return cells.first { it.row == rowIndex && it.column == columnIndex }
    }

    private fun columnPitch(columns: Int): Float = if (columns == 0) 0f else (innerWidth + gap) / columns

    private fun indexFor(
        position: Float,
        pitch: Float,
        count: Int,
    ): Int {
        if (pitch <= 0f) return 0
        return floor(position / pitch).toInt().coerceIn(0, count - 1)
    }
}
