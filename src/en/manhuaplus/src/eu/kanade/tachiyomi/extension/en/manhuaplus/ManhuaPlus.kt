package eu.kanade.tachiyomi.extension.en.manhuaplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ManhuaPlus : Madara() {
    override val filterNonMangaItems = false
    override val pageListParseSelector = ".read-container img"

    // manhuaplus.top is a mirror with a different site design,
    // so it needs its own parsing while .com keeps using the Madara theme.
    private val useTopMirror: Boolean
        get() = baseUrl.toHttpUrl().host.equals(TOP_MIRROR_HOST, ignoreCase = true)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3, period = 1.seconds) { it.host == TOP_MIRROR_HOST }

    override fun getHomeUrl(): String = if (useTopMirror) "$baseUrl/all-manga/" else super.getHomeUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = if (useTopMirror) {
        topMangaList("$baseUrl/all-manga/$page/?sort=views_month".toHttpUrl())
    } else {
        super.getPopularManga(page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (useTopMirror) {
        topMangaList("$baseUrl/all-manga/$page/?sort=last_update".toHttpUrl())
    } else {
        super.getLatestUpdates(page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (!useTopMirror) return super.getSearchMangaList(page, query, filters)

        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .apply { if (page > 1) addPathSegment(page.toString()) }
                .addQueryParameter("keyword", query)
                .build()

            return topMangaList(url)
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            .orEmpty()
            .ifEmpty { "all-manga" }
        val url = "$baseUrl/$genre".toHttpUrl().newBuilder()
            .addPathSegment(page.toString())

        filters.firstInstanceOrNull<StatusFilter>()?.toValue()
            ?.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter("status", it) }
        filters.firstInstanceOrNull<SortFilter>()?.toValue()?.let {
            url.addQueryParameter("sort", it)
        }

        return topMangaList(url.build())
    }

    private suspend fun topMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

        return MangasPage(
            document.select("figure.clearfix").map(::topMangaFromElement),
            topHasNextPage(document),
        )
    }

    private fun topMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h3 a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("div.image img")?.topImageUrl()
    }

    private fun topHasNextPage(document: Document): Boolean {
        val pageInfo = document.selectFirst("ul.pagination li.hidden")?.text()
            ?: document.selectFirst("ul.pagination li.active a")?.attr("title")
            ?: return false

        val match = PAGE_INFO_REGEX.find(pageInfo) ?: return false

        return match.groupValues[1].toInt() < match.groupValues[2].toInt()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!useTopMirror) return super.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = if (fetchDetails) topMangaDetailsParse(document) else manga
        val updatedChapters = if (fetchChapters) topChapterListParse(document) else chapters

        return SMangaUpdate(details, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!useTopMirror) return super.getMangaByUrl(url)
        if (url.host != baseUrl.toHttpUrl().host) return null

        return topMangaDetailsParse(client.get(url).asJsoup()).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = if (useTopMirror) "$baseUrl${manga.url}" else super.getMangaUrl(manga)

    private fun topMangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("article#item-detail")!!

        title = info.selectFirst("h1.title-detail")!!.text()
        thumbnail_url = info.selectFirst("div.col-image img")?.topImageUrl()
        author = info.select("ul.list-info li.author p.col-xs-8 a").joinToString { it.text() }
        status = info.selectFirst("ul.list-info li.status p.col-xs-8")?.text().toTopStatus()
        genre = info.select("ul.list-info li.kind p.col-xs-8 a").joinToString { it.text() }
        description = info.selectFirst("div.detail-content")?.text()

        setUrlWithoutDomain(document.location())
    }

    private fun String?.toTopStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun topChapterListParse(document: Document): List<SChapter> = document.select("div.list-chapter li.row").mapNotNull { element ->
        val link = element.selectFirst("div.chapter a") ?: return@mapNotNull null

        SChapter.create().apply {
            name = link.text()
            setUrlWithoutDomain(link.absUrl("href"))
            date_upload = element.selectFirst("div.col-xs-4")?.text().parseRelativeDate()
        }
    }

    private fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val match = RELATIVE_DATE_REGEX.find(this) ?: return 0L
        val number = match.groupValues[1].toInt()

        val calendar = Calendar.getInstance()
        when (match.groupValues[2]) {
            "s" -> calendar.add(Calendar.SECOND, -number)
            "m" -> calendar.add(Calendar.MINUTE, -number)
            "h" -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            "d" -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            "w" -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            "mo" -> calendar.add(Calendar.MONTH, -number)
            "y" -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    override fun getChapterUrl(chapter: SChapter): String = if (useTopMirror) "$baseUrl${chapter.url}" else super.getChapterUrl(chapter)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!useTopMirror) return super.getPageList(chapter)

        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()

        return document.select("div.page-chapter img")
            .mapNotNull { it.topImageUrl() }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, chapterUrl, imageUrl) }
    }

    private fun Element.topImageUrl(): String? = absUrl("data-original")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeIf { it.isNotBlank() && !it.startsWith("data:") }

    override val supportsFilterFetching: Boolean get() = !useTopMirror

    override fun getFilterList(data: JsonElement?): FilterList = if (useTopMirror) {
        FilterList(
            GenreFilter(),
            StatusFilter(),
            SortFilter(),
        )
    } else {
        super.getFilterList(data)
    }

    companion object {
        private const val TOP_MIRROR_HOST = "manhuaplus.top"
        private val PAGE_INFO_REGEX = """Page (\d+) / (\d+)""".toRegex()
        private val RELATIVE_DATE_REGEX = """(\d+)\s*(mo|s|m|h|d|w|y)\s+ago""".toRegex()
    }
}
