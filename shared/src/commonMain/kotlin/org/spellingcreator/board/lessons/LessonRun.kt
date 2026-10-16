package org.spellingcreator.board.lessons

/** A lesson being worked through: where the partner is in it and what the speller has answered. */
class LessonRun(
    val lesson: Lesson,
) {
    val steps: List<LessonStep> = LessonSteps.build(lesson)

    var index: Int = 0
        private set

    private val answers = mutableMapOf<String, String>()

    val current: LessonStep? get() = steps.getOrNull(index)

    val hasPrevious: Boolean get() = index > 0

    val hasNext: Boolean get() = index < steps.size - 1

    fun next(): Boolean {
        if (!hasNext) return false
        index += 1
        return true
    }

    fun previous(): Boolean {
        if (!hasPrevious) return false
        index -= 1
        return true
    }

    fun answer(step: LessonStep): String? = answers[step.key]

    fun record(
        step: LessonStep,
        answer: String,
    ) {
        answers[step.key] = answer
    }

    val answeredCount: Int get() = answers.size

    val questionCount: Int get() = steps.count { it.kind == LessonStepKind.QUESTION }
}
