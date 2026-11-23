package eu.kanade.tachiyomi.extension.ko.nameetoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.MirrorPreferences
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NameeToon : KeiSource() {

    // Each front's old domains redirect to its current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = coroutineScope {
        (fetchableBaseUrl as MirrorPreferences).mirrors.map { (_, url) ->
            async { client.get(url, Fetch.DOCUMENT).use { it.request.url.toString() } }
        }.awaitAll()
    }

    override val supportsLatest = false

    private val yatoon = Yatoon(this)

    private val isYatoon get() = YATOON_HOST.matches(baseUrl.toHttpUrl().host)

    override suspend fun getPopularManga(page: Int): MangasPage = if (isYatoon) yatoon.getPopularManga(page) else getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (isYatoon) return yatoon.getSearchMangaList(page, query, filters)
        if (query.isNotBlank()) {
            val results = searchEntries(page, query)
            return MangasPage(results.flatten().map { it.toSManga(baseUrl) }, results.any { it.size == PAGE_SIZE })
        }
        val day = filters.firstInstanceOrNull<DayFilter>()?.selected
        val params = listOf(
            "wt_fin" to (filters.firstInstanceOrNull<StatusFilter>()?.selected ?: ONGOING),
            if (day != null) "w" to day else "cate" to (filters.firstInstanceOrNull<GenreFilter>()?.selected ?: ALL_GENRES),
        )
        val entries = getEntries(page, params)
        return MangasPage(entries.map { it.toSManga(baseUrl) }, entries.size == PAGE_SIZE)
    }

    // The site's search only looks through ongoing series unless told to look through completed ones
    private suspend fun searchEntries(page: Int, text: String): List<List<WebtoonDto>> = coroutineScope {
        listOf(ONGOING, COMPLETED).map { status ->
            async { getEntries(page, listOf(searchParam(text), "wt_fin" to status)) }
        }.awaitAll()
    }

    // The site's search fails on apostrophes, and "_" matches any one character there
    private fun searchParam(text: String) = "search" to text.trim().replace('\'', '_')

    private suspend fun getEntries(page: Int, params: List<Pair<String, String>>): List<WebtoonDto> {
        val url = "$baseUrl/more.php".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            addQueryParameter("c", ((page - 1) * PAGE_SIZE).toString())
        }.build()
        return client.post(url, Fetch.XHR, FormBody.Builder().build()).parseAs()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = when {
            YATOON_HOST.matches(url.host) -> yatoon.getSeriesId(url)
            FRONT_HOST.matches(url.host) && url.pathSegments.lastOrNull() == "list.php" -> url.queryParameter("i")?.takeIf { it.all(Char::isDigit) }
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = if (isYatoon) yatoon.getMangaUrl(manga) else "$baseUrl/list.php?i=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (isYatoon) return yatoon.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = { if (fetchDetails) parseDetails(manga.url, document) else manga },
            chapters = { parseChapters(document) },
        )
    }

    // Each front lays the series page out its own way
    private suspend fun parseDetails(id: String, document: Document): SManga {
        // A missing series still answers with the page template, without a title
        val title = document.selectFirst(".book[data-title]")?.attr("data-title")?.takeIf(String::isNotEmpty)!!
        // The series page shows neither the author nor the status, the list API does
        val entry = searchEntries(1, title).flatten().firstOrNull { it.id == id }
        return SManga.create().apply {
            url = id
            this.title = title
            thumbnail_url = document.selectFirst(".list_p_img img, .toon_sum_img img, .list_page_head img")?.absUrl("src")
            author = entry?.author
            description = MarkdownDescription.Builder()
                .info(entry?.company)
                .description(document.selectFirst(".list_p_detail, .td_txt, .list_page_head_content p"))
                .build()
            // 머니툰's series page has no genres
            genre = (document.selectFirst(".cate_type span:last-child, .toon_cate_btn")?.text() ?: entry?.genre)
                ?.split(GENRE_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.joinToString()
                ?.ifEmpty { null }
            status = entry?.status ?: SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".list_data li").map { item ->
        val link = item.selectFirst("a[href*=detail.php]")!!
        SChapter.create().apply {
            url = link.absUrl("href").toHttpUrl().queryParameter("wi")!!
            name = link.text()
            date_upload = parseDate(item.selectFirst(".l_d_d")?.text())
        }
    }

    private fun parseDate(text: String?): Long = when (text) {
        "오늘" -> daysAgo(0)
        "어제" -> daysAgo(1)
        else -> dateFormat.tryParseDate(text, KOREA_ZONE)
    }

    private fun daysAgo(days: Long): Long = LocalDate.now(KOREA_ZONE).minusDays(days).atStartOfDay(KOREA_ZONE).toInstant().toEpochMilli()

    override fun getChapterUrl(chapter: SChapter): String = if (isYatoon) yatoon.getChapterUrl(chapter) else "$baseUrl/detail.php?wi=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (isYatoon) return yatoon.getPageList(chapter)
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".toon_view img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val PAGE_SIZE = 30
        internal const val ONGOING = "연재중"
        internal const val COMPLETED = "완결"
        internal const val ALL_GENRES = "전체"
        internal val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        internal val GENRE_SEPARATOR = Regex("[,/]")

        // 나미툰, 무료툰 and 머니툰 domains, the current ones and the older numbered ones
        private val FRONT_HOST = Regex("""(?:www\.)?(?:namee|foc|mtoon)\d+\.asia""")

        // 야툰's domains, whose template is its own
        private val YATOON_HOST = Regex("""(?:www\.)?yatoon\d+\.asia""")
    }
}
