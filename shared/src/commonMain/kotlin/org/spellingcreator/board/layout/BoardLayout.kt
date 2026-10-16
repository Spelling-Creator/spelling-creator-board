package org.spellingcreator.board.layout

/**
 * A board is a list of rows, each holding its keys in reading order. Rows may have different
 * lengths: a row's keys are spread evenly across the full width, like the cutouts on a stencil board.
 */
data class BoardLayout(
    val id: String,
    val name: String,
    val rows: List<List<BoardKey>>,
) {
    val keys: List<BoardKey> get() = rows.flatten()

    fun key(id: String): BoardKey? = keys.firstOrNull { it.id == id }
}
