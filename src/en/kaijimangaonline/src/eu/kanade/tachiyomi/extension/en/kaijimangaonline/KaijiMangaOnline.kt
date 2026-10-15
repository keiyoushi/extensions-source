package eu.kanade.tachiyomi.extension.en.kaijimangaonline

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
abstract class KaijiMangaOnline : KeiSource() {

    override val supportsLatest = false

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = "Kaiji"
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
        title = "Kaiji"
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    private val chapterSlugRegex = Regex("/manga/(.+)-chapter-(\\d+)/?$")

    // The site hosts four arcs sharing chapter numbers. Offsets keep the arcs
    // apart so a descending sort reproduces the site order: kaiji, datenroku,
    // hakairoku, mokushiroku.
    private val arcOffsets = mapOf(
        "kaiji" to 0f,
        "tobaku-datenroku-kaiji" to -1000f,
        "tobaku-hakairoku-kaiji" to -2000f,
        "tobaku-mokushiroku-kaiji" to -3000f,
    )

    private fun parseChapterList(document: Document): List<SChapter> {
        val seen = mutableSetOf<String>()
        return document.select("a[href*='-chapter-']")
            .map { it.attr("abs:href") }
            .mapNotNull { href ->
                val (arc, num) = chapterSlugRegex.find(href)?.destructured ?: return@mapNotNull null
                val offset = arcOffsets[arc] ?: -4000f
                val slug = "$arc-chapter-$num"
                if (!seen.add(slug)) return@mapNotNull null
                val number = num.toFloatOrNull() ?: return@mapNotNull null
                SChapter.create().apply {
                    url = href
                    name = if (arc == "kaiji") "Chapter $num" else "${arcTitle(arc)} Chapter $num"
                    // Chapter 0 is a prologue the site lists last.
                    chapter_number = if (number == 0f) -9999f else offset + number
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    private fun arcTitle(arc: String): String = arc.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.url).asJsoup()
        return document.select(".entry-content img")
            .mapNotNull { img ->
                // Pages are lazy-loaded: src holds a data: placeholder, the real
                // image is in data-src.
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
            "img.kaijimanga.com" in url ||
            "kaijimanga.com/wp-content/uploads" in url
    }
}
