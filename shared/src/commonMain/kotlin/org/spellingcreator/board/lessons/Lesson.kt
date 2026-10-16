package org.spellingcreator.board.lessons

/** One lesson in the hub's public listing. */
data class LessonSummary(
    val id: String,
    val title: String,
    val author: String,
    val sectionCount: Int,
)

enum class BlockKind {
    TEXT,
    HEADING,
    SPELLING,
    IMAGE,
    VAKT,
    QUESTION,
}

/**
 * A question as the board shows it. The types and tags follow the Open Spelling Lesson Format,
 * under the names Spelling Creator stores them.
 *
 * @property answers The author's answers, if any: one for a known, prior knowledge or math
 * question, several for a semi-open one, none for an open one.
 * @property suggested Whether [answers] are examples rather than the whole accepted set.
 */
data class LessonQuestion(
    val type: String,
    val prompt: String,
    val answers: List<String>,
    val suggested: Boolean,
) {
    val tag: String get() = QUESTION_TAGS[type] ?: "?"

    val label: String get() = QUESTION_LABELS[type] ?: "Question"

    companion object {
        val QUESTION_TAGS: Map<String, String> =
            mapOf(
                "number" to "M",
                "single" to "K",
                "background" to "PK",
                "multiple" to "SO",
                "multiple_open" to "SO",
                "tight_open" to "TO",
                "broad_open" to "BO",
                "paraphrase" to "P",
                "open" to "O",
                "wyr" to "WYR",
            )
        val QUESTION_LABELS: Map<String, String> =
            mapOf(
                "number" to "Math",
                "single" to "Known",
                "background" to "Prior knowledge",
                "multiple" to "Semi-open",
                "multiple_open" to "Suggested answers",
                "tight_open" to "Tight open",
                "broad_open" to "Broad open",
                "paraphrase" to "Paraphrase",
                "open" to "Open ended",
                "wyr" to "Would you rather",
            )
    }
}

/**
 * One block of a section, reduced to what a board can show: plain lines of text. Formatting,
 * footnotes and pictures are dropped; a picture keeps its caption.
 */
data class LessonBlock(
    val kind: BlockKind,
    val lines: List<String> = emptyList(),
    val question: LessonQuestion? = null,
)

data class LessonSection(
    val id: String,
    val name: String,
    val blocks: List<LessonBlock>,
)

data class Lesson(
    val id: String,
    val title: String,
    val author: String,
    val sections: List<LessonSection>,
)
