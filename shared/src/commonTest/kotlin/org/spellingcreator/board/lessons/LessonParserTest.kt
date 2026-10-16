package org.spellingcreator.board.lessons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A trimmed copy of what `GET /lessons/:id` returns for a real hub lesson. */
internal const val LESSON_JSON = """
{"lesson":{"id":"4ad249e0","title":"The History of Domestic Cats","author":"Admin","sectionCount":2,"published":true,
 "doc":{"title":"The History of Domestic Cats","sections":[
  {"id":"s1","name":"Where cats come from","blocks":[
   {"id":"t1","type":"text","content":{"type":"doc","content":[
     {"type":"paragraph","content":[
       {"type":"text","text":"Every pet cat is descended from the AFRICAN WILDCAT, known as "},
       {"type":"text","text":"Felis lybica","marks":[{"type":"italic"}]},
       {"type":"text","text":"."},
       {"type":"footnote","attrs":{"note":"","locator":"","sourceId":"science2025"}}]},
     {"type":"paragraph"},
     {"type":"paragraph","content":[{"type":"text","text":"It still roams North Africa."}]}]}},
   {"id":"i1","type":"image","src":"x","caption":"A wildcat (by Someone, CC BY 4.0) via Wikimedia Commons"},
   {"id":"i2","type":"image","src":"y","caption":"Image (by Someone, CC0) via Wikimedia Commons"},
   {"id":"w1","type":"spelling","words":[{"id":"a","text":"FELINE"},{"id":"b","text":"PROWLED"}]},
   {"id":"v1","type":"vakt","text":"VAKT: Stretch like a cat waking up."},
   {"id":"q1","type":"question","questionType":"single","prompt":"From which species is every pet cat descended?","answer":"THE AFRICAN WILDCAT"},
   {"id":"q2","type":"question","questionType":"multiple","prompt":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Name an animal cats hunted."}]}]},"answers":[{"id":"x","text":"MICE"},{"id":"y","text":"BIRDS"}]},
   {"id":"q3","type":"question","questionType":"broad_open","prompt":"What do you think of cats?"}]},
  {"id":"s2","name":"Empty","blocks":[]},
  {"id":"s3","name":"Only questions","blocks":[
   {"id":"q4","type":"question","questionType":"number","prompt":"How many lives?","answer":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"9"}]}]}}]}]}}}
"""

class LessonParserTest {
    private val lesson = assertNotNull(LessonParser.parseLesson(LESSON_JSON))

    @Test
    fun readsTitleAuthorAndSections() {
        assertEquals("The History of Domestic Cats", lesson.title)
        assertEquals("Admin", lesson.author)
        assertEquals(listOf("s1", "s2", "s3"), lesson.sections.map { it.id })
    }

    @Test
    fun textKeepsTheWordsAndDropsFormattingFootnotesAndEmptyParagraphs() {
        val text = lesson.sections[0].blocks[0]
        assertEquals(BlockKind.TEXT, text.kind)
        assertEquals(
            listOf(
                "Every pet cat is descended from the AFRICAN WILDCAT, known as Felis lybica.",
                "It still roams North Africa.",
            ),
            text.lines,
        )
    }

    @Test
    fun picturesKeepACaptionButNotACreditAlone() {
        val blocks = lesson.sections[0].blocks
        assertEquals(listOf("A wildcat"), blocks[1].lines)
        assertTrue(blocks[2].lines.isEmpty())
    }

    @Test
    fun spellingWordsAndVaktActivitiesComeThrough() {
        val blocks = lesson.sections[0].blocks
        assertEquals(listOf("FELINE", "PROWLED"), blocks[3].lines)
        assertEquals(listOf("Stretch like a cat waking up."), blocks[4].lines)
    }

    @Test
    fun questionsCarryTheirPromptsAnswersAndTags() {
        val questions = lesson.sections[0].blocks.mapNotNull { it.question }
        assertEquals(3, questions.size)
        assertEquals("K", questions[0].tag)
        assertEquals(listOf("THE AFRICAN WILDCAT"), questions[0].answers)
        assertEquals("Name an animal cats hunted.", questions[1].prompt)
        assertEquals(listOf("MICE", "BIRDS"), questions[1].answers)
        assertEquals(false, questions[1].suggested)
        assertEquals("BO", questions[2].tag)
        assertTrue(questions[2].answers.isEmpty())
        val number = assertNotNull(lesson.sections[2].blocks[0].question)
        assertEquals(listOf("9"), number.answers)
    }

    @Test
    fun listingParsesSummaries() {
        val listing =
            LessonParser.parseListing(
                """{"lessons":[{"id":"a","title":"One","author":"Me","sectionCount":3},{"title":"no id"}]}""",
            )
        assertEquals(listOf(LessonSummary("a", "One", "Me", 3)), listing)
    }

    @Test
    fun junkIsNotALesson() {
        assertNull(LessonParser.parseLesson("not json"))
        assertNull(LessonParser.parseLesson("""{"error":"nope"}"""))
    }
}
