package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun chapterListCanMixOldAndSecureChapters() {
        val chapters = json.decodeFromString<List<ChapterDto>>(
            """
            [
                {"id":"new","seriesSlug":"series","number":2,"title":"Chapter 2","slug":"chapter-2","deliverySystem":"secure","isLocked":true},
                {"id":"old","seriesSlug":"series","number":1.5,"title":"Chapter 1.5","slug":"chapter-1-5","isLocked":false}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("new", "old"), chapters.map { it.id })
        assertEquals(listOf("secure", "legacy"), chapters.map { it.deliverySystem })
        assertTrue(chapters.first().isLocked)
        assertEquals(1.5f, chapters.last().number)
    }

    @Test
    fun oldChapterDetailRetainsItsDirectImageUrl() {
        val chapter = json.decodeFromString<ChapterDto>(
            """
            {
                "id":"old","seriesSlug":"series","number":1,"title":"Chapter 1","slug":"chapter-1","isLocked":false,
                "pages":[{"index":0,"url":"https://example.org/1.webp"}]
            }
            """.trimIndent(),
        )

        assertEquals("legacy", chapter.deliverySystem)
        assertEquals("https://example.org/1.webp", chapter.pages.orEmpty().single().url)
    }
}
