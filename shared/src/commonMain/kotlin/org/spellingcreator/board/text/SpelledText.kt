package org.spellingcreator.board.text

import org.spellingcreator.board.layout.BoardKey

/** The sentence being spelled: the finished words plus the word in progress. */
class SpelledText {
    private val builder = StringBuilder()

    val text: String get() = builder.toString()

    val isEmpty: Boolean get() = builder.isEmpty()

    /** The word being spelled now, or an empty string right after a space. */
    val currentWord: String get() = text.substringAfterLast(' ')

    fun append(key: BoardKey) {
        builder.append(key.text)
    }

    /** Ends the current word. Returns that word, or null if there was nothing to end. */
    fun space(): String? {
        if (builder.isEmpty() || builder.last() == ' ') return null
        val word = currentWord
        builder.append(' ')
        return word
    }

    /** Removes the last character. Returns it, or null if there was nothing to remove. */
    fun backspace(): String? {
        if (builder.isEmpty()) return null
        val removed = builder.last().toString()
        builder.setLength(builder.length - 1)
        return removed
    }

    fun clear() {
        builder.setLength(0)
    }
}
