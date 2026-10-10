package eu.kanade.tachiyomi.extension.en.maidsamamangaonline

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
abstract class MaidSamaMangaOnline : KeiSource() {

    override val supportsLatest = false

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = "Maid Sama"
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
        title = "Maid Sama"
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    private val chapterSlugRegex = Regex("maid-sama-chapter-(.+?)/?$")
    private val specialRegex = Regex("oneshot-special-(\\d+)")

    private fun parseChapterList(document: Document): List<SChapter> {
        val seen = mutableSetOf<String>()
        return document.select("a[href*='maid-sama-chapter-']")
            .map { it.attr("abs:href") }
            .mapNotNull { href ->
                val slug = chapterSlugRegex.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!seen.add(slug)) return@mapNotNull null
                SChapter.create().apply {
                    url = href
                    name = chapterName(slug)
                    chapter_number = chapterNumber(slug)
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    private fun chapterName(slug: String): String {
        val special = specialRegex.find(slug)
        if (special != null) return "Oneshot Special ${special.groupValues[1]}"
        return "Chapter " + slug.replace("-", ".")
    }

    private fun chapterNumber(slug: String): Float {
        val special = specialRegex.find(slug)
        if (special != null) return 86f + special.groupValues[1].toFloat() / 100f
        var number = 0f
        var divisor = 1f
        slug.split("-").forEach { part ->
            number += (part.toFloatOrNull() ?: 0f) / divisor
            divisor *= 10f
        }
        return number
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
            "img.maid-sama.com" in url
    }
}
