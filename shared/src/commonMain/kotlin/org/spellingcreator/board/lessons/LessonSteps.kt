package org.spellingcreator.board.lessons

enum class LessonStepKind {
    /** A section's material: text, pictures' captions, spelling words, a VAKT activity. */
    CONTENT,

    /** One question, with the board as the answer box. */
    QUESTION,
}

/**
 * One screen of a lesson walk-through, the same steps Spelling Creator's interactive mode shows:
 * a section's material first, then each of its questions on its own, numbered from 1 within the
 * section. A section with nothing in it contributes nothing.
 */
data class LessonStep(
    val key: String,
    val kind: LessonStepKind,
    val sectionName: String,
    val blocks: List<LessonBlock>,
    val question: LessonQuestion? = null,
    val questionNumber: Int = 0,
)

object LessonSteps {
    fun build(lesson: Lesson): List<LessonStep> {
        val steps = mutableListOf<LessonStep>()
        for (section in lesson.sections) {
            val content = section.blocks.filter { it.kind != BlockKind.QUESTION && it.lines.isNotEmpty() }
            if (content.isNotEmpty()) {
                steps +=
                    LessonStep(
                        key = "${section.id}:content",
                        kind = LessonStepKind.CONTENT,
                        sectionName = section.name,
                        blocks = content,
                    )
            }
            var number = 0
            section.blocks.forEachIndexed { index, block ->
                val question = block.question ?: return@forEachIndexed
                number += 1
                steps +=
                    LessonStep(
                        key = "${section.id}:question-$index",
                        kind = LessonStepKind.QUESTION,
                        sectionName = section.name,
                        blocks = emptyList(),
                        question = question,
                        questionNumber = number,
                    )
            }
        }
        return steps
    }

    /** What a step says when read aloud: the section name, then its lines or its prompt. */
    fun speech(step: LessonStep): String {
        val lines = mutableListOf<String>()
        if (step.sectionName.isNotBlank()) lines += step.sectionName
        step.question?.let { lines += it.prompt }
        for (block in step.blocks) {
            when (block.kind) {
                BlockKind.SPELLING -> lines += block.lines.joinToString(", ")
                else -> lines += block.lines
            }
        }
        return lines.filter { it.isNotBlank() }.joinToString("\n")
    }
}
