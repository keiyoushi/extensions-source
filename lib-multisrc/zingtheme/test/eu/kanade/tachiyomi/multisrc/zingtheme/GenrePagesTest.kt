package eu.kanade.tachiyomi.multisrc.zingtheme

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GenrePagesTest {
    @Test
    fun exhaustedGenreCannotHideAnotherGenreFailure() = runBlocking {
        val failure = IOException("temporary listing failure")
        try {
            mergePages(listOf("exhausted", "failed"), 3) { genre, page ->
                check(page == 3)
                if (genre == "failed") throw failure
                MangasPage(emptyList(), false)
            }
            throw AssertionError("A failed genre page must be retryable")
        } catch (actual: IOException) {
            assertEquals(failure.message, actual.message)
        }
    }

    @Test
    fun aCancelledGenreCannotProduceAPartialPage() = runBlocking {
        val cancellation = CancellationException("cancelled genre")
        try {
            mergePages(listOf("exhausted", "cancelled"), 1) { genre, _ ->
                if (genre == "cancelled") throw cancellation
                MangasPage(emptyList(), false)
            }
            throw AssertionError("Genre cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
    }

    @Test
    fun successfulExhaustedPagesRemainEmpty() = runBlocking {
        val result = mergePages(listOf("first", "second"), 3) { _, _ -> MangasPage(emptyList(), false) }
        assertTrue(result.mangas.isEmpty())
        assertFalse(result.hasNextPage)
    }

    @Test
    fun successfulPagesStayInterleavedAndDeduplicatedWithinThePage() = runBlocking {
        val result = mergePages(listOf("first", "second"), 2) { genre, _ ->
            if (genre == "first") {
                MangasPage(listOf(TestManga("a"), TestManga("shared"), TestManga("c")), false)
            } else {
                MangasPage(listOf(TestManga("shared"), TestManga("b"), TestManga("d")), true)
            }
        }
        assertEquals(listOf("a", "shared", "b", "c", "d"), result.mangas.map { it.url })
        assertTrue(result.hasNextPage)
    }

    private class TestManga(override var url: String) : SManga {
        override var title = url

        @Suppress("ktlint:standard:property-naming")
        override var thumbnail_url: String? = null
        override var artist: String? = null
        override var author: String? = null
        override var status = SManga.UNKNOWN
        override var description: String? = null
        override var genre: String? = null

        @Suppress("ktlint:standard:property-naming")
        override var update_strategy = UpdateStrategy.ALWAYS_UPDATE
        override var memo = JsonObject(emptyMap())
        override var initialized = false
    }
}
