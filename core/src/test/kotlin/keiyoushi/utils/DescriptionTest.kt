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

    private fun JsonObject.blocks(type: String) = getArray("sections").map { it.obj }.filter { it.getString("type") == type }

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
    fun `typed facts and blocks are recorded`() {
        val meta = manga(block = full).meta
        assertEquals(META_VERSION, meta.getInt("v"))
        assertEquals(listOf("A", "B"), meta.getArray("altTitles").map { it.string })
        val rating = meta.getObject("rating")
        assertEquals(4.5, rating.getString("value").toDouble(), 0.0)
        assertEquals(5, rating.getInt("max"))
        assertEquals(10L, rating.getLong("votes"))
        assertEquals(listOf("fields", "titles", "links"), meta.getArray("sections").map { it.obj.getString("type") })
        assertEquals("Publisher", meta.blocks("fields").single().getArray("fields").single().obj.getString("label"))
        assertEquals("Other names", meta.blocks("titles").single().getString("label"))
        assertEquals("https://anilist.co/manga/1", meta.blocks("links").single().getArray("links").single().obj.getString("url"))
    }

    @Test
    fun `blocks the app shows leave the description`() {
        assertEquals("★★★★✬ 4.5 (10)\n2021 · Manhwa\n\nSynopsis", manga(setOf("sections"), full).description)
        assertEquals("2021 · Manhwa\n\nSynopsis\n\n**Publisher:** Pub\n\n**Other names:**\n- A\n- B\n\n**Links:**\n- [AniList](https://anilist.co/manga/1)", manga(setOf("rating"), full).description)
        assertEquals("2021 · Manhwa\n\nSynopsis", manga(setOf("sections", "rating"), full).description)
    }

    @Test
    fun `a left out block still ends the line before it`() {
        val manga = manga(setOf("sections")) {
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
    fun `fields merge into one block and keep summary`() {
        val meta = manga {
            field("Type", "Manhwa", summary = true)
            field("Year:", "2021", summary = true)
            meta { field("Views", "1.2K") }
            altTitles(listOf("A"))
            field("Publisher", listOf("P1", " ", "P2"))
        }.meta
        val fields = meta.blocks("fields")
        assertEquals(2, fields.size)
        val first = fields[0].getArray("fields").map { it.obj }
        assertEquals(listOf("Type", "Year", "Views"), first.map { it.getString("label") })
        assertEquals(listOf(true, true, false), first.map { it.getBooleanOrNull("summary") == true })
        assertEquals("P1, P2", fields[1].getArray("fields").single().obj.getString("value"))
    }

    @Test
    fun `chips and text blocks`() {
        val meta = manga {
            meta {
                chips("Tags", listOf("Magic", " ", "Magic"))
                chips("More tags", listOf("Gore"), search = false, collapsed = true)
                chips("Spoilers", listOf("Death"), spoiler = true)
                chips("Empty", emptyList())
                text("Note")
            }
        }.meta
        val chips = meta.blocks("chips")
        assertEquals(listOf("Tags", "More tags", "Spoilers"), chips.map { it.getString("label") })
        assertEquals(listOf("Magic"), chips[0].getArray("values").map { it.string })
        assertEquals(true, chips[0].getBoolean("search"))
        assertEquals(false, "search" in chips[1])
        assertEquals(true, chips[1].getBoolean("collapsed"))
        assertEquals(true, chips[2].getBoolean("spoiler"))
        assertEquals("Note", meta.blocks("text").single().getString("markdown"))
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
    fun `typed facts`() {
        val meta = manga {
            meta {
                contentRating = MangaMeta.ContentRating.ADULT
                primaryTags = listOf("Action", " ", "Action")
                altTitles = listOf("", " A ", "A")
            }
        }.meta
        assertEquals(setOf("v", "altTitles", "contentRating", "primaryTags"), meta.keys)
        assertEquals("adult", meta.getString("contentRating"))
        assertEquals(listOf("Action"), meta.getArray("primaryTags").map { it.string })
        assertEquals(listOf("A"), meta.getArray("altTitles").map { it.string })
    }

    @Test
    fun `values outside the schema are left out`() {
        val meta = manga {
            rating(5.0, max = 0)
            links(listOf("A" to "ftp://x", "B" to "https://b.test/a b", " " to "https://c.test"))
            field("Empty", " ")
        }.meta
        assertEquals(setOf("v", "sections"), meta.keys)
        val link = meta.blocks("links").single().getArray("links").single().obj
        assertEquals("https://b.test/a%20b", link.getString("url"))
    }

    @Test
    fun `alt titles leave out the title`() {
        val meta = TestManga().apply {
            title = "Main"
            describe(emptySet()) { altTitles(listOf("Main", "Other")) }
        }.meta
        assertEquals(listOf("Other"), meta.getArray("altTitles").map { it.string })
        assertEquals(listOf("Other"), meta.blocks("titles").single().getArray("values").map { it.string })
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
            setMeta(emptySet()) {
                locked = true
                volume = "2"
                scanlators = listOf("A", "B")
            }
        }
        assertEquals("🔒 Chapter 1", chapter.name)
        val meta = chapter.memo[MEMO_META]!!.obj
        assertEquals(true, meta.getBoolean("locked"))
        assertEquals("2", meta.getString("volume"))
        assertEquals(listOf("A", "B"), meta.getArray("scanlators").map { it.string })
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
