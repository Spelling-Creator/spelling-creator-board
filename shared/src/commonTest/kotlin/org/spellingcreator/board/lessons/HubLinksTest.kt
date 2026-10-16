package org.spellingcreator.board.lessons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubLinksTest {
    @Test
    fun aHubLinkNamesItsOwnHub() {
        val address =
            HubLinks.parse(
                "https://spelling.example.org/hub/abc-123/practice?x=1",
                "https://spellingcreator.org",
            )
        assertEquals(LessonAddress("https://spelling.example.org", "abc-123"), address)
        assertEquals("https://spelling.example.org/lessons/abc-123", HubLinks.lessonUrl(address!!))
    }

    @Test
    fun aBareIdUsesTheConfiguredHub() {
        assertEquals(LessonAddress("https://my.hub", "abc"), HubLinks.parse("  abc ", "my.hub/"))
    }

    @Test
    fun linksWithoutASchemeAndWithoutHubStillWork() {
        assertEquals(
            LessonAddress("https://spellingcreator.org", "abc"),
            HubLinks.parse("spellingcreator.org/hub/abc", "x"),
        )
        assertEquals(LessonAddress("https://h.org", "abc"), HubLinks.parse("https://h.org/abc", "x"))
        assertNull(HubLinks.parse("https://h.org/users/42", "x"))
        assertNull(HubLinks.parse("", "x"))
    }

    @Test
    fun baseUrlsAreNormalized() {
        assertEquals("https://spellingcreator.org", HubLinks.normalizeBaseUrl(""))
        assertEquals("http://localhost:8080", HubLinks.normalizeBaseUrl("http://localhost:8080/"))
        assertEquals("https://spellingcreator.org/lessons", HubLinks.listingUrl("spellingcreator.org"))
    }
}
