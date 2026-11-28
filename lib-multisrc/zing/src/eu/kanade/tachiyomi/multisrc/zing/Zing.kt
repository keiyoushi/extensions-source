package eu.kanade.tachiyomi.multisrc.zing

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The zing-92 child themes */
abstract class Zing : ZingBase() {
    // First path segment of series pages
    protected abstract val mangaSubString: String

    // Synopsis on the series page
    protected open val descriptionSelector = ".z-single-mg .content-text p"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select(".hero-banners .swiper-slide").map(::mangaFromCard), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = gridPage(pageUrl(page).build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // The site's tag slugs: lowercase, spaces and commas as "-"
        val tag = filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty()
            .lowercase()
            .replace(TAG_SEPARATOR_REGEX, "-")
            .trim('-')
        if (query.isEmpty() && tag.isNotEmpty()) return tagPage(tag, page)
        return gridPage(pageUrl(page).apply { if (query.isNotEmpty()) addQueryParameter("s", query) }.build())
    }

    private suspend fun tagPage(tag: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(GENRE_PATH)
            .addPathSegment(tag)
            .apply { if (page > 1) addPathSegments("page/$page") }
            .addPathSegment("")
            .build()
        val document = try {
            client.get(url, Fetch.DOCUMENT).asJsoup()
        } catch (e: HttpException) {
            // An unknown tag
            if (e.code != 404) throw e
            return MangasPage(emptyList(), false)
        }
        return gridPage(document)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (!isSiteHost(url) || segments.size < 2 || segments[0] != GENRE_PATH || segments[1].isEmpty()) {
            return super.getMangasByUrl(url, page)
        }
        return tagPage(segments[1], page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteHost(url)) return null
        val segments = url.pathSegments
        // /<mangaSubString>/page/N/ is the site's archive listing, not a series
        if (segments.size < 2 || segments[0] != mangaSubString || segments[1].isEmpty() || segments[1] == "page") return null
        return fetchSeries(baseUrl.toHttpUrl().newBuilder().addPathSegment(mangaSubString).addPathSegment(segments[1]).addPathSegment("").build())
    }

    override fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst(".z-single-mg h1.name")!!.text().removeTitleSuffix()
        thumbnail_url = document.selectFirst(".z-single-mg .main-thumb img")?.absUrl("src")
        genre = document.select(".z-single-mg .genres:not(.grid-related .genres) a").joinToString { it.text() }.ifEmpty { null }
        val altTitles = document.selectFirst(".z-single-mg p:containsOwn(Other Names:)")?.ownText()
            ?.substringAfter(':')
            ?.split(';')
            .orEmpty()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(descriptionSelector))
            .altTitles(altTitles)
            .build()
    }

    override fun parseChapters(document: Document) = document.select(".chapter-box h4 a").map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = chapterName(link).removeTitleSuffix()
            date_upload = DATE_FORMAT.tryParseDate(link.closest(".entry")?.selectFirst("p.text-muted")?.text(), ZONE)
        }
    }

    protected open fun chapterName(link: Element) = link.text()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".grid-related .entry")
        .map(::mangaFromCard)

    override fun readerFields(document: Document) = document.selectFirst("script:containsData(reading_chapter:)")
        ?.let { listOf("reading_chapter" to READING_CHAPTER_REGEX.find(it.data())!!.groupValues[1]) }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("タグは検索語が空のときに使われます"),
        TagFilter(),
    )

    private fun pageUrl(page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        if (page > 1) addPathSegments("page/$page/")
    }

    private suspend fun gridPage(url: HttpUrl) = gridPage(client.get(url, Fetch.DOCUMENT).asJsoup())

    private fun gridPage(document: Document): MangasPage {
        val mangas = document.select(".grid-of-mangas:not(.grid-of-popular) .entry").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst(".z-pagination a.next") != null)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".name a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().removeTitleSuffix()
        thumbnail_url = element.selectFirst(".thumb img")?.absUrl("src")
        genre = element.select(".genres a").joinToString { it.text() }.ifEmpty { null }
    }

    companion object {
        private const val GENRE_PATH = "manga-genre"
        private val READING_CHAPTER_REGEX = Regex("""reading_chapter:\s*(\d+)""")
        private val TAG_SEPARATOR_REGEX = Regex("""[\s,]+""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Tokyo")
    }
}
