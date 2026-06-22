package eu.kanade.tachiyomi.extension.vi.vinahentai

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class VinaHentai :
    HttpSource(),
    ConfigurableSource {

    override val name = "VinaHentai"
    override val lang = "vi"

    private val defaultBaseUrl = "https://vinahentai.shop"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach?page=$page&sort=views", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach?page=$page&sort=updatedAt", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }

        var genreSlug: String? = null
        var sort = "updatedAt"
        var status = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genreSlug = filter.selected
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                else -> {}
            }
        }

        return if (genreSlug != null) {
            val url = "$baseUrl/genres/$genreSlug".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", sort)
                .apply { if (status.isNotEmpty()) addQueryParameter("status", status) }
                .build()
            GET(url, headers)
        } else {
            val url = "$baseUrl/danh-sach".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", sort)
                .apply { if (status.isNotEmpty()) addQueryParameter("status", status) }
                .build()
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val url = response.request.url.toString()

        if (url.contains("/search?")) {
            return parseSearchPage(document)
        }

        return parseMangaListPage(document)
    }

    // ============================== Filters ==============================

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var genreList: List<Pair<String, String>> = emptyList()
    private var fetchGenresAttempts: Int = 0

    private fun fetchGenres() {
        if (genreList.isEmpty() && fetchGenresAttempts < 3) {
            scope.launch {
                try {
                    client.newCall(GET("$baseUrl/danh-sach", headers)).await()
                        .use { response ->
                            parseGenresFromHtml(response)
                                .takeIf { it.isNotEmpty() }
                                ?.let { genreList = it }
                        }
                } catch (_: Exception) {
                } finally {
                    fetchGenresAttempts++
                }
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getFilters(genreList)
    }

    // ============================== Parsing ==============================

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaList = document.select("a[href^=/truyen-hentai/][data-variant]")
            .map { element -> mangaFromGridElement(element) }

        val hasNextPage = mangaList.size >= MANGA_PER_PAGE

        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseSearchPage(document: Document): MangasPage {
        val mangaList = document.select("a[href^=/truyen-hentai/]")
            .filter { it.selectFirst("h2") != null }
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("h2")!!.text()
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }

        val currentPage = PAGE_REGEX
            .find(document.location())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val hasNextPage = document.select("a[href*=page=]")
            .any { element ->
                PAGE_REGEX
                    .find(element.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                    ?.let { it > currentPage } == true
            }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromGridElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val titleDiv = element.selectFirst("div.truncate.font-semibold[title]")
        title = titleDiv?.attr("title") ?: titleDiv?.text() ?: ""
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()

            author = document.select("a[href^=/authors/]")
                .map { it.text() }
                .filter { !it.startsWith("+") }
                .joinToString()
                .ifEmpty { null }

            genre = document.select("a[href^=/genres/]")
                .map { it.text() }
                .filter { !it.startsWith("+") }
                .joinToString()
                .ifEmpty { null }

            thumbnail_url = document.selectFirst("img[alt*=Bìa]")?.absUrl("src")
                ?: document.selectFirst("img[src*=story-images]")?.absUrl("src")

            description = document.selectFirst("#manga-description-section .text-txt-secondary")
                ?.text()

            status = document.body().text().let { bodyText ->
                when {
                    bodyText.contains("Đang tiến hành") -> SManga.ONGOING
                    bodyText.contains("Đã hoàn thành") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a.block[href^=/truyen-hentai/]")
            .filter { element ->
                val href = element.attr("href")
                href.count { it == '/' } > 2
            }
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("href"))
                    name = element.selectFirst("span")?.text() ?: element.text()
                    date_upload = parseRelativeDate(element.selectFirst("time")?.text())
                }
            }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================

    private val imageUrlRegex by lazy {
        val baseDomain = baseUrl.removePrefix("https://").removePrefix("http://")
        Regex("""https://cdn\.${Regex.escape(baseDomain)}/manga-images/[^"'\s\\]+""")
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()

        val imageUrls = imageUrlRegex.findAll(body)
            .map { it.value }
            .distinct()
            .toList()

        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val MANGA_PER_PAGE = 24

        private val NUMBER_REGEX = Regex("""\d+""")
        private val PAGE_REGEX = Regex("""page=(\d+)""")

        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
