package eu.kanade.tachiyomi.extension.en.manhuaplustop

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.ZonedDateTime

@Source
abstract class ManhuaPlusTop : WPComics() {

    override suspend fun getPopularManga(page: Int): MangasPage = allManga(page, "views")

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li"

    override suspend fun getLatestUpdates(page: Int): MangasPage = allManga(page, "latest-updated")

    private suspend fun allManga(page: Int, sort: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("all-manga")
            .addPathSegment(page.toString())
            .addPathSegment("")
            .addQueryParameter("sort", sort)
            .build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), popularMangaSelector(), ::popularMangaFromElement)
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

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), searchMangaSelector(), ::searchMangaFromElement)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2 || url.pathSegments[0] != "manga") return null

        val manga = SManga.create().apply { this.url = "/manga/${url.pathSegments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply { this.url = manga.url }
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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
        thumbnail_url = info.selectFirst("div.col-image img")?.let(::imageOrNull)
        description = MarkdownDescription.Builder()
            .description(info.selectFirst("div.detail-content p"), keepNewlines = true)
            .altTitles(info.selectFirst("h2.other-name")?.text()?.split(ALT_TITLE_SEPARATOR).orEmpty().filterNot { it == title })
            .build()
    }

    override fun String?.toDate(): Long {
        val match = this?.let { RELATIVE_DATE_REGEX.matchEntire(it.trim()) } ?: return 0L
        val amount = match.groupValues[1].toLong()
        val now = ZonedDateTime.now(dateZone)
        val date = when (match.groupValues[2]) {
            "s" -> now.minusSeconds(amount)
            "m" -> now.minusMinutes(amount)
            "h" -> now.minusHours(amount)
            "d" -> now.minusDays(amount)
            "w" -> now.minusWeeks(amount)
            "M" -> now.minusMonths(amount)
            "y" -> now.minusYears(amount)
            else -> return 0L
        }
        return date.toInstant().toEpochMilli()
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
            .mapNotNull(::imageOrNull)
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    private val ajaxHeaders
        get() = headersBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

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

    companion object {
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)([smhdwMy]) ago""")
        private val ALT_TITLE_SEPARATOR = Regex("""\s*;\s*|\s+/\s+""")
        private val CHAPTER_ID_REGEX = Regex("""CHAPTER_ID\s*=\s*(\d+)""")
    }
}
