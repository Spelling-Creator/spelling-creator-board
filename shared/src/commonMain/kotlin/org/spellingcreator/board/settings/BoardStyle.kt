package org.spellingcreator.board.settings

/** How the board is drawn. The selection rules and feedback are the same either way. */
enum class BoardStyle {
    /** A stencil board with the letters cut out so the background shows through, handle slot and all. */
    STENCIL,

    /** Plain rounded keys in the platform's colors. */
    PLAIN,
}
