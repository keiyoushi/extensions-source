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
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
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

    // Old domains redirect to the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url.toString() },
    )

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val params = if (query.isNotBlank()) {
            listOf(searchParam(query))
        } else {
            val day = filters.firstInstanceOrNull<DayFilter>()?.selected
            listOf(
                "wt_fin" to (filters.firstInstanceOrNull<StatusFilter>()?.selected ?: ONGOING),
                if (day != null) "w" to day else "cate" to (filters.firstInstanceOrNull<GenreFilter>()?.selected ?: ALL_GENRES),
            )
        }
        val entries = getEntries(page, params)
        return MangasPage(entries.map { it.toSManga(baseUrl) }, entries.size == PAGE_SIZE)
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
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.lastOrNull() != "list.php") return null
        val id = url.queryParameter("i")?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/list.php?i=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = { if (fetchDetails) parseDetails(manga.url, document) else manga },
            chapters = { parseChapters(document) },
        )
    }

    private suspend fun parseDetails(id: String, document: Document): SManga {
        // A missing series still answers with the page template, without a title
        val title = document.selectFirst("h1.list_p_title")?.text()?.takeIf(String::isNotEmpty)!!
        // The series page shows neither the author nor the status, the list API does
        val entry = getEntries(1, listOf(searchParam(title))).firstOrNull { it.id == id }
        return SManga.create().apply {
            url = id
            this.title = title
            thumbnail_url = document.selectFirst(".list_p_img img")?.absUrl("src")
            author = entry?.author
            description = MarkdownDescription.Builder()
                .info(entry?.company)
                .description(document.selectFirst(".list_p_detail"))
                .build()
            genre = document.select(".cate_type span").last()?.text()
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
        "오늘" -> LocalDate.now(KOREA_ZONE).atStartOfDay(KOREA_ZONE).toInstant().toEpochMilli()
        else -> dateFormat.tryParseDate(text, KOREA_ZONE)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/detail.php?wi=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".toon_view .list_p_img img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val PAGE_SIZE = 30
        private const val ONGOING = "연재중"
        private const val ALL_GENRES = "전체"
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        private val GENRE_SEPARATOR = Regex("[,/]")
    }
}
