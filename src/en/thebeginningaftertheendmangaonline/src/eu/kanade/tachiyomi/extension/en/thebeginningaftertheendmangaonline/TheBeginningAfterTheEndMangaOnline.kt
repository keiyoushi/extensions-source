package eu.kanade.tachiyomi.extension.en.thebeginningaftertheendmangaonline

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class TheBeginningAfterTheEndMangaOnline : KeiSource() {

    override val supportsLatest = false

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = "The Beginning After The End"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(singleManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = singleManga()
        return if (manga.title.contains(query, ignoreCase = true)) {
            MangasPage(listOf(manga), false)
        } else {
            MangasPage(emptyList(), false)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return singleManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        url = "/"
        title = "The Beginning After The End"
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        // Deduplicate by chapter number, preferring the "ch-N" slug (has images)
        // over "chapter-N" duplicates.
        val byNumber = mutableMapOf<String, String>()
        document.select("a[href*='beginning-after-the-end-']")
            .map { it.attr("abs:href") }
            .forEach { href ->
                val num = Regex("(?:chapter|ch)-(\\d+)/?$").find(href)?.groupValues?.get(1) ?: return@forEach
                val existing = byNumber[num]
                if (existing == null || urlPriority(href) < urlPriority(existing)) {
                    byNumber[num] = href
                }
            }

        return byNumber.map { (chapterNum, href) ->
            SChapter.create().apply {
                url = href
                name = "Chapter $chapterNum"
                chapter_number = chapterNum.toFloatOrNull() ?: 0f
            }
        }.sortedByDescending { it.chapter_number }
    }

    // Lower is better: "ch-N" slug wins (has images)
    private fun urlPriority(url: String): Int = when {
        Regex("/ch-\\d+/?$").containsMatchIn(url) -> 0
        else -> 1
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.url).asJsoup()
        return document.select(".entry-content img")
            .mapNotNull { img ->
                // Pages are lazy-loaded: src holds a data: placeholder, the real
                // image is in data-src (Blogger chapters) or data-src/srcset
                // (Kadence gallery chapters).
                val url = img.attr("data-src")
                    .ifBlank { img.attr("data-lazy-src") }
                    .ifBlank { img.attr("srcset").substringBefore(',').substringBefore(' ').trim() }
                    .ifBlank { img.attr("abs:src") }
                url.takeIf { it.startsWith("http") && isPageImage(it) }
            }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun isPageImage(url: String): Boolean {
        if ("cropped-" in url) return false // site logo and other cropped assets
        return "blogger.googleusercontent.com" in url ||
            "bp.blogspot.com" in url ||
            "img.thebeginningaftertheendmanga.com" in url
    }
}
