package eu.kanade.tachiyomi.extension.vi.khomanhwa

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KhoManhwa : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/popular?page=$page"))

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/latest?page=$page"))

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.series-card").map { el ->
            SManga.create().apply {
                title = el.selectFirst("strong")!!.text()
                setUrlWithoutDomain(el.absUrl("href"))
                thumbnail_url = el.selectFirst("img")?.let {
                    it.absUrl("src").ifEmpty { it.absUrl("data-src") }
                }
            }
        }

        val hasNextPage = document.select("nav.pagination a").any { it.text() == "Next" }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val categoryPath = categoryFilter?.getCategoryPath()

        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("q", query)
            }.build()
            return parseMangaList(client.get(url))
        }

        if (categoryPath != null) {
            return parseMangaList(client.get("$baseUrl/$categoryPath?page=$page"))
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaList(client.get(url))
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.firstOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/$slug" }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl${manga.url}")
        val document = response.asJsoup()

        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        title = document.selectFirst(".series-main h1")!!.text()
        setUrlWithoutDomain(manga.url)
        thumbnail_url = document.selectFirst(".cover-card img")?.absUrl("src")
        description = document.selectFirst(".summary-inline p")?.text()
        author = document.selectFirst("a[href*=\"author=\"] span")?.text()
        artist = document.selectFirst("a[href*=\"artist=\"] span")?.text()
        status = parseStatus(document.selectFirst(".status-badge")?.text())
    }

    private fun parseStatus(text: String?): Int = when (text?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-row").map { el ->
        SChapter.create().apply {
            name = el.selectFirst(".chapter-name strong")!!.text()
            date_upload = chapterDateFormat.tryParseDate(
                el.selectFirst(".chapter-age")?.text(),
                chapterDateZone,
            )
            chapter_number = el.attr("data-number").toFloatOrNull() ?: 0f
            setUrlWithoutDomain(el.selectFirst("a.chapter-main")!!.absUrl("href"))
        }
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl${chapter.url}", ensureSuccess = false)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            if (code == 403) {
                throw Exception("Đăng nhập Webview bằng tài khoản phù hợp để xem chương này")
            }
            throw HttpException(code)
        }
        val document = response.asJsoup()

        return document.select("#chapter_boxImages img.chapter-page")
            .mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("src"))
            }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search").asJsoup()
        val genres = document.select("select[name=\"genre\"] option")
            .filterNot { it.attr("value").isEmpty() }
            .map { FilterOption(it.text(), it.attr("value")) }

        return FilterData(genres = genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.let { it.parseAs<FilterData>() } ?: return FilterList()
        return FilterList(
            CategoryFilter(),
            Filter.Header("Genre, Status and Sort are ignored when Category is not 'All'"),
            GenreFilter(filterData.genres),
            StatusFilter(STATUS_OPTIONS),
            SortFilter(SORT_OPTIONS),
        )
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return document.select(".similar-series a.series-card").map { el ->
            SManga.create().apply {
                title = el.selectFirst("strong")!!.text()
                setUrlWithoutDomain(el.absUrl("href"))
                thumbnail_url = el.selectFirst("img")?.absUrl("src")
            }
        }
    }

    private val chapterDateFormat = DateTimeFormatter.ofPattern("MMM dd, uuuu", Locale.ENGLISH)
    private val chapterDateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
