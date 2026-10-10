package eu.kanade.tachiyomi.extension.en.theexiledheavyknightmangaonline

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
abstract class TheExiledHeavyKnightMangaOnline : KeiSource() {

    override val supportsLatest = false

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = "The Exiled Heavy Knight"
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
        title = "The Exiled Heavy Knight"
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    private val chapterSlugRegex =
        Regex("the-exiled-heavy-knight-knows-how-to-game-the-system-chapter-(\\d+)/?$")

    private fun parseChapterList(document: Document): List<SChapter> {
        val seen = mutableSetOf<String>()
        return document.select("a[href*='the-exiled-heavy-knight-knows-how-to-game-the-system-chapter-']")
            .map { it.attr("abs:href") }
            .mapNotNull { href ->
                val num = chapterSlugRegex.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!seen.add(num)) return@mapNotNull null
                SChapter.create().apply {
                    url = href
                    name = "Chapter $num"
                    chapter_number = num.toFloatOrNull() ?: 0f
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.url).asJsoup()
        return document.select(".post-single-content img, .entry-content img")
            .mapNotNull { img ->
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
            "pic.readkakegurui.com" in url ||
            "img.theexiledheavyknight.com" in url ||
            "theexiledheavyknight.com/wp-content/uploads" in url
    }
}
