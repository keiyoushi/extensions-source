package keiyoushi.utils

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptionTest {
    @Suppress("ktlint:standard:property-naming")
    private class TestManga : SManga {
        override var url = ""
        override var title = ""
        override var artist: String? = null
        override var author: String? = null
        override var description: String? = null
        override var genre: String? = null
        override var status = 0
        override var thumbnail_url: String? = null
        override var update_strategy = UpdateStrategy.ALWAYS_UPDATE
        override var initialized = false
        override var memo = JsonObject(emptyMap())
    }

    @Suppress("ktlint:standard:property-naming")
    private class TestChapter : SChapter {
        override var url = ""
        override var name = ""
        override var date_upload = 0L
        override var chapter_number = -1f
        override var scanlator: String? = null
        override var memo = JsonObject(emptyMap())
    }

    private val full: MangaDescription.() -> Unit = {
        rating(4.5, max = 5, votes = 10)
        info("2021", "Manhwa")
        description("Synopsis")
        field("Publisher", "Pub")
        altTitles(listOf("A", " B ", "A", ""), "Other names:")
        links(listOf("AniList" to "https://anilist.co/manga/1", "MAL" to null))
    }

    private fun manga(shown: Set<String> = emptySet(), block: MangaDescription.() -> Unit) = TestManga().apply { describe(shown, block) }

    private val SManga.meta get() = memo[MEMO_META]!!.obj

    @Test
    fun `trackers are stored by key and listed unless shown`() {
        val block: MangaDescription.() -> Unit = {
            trackers(MangaMeta.Tracker.ANILIST to 30002, MangaMeta.Tracker.MANGAUPDATES to "12345", MangaMeta.Tracker.KITSU to " ")
            trackers(listOf("https://myanimelist.net/manga/2/Berserk", "https://www.mangaupdates.com/series/abc", "https://example.org/x", null))
        }
        val manga = manga(block = block)
        val trackers = manga.meta.getObject("trackers")
        assertEquals(mapOf("anilist" to "30002", "mangaupdates" to "12345", "myanimelist" to "2"), trackers.mapValues { it.value.string })
        assertEquals(
            "**Trackers:**\n- [AniList](https://anilist.co/manga/30002)\n- [MangaUpdates](https://www.mangaupdates.com/series.html?id=12345)\n\n" +
                "**Trackers:**\n- [MyAnimeList](https://myanimelist.net/manga/2)",
            manga.description,
        )
        assertNull(manga(setOf("trackers"), block).description)
    }

    @Test
    fun `description matches the deprecated builder`() {
        @Suppress("DEPRECATION")
        val old = MarkdownDescription.Builder()
            .rating(4.5, max = 5, votes = 10)
            .info("2021", "Manhwa")
            .description("Synopsis")
            .field("Publisher", "Pub")
            .altTitles(listOf("A", " B ", "A", ""), "Other names")
            .links(listOf("AniList" to "https://anilist.co/manga/1", "MAL" to null))
            .build()
        assertEquals(old, manga(block = full).description)
    }

    @Test
    fun `rating, alt titles and links are recorded`() {
        val meta = manga(block = full).meta
        assertEquals(setOf("v", "rating", "altTitles", "links"), meta.keys)
        assertEquals(META_VERSION, meta.getInt("v"))
        assertEquals(listOf("A", "B"), meta.getArray("altTitles").map { it.string })
        val rating = meta.getObject("rating")
        assertEquals(4.5, rating.getString("value").toDouble(), 0.0)
        assertEquals(5, rating.getInt("max"))
        assertEquals(10L, rating.getLong("votes"))
        val link = meta.getArray("links").single().obj
        assertEquals(setOf("label", "url"), link.keys)
        assertEquals("AniList", link.getString("label"))
        assertEquals("https://anilist.co/manga/1", link.getString("url"))
    }

    @Test
    fun `keys the app shows leave the description`() {
        val shown = setOf("rating", "altTitles", "links")
        assertEquals("2021 · Manhwa\n\nSynopsis\n\n**Publisher:** Pub", manga(shown, full).description)
        assertEquals("2021 · Manhwa\n\nSynopsis\n\n**Publisher:** Pub\n\n**Other names:**\n- A\n- B\n\n**Links:**\n- [AniList](https://anilist.co/manga/1)", manga(setOf("rating"), full).description)
        assertEquals("★★★★✬ 4.5 (10)\n2021 · Manhwa\n\nSynopsis\n\n**Publisher:** Pub\n\n**Links:**\n- [AniList](https://anilist.co/manga/1)", manga(setOf("altTitles"), full).description)
        assertEquals(manga(block = full).meta, manga(shown, full).meta)
    }

    @Test
    fun `a left out section still ends the line before it`() {
        val manga = manga(setOf("altTitles")) {
            info("2021")
            altTitles(listOf("A"))
            info("Manga")
        }
        assertEquals("2021\n\nManga", manga.description)
    }

    @Test
    fun `info after a left out rating starts its own line`() {
        val manga = manga(setOf("rating")) {
            info("Ongoing")
            rating(7.0)
            info("2021")
        }
        assertEquals("Ongoing\n\n2021", manga.description)
    }

    @Test
    fun `fields and info are printed, not recorded`() {
        val manga = manga {
            field("Type", "Manhwa")
            field("Year:", "2021")
            field("Publisher", listOf("P1", " ", "P2"))
            info("Ongoing")
        }
        assertEquals("**Type:** Manhwa · **Year:** 2021 · **Publisher:** P1, P2 · Ongoing", manga.description)
        assertNull(manga.memo[MEMO_META])
    }

    @Test
    fun `links are one flat list with their language`() {
        val manga = manga {
            links(listOf("Daum" to "https://webtoon.kakao.com/x"), "Original Webtoon", language = "ko")
            links(listOf("English" to "https://tapas.io/x", "Daum again" to "https://webtoon.kakao.com/x"), "Official Translations", language = "en")
            meta { links(listOf("Store" to "https://store.test/x"), language = "not a tag") }
        }
        val links = manga.meta.getArray("links").map { it.obj }
        assertEquals(listOf("Daum", "English", "Store"), links.map { it.getString("label") })
        assertEquals(listOf("ko", "en", null), links.map { it.getStringOrNull("language") })
        assertEquals(
            "**Original Webtoon:**\n- [Daum](https://webtoon.kakao.com/x)\n\n" +
                "**Official Translations:**\n- [English](https://tapas.io/x)\n- [Daum again](https://webtoon.kakao.com/x)",
            manga.description,
        )
    }

    @Test
    fun `nothing recorded leaves memo alone`() {
        val manga = TestManga().apply {
            memo = buildJsonObject { put("slug", "x") }
            describe(emptySet()) { description("Synopsis") }
        }
        assertEquals(buildJsonObject { put("slug", "x") }, manga.memo)
        assertEquals("Synopsis", manga.description)
    }

    @Test
    fun `other memo keys are kept and a stale meta is dropped`() {
        val manga = TestManga().apply {
            memo = buildJsonObject { put("slug", "x") }
            describe(emptySet()) { altTitles(listOf("A")) }
        }
        assertEquals(JsonPrimitive("x"), manga.memo["slug"])
        manga.describe(emptySet()) { description("Synopsis") }
        assertNull(manga.memo[MEMO_META])
        assertEquals(JsonPrimitive("x"), manga.memo["slug"])
    }

    @Test
    fun `facts only stored`() {
        val manga = manga {
            meta {
                contentRating = MangaMeta.ContentRating.ADULT
                altTitles = listOf("", " A ", "A")
                rating(8, votes = 3)
                trackers(MangaMeta.Tracker.ANILIST to 1)
            }
        }
        val meta = manga.meta
        assertEquals(setOf("v", "rating", "contentRating", "altTitles", "trackers"), meta.keys)
        assertEquals("adult", meta.getString("contentRating"))
        assertEquals(listOf("A"), meta.getArray("altTitles").map { it.string })
        assertNull(manga.description)
    }

    @Test
    fun `values outside the schema are left out`() {
        val meta = manga {
            rating(5.0, max = 0)
            links(listOf("A" to "ftp://x", "B" to "https://b.test/a b", " " to "https://c.test"))
        }.meta
        assertEquals(setOf("v", "links"), meta.keys)
        val link = meta.getArray("links").single().obj
        assertEquals("https://b.test/a%20b", link.getString("url"))
    }

    @Test
    fun `alt titles leave out the title`() {
        val manga = TestManga().apply {
            title = "Main"
            describe(emptySet()) { altTitles(listOf(" Main ", "Other")) }
        }
        assertEquals(listOf("Other"), manga.meta.getArray("altTitles").map { it.string })
        assertEquals("**Alternative Names:**\n- Other", manga.description)
    }

    @Test
    fun `the first of repeated trackers wins and is listed`() {
        val manga = manga { trackers(MangaMeta.Tracker.ANILIST to 1, MangaMeta.Tracker.ANILIST to 2) }
        assertEquals("1", manga.meta.getObject("trackers").getString("anilist"))
        assertEquals("**Trackers:**\n- [AniList](https://anilist.co/manga/1)", manga.description)
    }

    @Test
    fun `tracker pages give the tracker's id`() {
        assertEquals(MangaMeta.Tracker.SHIKIMORI to "116778", MangaMeta.Tracker.fromUrl("https://shikimori.one/mangas/z116778-chainsaw-man"))
        assertEquals(MangaMeta.Tracker.SHIKIMORI to "2", MangaMeta.Tracker.fromUrl("https://shikimori.io/mangas/2-berserk"))
        assertEquals(MangaMeta.Tracker.SHIKIMORI to "2", MangaMeta.Tracker.fromUrl("https://shikimori.io/mangas/2"))
        assertNull(MangaMeta.Tracker.fromUrl("https://shikimori.io/mangas/berserk"))
    }

    @Test
    fun `float rating keeps its decimal value`() {
        assertEquals("9.1", manga { rating(9.1f) }.meta.getObject("rating").getString("value"))
    }

    @Test
    fun `locked chapter gets the lock unless the app shows it`() {
        val chapter = TestChapter().apply {
            name = "Chapter 1"
            memo = buildJsonObject { put("id", 1) }
            setMeta(emptySet()) { locked = true }
        }
        assertEquals("🔒 Chapter 1", chapter.name)
        val meta = chapter.memo[MEMO_META]!!.obj
        assertEquals(setOf("v", "locked"), meta.keys)
        assertEquals(true, meta.getBoolean("locked"))
        assertEquals(JsonPrimitive(1), chapter.memo["id"])

        val shown = TestChapter().apply {
            name = "Chapter 1"
            setMeta(setOf("locked")) { locked = true }
        }
        assertEquals("Chapter 1", shown.name)

        chapter.setMeta(emptySet()) { }
        assertNull(chapter.memo[MEMO_META])
    }

    @Test
    fun `a chapters-only result keeps the stored meta`() {
        val stored = manga { altTitles(listOf("A")) }
        val fresh = TestManga().apply { memo = buildJsonObject { put("id", 1) } }
        fresh.keepMetaOf(stored)
        assertEquals(stored.memo[MEMO_META], fresh.memo[MEMO_META])
        assertEquals(JsonPrimitive(1), fresh.memo["id"])
    }

    @Test
    fun `putMemo keeps the other keys and meta`() {
        val manga = manga { altTitles(listOf("A")) }.apply {
            putMemo { put("slug", "x") }
            putMemo(buildJsonObject { put("slug", "y") })
        }
        assertEquals(JsonPrimitive("y"), manga.memo["slug"])
        assertEquals(listOf("A"), manga.meta.getArray("altTitles").map { it.string })
    }
}
