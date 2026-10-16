package org.spellingcreator.board.lessons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the hub's JSON into [Lesson]s.
 *
 * The hub's document is the editor's own shape: `{ title, sections: [{ id, name, blocks }] }`,
 * where a text block's `content` is a rich-text document and a question's prompt is either a
 * string or the same rich text. Only the words are kept. Anything unexpected is skipped rather
 * than failing the whole lesson, since lessons written long ago or by hand still open on the hub.
 */
object LessonParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** The body of `GET /lessons`. */
    fun parseListing(body: String): List<LessonSummary> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()?.obj ?: return emptyList()
        return root["lessons"]
            ?.array
            ?.mapNotNull { element ->
                val lesson = element.obj ?: return@mapNotNull null
                val id = lesson["id"]?.string ?: return@mapNotNull null
                LessonSummary(
                    id = id,
                    title = lesson["title"]?.string?.ifBlank { null } ?: "Untitled lesson",
                    author = lesson["author"]?.string.orEmpty(),
                    sectionCount = lesson["sectionCount"]?.int ?: 0,
                )
            }.orEmpty()
    }

    /** The body of `GET /lessons/:id`. Null when it is not a lesson. */
    fun parseLesson(body: String): Lesson? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()?.obj ?: return null
        val lesson = root["lesson"]?.obj ?: return null
        val id = lesson["id"]?.string ?: return null
        val doc = lesson["doc"]?.obj
        val sections =
            doc
                ?.get("sections")
                ?.array
                ?.mapIndexedNotNull { index, element ->
                    val section = element.obj ?: return@mapIndexedNotNull null
                    LessonSection(
                        id = section["id"]?.string ?: "section-$index",
                        name = section["name"]?.string?.trim().orEmpty(),
                        blocks = section["blocks"]?.array?.mapNotNull { parseBlock(it.obj) }.orEmpty(),
                    )
                }.orEmpty()
        return Lesson(
            id = id,
            title = lesson["title"]?.string?.ifBlank { null } ?: doc?.get("title")?.string ?: "Untitled lesson",
            author = lesson["author"]?.string.orEmpty(),
            sections = sections,
        )
    }

    private fun parseBlock(block: JsonObject?): LessonBlock? {
        block ?: return null
        return when (block["type"]?.string) {
            "text" -> LessonBlock(BlockKind.TEXT, lines = textLines(block))
            "heading" ->
                LessonBlock(
                    BlockKind.HEADING,
                    lines = listOfNotNull(block["text"]?.string?.trim()?.ifBlank { null }),
                )
            "spelling" ->
                LessonBlock(
                    BlockKind.SPELLING,
                    lines =
                        block["words"]
                            ?.array
                            ?.mapNotNull {
                                it.obj
                                    ?.get(
                                        "text",
                                    )?.string
                                    ?.trim()
                                    ?.ifBlank { null }
                            }.orEmpty(),
                )
            "image" -> LessonBlock(BlockKind.IMAGE, lines = listOfNotNull(imageCaption(block)))
            "vakt" -> LessonBlock(BlockKind.VAKT, lines = listOfNotNull(vaktText(block)))
            "question" -> LessonBlock(BlockKind.QUESTION, question = parseQuestion(block))
            else -> null
        }
    }

    private fun parseQuestion(block: JsonObject): LessonQuestion {
        val type = block["questionType"]?.string ?: "open"
        val listed =
            block["answers"]
                ?.array
                ?.mapNotNull {
                    it.obj
                        ?.get(
                            "text",
                        )?.string
                        ?.trim()
                        ?.ifBlank { null }
                }.orEmpty()
        val single = block["answer"]?.let { inlinePlain(it) }?.trim().orEmpty()
        val answers =
            when (type) {
                "multiple", "multiple_open", "tight_open" -> listed
                "number", "single", "background" -> listOfNotNull(single.ifBlank { null })
                else -> emptyList()
            }
        return LessonQuestion(
            type = type,
            prompt = block["prompt"]?.let { inlinePlain(it) }?.trim().orEmpty(),
            answers = answers,
            // A semi-open question's listed answers are the whole set; the suggested and tight open
            // types list examples, and a learner who wrote something else that fits is also right.
            suggested = type == "multiple_open" || type == "tight_open",
        )
    }

    /** The lines of a text block: one per paragraph, formatting and footnotes dropped. */
    private fun textLines(block: JsonObject): List<String> {
        val content = block["content"]?.obj
        if (content != null) return paragraphs(content).filter { it.isNotBlank() }
        return block["text"]
            ?.string
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    /** Inline text is either a plain string or a rich-text document. */
    private fun inlinePlain(value: JsonElement): String =
        when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonObject -> paragraphs(value).joinToString("\n")
            else -> ""
        }

    private fun paragraphs(doc: JsonObject): List<String> {
        val out = mutableListOf<String>()
        collectParagraphs(doc, out, 0)
        return out.map { it.trim() }
    }

    private fun collectParagraphs(
        node: JsonObject,
        out: MutableList<String>,
        depth: Int,
    ) {
        if (depth > 20) return
        val children = node["content"]?.array.orEmpty().mapNotNull { it.obj }
        val isParagraph = node["type"]?.string == "paragraph" || children.any { it["type"]?.string in INLINE_TYPES }
        if (isParagraph) {
            out += children.joinToString("") { inlineText(it) }
            return
        }
        for (child in children) collectParagraphs(child, out, depth + 1)
    }

    private fun inlineText(node: JsonObject): String =
        when (node["type"]?.string) {
            "text" -> node["text"]?.string.orEmpty()
            "math" ->
                node["attrs"]
                    ?.obj
                    ?.get("source")
                    ?.string
                    .orEmpty()
            "hardBreak" -> "\n"
            "pause" -> " "
            else -> ""
        }

    private fun imageCaption(block: JsonObject): String? {
        val caption = block["caption"]?.string?.trim().orEmpty()
        // The hub stores the credit inside the caption after " (by "; readers show the words before it.
        val words = caption.substringBefore(" (by ").trim()
        return words.ifBlank { null }?.takeUnless { it.equals("Image", ignoreCase = true) }
    }

    private fun vaktText(block: JsonObject): String? =
        block["text"]
            ?.string
            ?.trim()
            ?.removePrefix("VAKT:")
            ?.trim()
            ?.ifBlank { null }

    private val INLINE_TYPES = setOf("text", "footnote", "math", "pause", "hardBreak")

    private val JsonElement.obj: JsonObject? get() = this as? JsonObject
    private val JsonElement.array: JsonArray? get() = this as? JsonArray
    private val JsonElement.string: String? get() =
        (this as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.contentOrNull
    private val JsonElement.int: Int? get() = (this as? JsonPrimitive)?.intOrNull
}
