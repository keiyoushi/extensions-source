package eu.kanade.tachiyomi.extension.en.hentai18

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class Hentai18 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl("top-hentai", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl("new-manga-hentai", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("s", query.trim())
                .build()
            return getMangaList(url)
        }
        val tag = filters.firstInstanceOrNull<TagFilter>()?.state?.trim()?.lowercase()?.replace(' ', '-')
        val path = tag?.takeIf(String::isNotEmpty)?.let { "tag/$it" }
            ?: filters.firstInstanceOrNull<ListFilter>()?.path
            ?: "top-hentai"
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value?.takeIf { path.startsWith("category/") || path.startsWith("tag/") }
        return getMangaList(listUrl(path, page, sort))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "read-hentai") return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Chapter links share the series' path prefix and point back to their series
        val seriesPath = document.selectFirst("[data-comic-url]")?.attr("data-comic-url")
            ?: return parseDetails(document, url.encodedPath).apply { initialized = true }
        val manga = SManga.create().apply { this.url = seriesPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("#chapter-list a.hx-ep").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".hx-ep-name")!!.text()
                date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("time.hx-ep-date")?.attrOrNull("datetime"), ZoneOffset.UTC)
            }
        }
        return SMangaUpdate(parseDetails(document, manga.url), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".hx-pages img[data-page]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun parseDetails(document: Document, path: String): SManga = SManga.create().apply {
        url = path
        val info = document.selectFirst(".hx-detail-info")!!
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("[data-share-image]")?.attrOrNull("data-share-image")
            ?: document.selectFirst(".hx-detail-poster img")?.largestImage()
        author = info.select("a.hx-pill-artist").eachText().joinToString().ifEmpty { null }
        artist = author
        genre = (info.select(".hx-pills a[href^=/category/]").eachText() + info.select(".hx-genres a.hx-genre").eachText())
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (info.selectFirst(".hx-pill-status, .hx-pill-done")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        // Alternative titles come escaped twice ("Maiden&#039;s")
        val altTitles = info.selectFirst(".hx-detail-alt")?.ownText()
            ?.let { Parser.unescapeEntities(it, false) }
            ?.split(" / ", " | ")
            .orEmpty()
        description = MarkdownDescription.Builder()
            .rating(info.selectFirst("[data-rate-avg]")?.text()?.toFloatOrNull(), max = 5, votes = info.selectFirst("[data-rate-count]")?.textOrNull()?.replace(",", "")?.toIntOrNull())
            .description(info.selectFirst(".hx-synopsis-text"))
            .altTitles(altTitles)
            .build()
    }

    private fun listUrl(path: String, page: Int, sort: String? = null): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply {
            sort?.let { addQueryParameter("sort", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#comics-grid article.hx-card").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h3.hx-card-title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.largestImage()
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun Element.largestImage(): String? = attrOrNull("srcset")
        ?.split(", ")
        ?.lastOrNull()
        ?.substringBefore(' ')
        ?: absUrl("src")

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
