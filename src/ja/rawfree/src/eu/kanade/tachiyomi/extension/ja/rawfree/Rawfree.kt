package eu.kanade.tachiyomi.extension.ja.rawfree

import eu.kanade.tachiyomi.multisrc.zing.GenreFilter
import eu.kanade.tachiyomi.multisrc.zing.ZingBase
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Rawfree : ZingBase() {
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(pageUrl(1, "ranking").build(), Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select(".entry-ma").map(::mangaFromCard), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listPage(pageUrl(page, "browse"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val document = client.get(pageUrl(page).addQueryParameter("s", query).build(), Fetch.DOCUMENT).asJsoup()
            // Past the last page and for no results the site redirects to its home page
            val mangas = document.select("body.search-results .entry-ma").map(::mangaFromCard)
            return MangasPage(mangas, mangas.isNotEmpty())
        }
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
            ?: return getLatestUpdates(page)
        return listPage(pageUrl(page, CATEGORY_PATH, genre))
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val list = url.pathSegments.firstOrNull()?.takeIf { it in LIST_PATHS }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() }
        if (!isSiteHost(url) || list == null || slug == null) return super.getMangasByUrl(url, page)
        return listPage(pageUrl(page, list, slug))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteHost(url)) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when {
            segments.size == 2 && segments[0] == MANGA_PATH -> segments[1]
            // A chapter page links its series
            segments.size == 1 -> {
                val chapter = client.get(pageUrl(1, segments[0]).build(), Fetch.DOCUMENT).asJsoup()
                chapter.selectFirst(".manga-name a")?.absUrl("href")?.toHttpUrl()?.pathSegments?.getOrNull(1) ?: return null
            }
            else -> return null
        }
        return fetchSeries(pageUrl(1, MANGA_PATH, slug).build())
    }

    override fun parseDetails(document: Document) = SManga.create().apply {
        val info = document.selectFirst(".ma-box")!!
        title = info.selectFirst("h1.name")!!.text().removeTitleSuffix()
        thumbnail_url = info.selectFirst(".main-thumb img")?.absUrl("src")
        genre = info.select(".genres-wrap a").joinToString { it.text() }.ifEmpty { null }
        status = when (info.selectFirst("h1.name + span")?.text()) {
            "OnGoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(info.selectFirst("div:has(> h1.name) + p")?.textOrNull())
            .description(info.selectFirst(".note-box"))
            .build()
    }

    override fun parseChapters(document: Document) = document.select(".entry-chapter").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".date")?.text(), ZONE)
        }
    }

    override fun readerFields(document: Document) = postIdReaderFields(document)

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(pageUrl(1, "browse").build(), Fetch.DOCUMENT).asJsoup()
        return document.select(".hero-genres a").map { link ->
            link.text() to link.absUrl("href").toHttpUrl().pathSegments[1]
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>()?.takeIf { it.isNotEmpty() } ?: return FilterList()
        return FilterList(
            Filter.Header("キーワード検索ではジャンルは無視されます"),
            GenreFilter(genres),
        )
    }

    private fun pageUrl(page: Int, vararg segments: String) = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        if (segments.isNotEmpty() || page > 1) addPathSegment("")
    }

    private suspend fun listPage(url: HttpUrl.Builder): MangasPage {
        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".entry-ma").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst(".pagination a.next") != null)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".ma-name a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().removeTitleSuffix()
        thumbnail_url = element.selectFirst(".thumb img")?.absUrl("src")
    }

    companion object {
        private const val MANGA_PATH = "manga-raw"
        private const val CATEGORY_PATH = "category"
        private val LIST_PATHS = setOf(CATEGORY_PATH, "manga-tag")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月 d, yyyy", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Tokyo")
    }
}
