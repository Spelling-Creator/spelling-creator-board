package org.spellingcreator.board.lessons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LessonStepsTest {
    private val lesson = assertNotNull(LessonParser.parseLesson(LESSON_JSON))
    private val steps = LessonSteps.build(lesson)

    @Test
    fun materialFirstThenEachQuestionAndEmptySectionsSkipped() {
        assertEquals(
            listOf(
                LessonStepKind.CONTENT,
                LessonStepKind.QUESTION,
                LessonStepKind.QUESTION,
                LessonStepKind.QUESTION,
                LessonStepKind.QUESTION,
            ),
            steps.map { it.kind },
        )
        assertEquals(listOf(0, 1, 2, 3, 1), steps.map { it.questionNumber })
        assertEquals("Only questions", steps.last().sectionName)
    }

    @Test
    fun speechReadsTheSectionThenTheMaterialOrThePrompt() {
        val content = LessonSteps.speech(steps[0])
        assertTrue(content.startsWith("Where cats come from\nEvery pet cat"))
        assertTrue(content.contains("FELINE, PROWLED"))
        assertEquals(
            "Where cats come from\nFrom which species is every pet cat descended?",
            LessonSteps.speech(steps[1]),
        )
    }

    @Test
    fun aRunMovesThroughTheStepsAndKeepsAnswers() {
        val run = LessonRun(lesson)
        assertFalse(run.hasPrevious)
        assertTrue(run.next())
        run.record(run.current!!, "THE AFRICAN WILDCAT")
        assertEquals("THE AFRICAN WILDCAT", run.answer(run.current!!))
        assertEquals(1, run.answeredCount)
        assertEquals(4, run.questionCount)
        repeat(10) { run.next() }
        assertEquals(steps.size - 1, run.index)
        assertFalse(run.hasNext)
        assertTrue(run.previous())
    }
}
