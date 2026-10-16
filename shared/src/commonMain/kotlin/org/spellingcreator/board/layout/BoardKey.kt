package org.spellingcreator.board.layout

/** What a key on the board stands for. Control keys (space, delete) are not on the board itself. */
enum class KeyKind {
    LETTER,
    DIGIT,
    SYMBOL,
}

/**
 * One cell on a board.
 *
 * @property id Stable identifier, unique within a layout. Used to match highlights and sounds to cells.
 * @property label What is printed on the cell.
 * @property spoken What the letter voice says when the cell is selected. Usually the label; symbols
 * spell out their name so "×" is said as "times".
 * @property text What the cell adds to the sentence.
 */
data class BoardKey(
    val id: String,
    val label: String,
    val kind: KeyKind,
    val spoken: String = label,
    val text: String = label,
)
