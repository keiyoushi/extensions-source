package eu.kanade.tachiyomi.extension.en.manhuaplustop

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime

@Source
abstract class ManhuaPlusTop : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = allManga(page, "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = allManga(page, "latest-updated")

    private suspend fun allManga(page: Int, sort: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("all-manga")
            .addPathSegment(page.toString())
            .addPathSegment("")
            .addQueryParameter("sort", sort)
            .build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(if (query.isNotBlank()) "search" else "filter")
            addPathSegment(page.toString())
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            } else {
                filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            }
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.items div.item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3 a")!!
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("div.image img")?.imageUrl()
            }
        }

        return MangasPage(mangas, document.selectFirst("ul.pagination li.active + li") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2 || url.pathSegments[0] != "manga") return null

        val manga = SManga.create().apply { this.url = "/manga/${url.pathSegments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return SMangaUpdate(
            parseDetails(document).apply { url = manga.url },
            document.select("div.list-chapter li.row:not(.heading)").map(::parseChapter),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("article#item-detail")!!
        title = info.selectFirst("h1.title-detail")!!.text()
        author = info.select("li.author p.col-xs-8 a").joinToString { it.text() }.ifEmpty { null }
        genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }.ifEmpty { null }
        status = when (info.selectFirst("li.status p.col-xs-8")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = info.selectFirst("div.col-image img")?.imageUrl()
        description = MarkdownDescription.Builder()
            .description(info.selectFirst("div.detail-content p"), keepNewlines = true)
            .altTitles(info.selectFirst("h2.other-name")?.text()?.split(ALT_TITLE_SEPARATOR).orEmpty().filterNot { it == title })
            .build()
    }

    private fun parseChapter(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a")!!
        name = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        date_upload = parseRelativeDate(element.select("div.col-xs-4").text())
    }

    private fun parseRelativeDate(date: String): Long {
        val match = RELATIVE_DATE_REGEX.matchEntire(date) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val now = ZonedDateTime.now()
        val dateTime = when (match.groupValues[2]) {
            "s" -> now.minusSeconds(amount)
            "m" -> now.minusMinutes(amount)
            "h" -> now.minusHours(amount)
            "d" -> now.minusDays(amount)
            "w" -> now.minusWeeks(amount)
            "M" -> now.minusMonths(amount)
            "y" -> now.minusYears(amount)
            else -> return 0L
        }
        return dateTime.toInstant().toEpochMilli()
    }

    // The chapter page sometimes has only loading placeholders: its script always gets the images from this endpoint
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val script = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(const CHAPTER_ID)")!!.data()
        val chapterId = CHAPTER_ID_REGEX.find(script)!!.groupValues[1]

        val images = client.post("$baseUrl/ajax/image/list/chap/$chapterId", ajaxHeaders, Fetch.XHR.from(chapterUrl), "".toRequestBody())
            .parseAs<ImageListDto>()

        return Jsoup.parseBodyFragment(images.html, chapterUrl).select("div.page-chapter > img")
            .mapNotNull { it.imageUrl() }
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    private val ajaxHeaders
        get() = headersBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/filter", Fetch.DOCUMENT).asJsoup()

        return FilterData(
            genres = document.select(".genre-item span[data-id]").map { it.parent()!!.text() to it.attr("data-id") },
            chapterCounts = document.options("#select-minchapter"),
            statuses = document.options("#select-status"),
            sexes = document.options("#select-gender"),
            sorts = document.options("#select-sort"),
        ).toJsonElement()
    }

    private fun Document.options(selector: String) = select("$selector option").map { it.text() to it.attr("value") }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()

        return FilterList(
            Filter.Header("Ignored when searching by text"),
            GenresFilter(filterData.genres),
            ChapterCountFilter(filterData.chapterCounts),
            SeriesStatusFilter(filterData.statuses),
            SexFilter(filterData.sexes),
            SortFilter(filterData.sorts),
        )
    }

    private fun Element.imageUrl(): String? = attrOrNull("abs:data-original") ?: attrOrNull("abs:src")

    companion object {
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)([smhdwMy]) ago""")
        private val ALT_TITLE_SEPARATOR = Regex("""\s*;\s*|\s+/\s+""")
        private val CHAPTER_ID_REGEX = Regex("""CHAPTER_ID\s*=\s*(\d+)""")
    }
}
