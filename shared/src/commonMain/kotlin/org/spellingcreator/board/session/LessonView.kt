package org.spellingcreator.board.session

import org.spellingcreator.board.lessons.LessonBlock
import org.spellingcreator.board.lessons.LessonQuestion
import org.spellingcreator.board.lessons.LessonStepKind

/** What the lesson panel shows while a lesson is open: the current step and where it sits. */
data class LessonView(
    val lessonId: String,
    val title: String,
    val author: String,
    val stepIndex: Int,
    val stepCount: Int,
    val kind: LessonStepKind,
    val sectionName: String,
    /** The section's material, for a content step. */
    val blocks: List<LessonBlock>,
    /** The question, for a question step. */
    val question: LessonQuestion?,
    val questionNumber: Int,
    /** What the speller already answered on this step, if anything. */
    val answer: String?,
    val answeredCount: Int,
    val questionCount: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)
