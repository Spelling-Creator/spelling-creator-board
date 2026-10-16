package org.spellingcreator.board.layout

/** The boards that ship with the app. Modeled on the common stencil boards. */
object BoardLayouts {
    /** A through Z in five rows: four rows of five and a last row of six. */
    val alphabet: BoardLayout =
        BoardLayout(
            id = "alphabet",
            name = "Letters",
            rows =
                listOf("ABCDE", "FGHIJ", "KLMNO", "PQRST", "UVWXYZ").map { row ->
                    row.map { letter ->
                        // Spoken in lowercase: engines read an uppercase letter on its own as "capital A".
                        BoardKey(
                            id = "letter-$letter",
                            label = letter.toString(),
                            kind = KeyKind.LETTER,
                            spoken = letter.lowercaseChar().toString(),
                        )
                    }
                },
        )

    /** Arithmetic symbols on the top row, then the digits 0 through 9. */
    val math: BoardLayout =
        BoardLayout(
            id = "math",
            name = "Math",
            rows =
                listOf(
                    listOf(
                        BoardKey(id = "symbol-plus", label = "+", kind = KeyKind.SYMBOL, spoken = "plus"),
                        BoardKey(id = "symbol-minus", label = "−", kind = KeyKind.SYMBOL, spoken = "minus", text = "-"),
                        BoardKey(id = "symbol-times", label = "×", kind = KeyKind.SYMBOL, spoken = "times"),
                        BoardKey(id = "symbol-divide", label = "÷", kind = KeyKind.SYMBOL, spoken = "divided by"),
                        BoardKey(id = "symbol-equals", label = "=", kind = KeyKind.SYMBOL, spoken = "equals"),
                    ),
                    digits("01234"),
                    digits("56789"),
                ),
        )

    val all: List<BoardLayout> = listOf(alphabet, math)

    fun byId(id: String): BoardLayout = all.firstOrNull { it.id == id } ?: alphabet

    private fun digits(row: String): List<BoardKey> =
        row.map { digit -> BoardKey(id = "digit-$digit", label = digit.toString(), kind = KeyKind.DIGIT) }
}
