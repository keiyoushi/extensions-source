package eu.kanade.tachiyomi.extension.ru.nudemoon

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class Nudemoon : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val cookieManager by lazy { CookieManager.getInstance() }

    override fun OkHttpClient.Builder.configureClient() = addCookie { listOf("NMfYa" to "1", "nm_mobile" to "1", "Domain" to domain) }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeRequest("views", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeRequest("date", page)

    // ============================== Common function ===============================
    private suspend fun makeRequest(sort: String, page: Int): MangasPage {
        val response = client.get("$baseUrl/all_manga?$sort&rowstart=${30 * (page - 1)}").asJsoup()

        return searchMangaParse(response)
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            buildString {
                append(baseUrl)
                append("/search?stext=")
                append(URLEncoder.encode(query, "CP1251"))
                append("&rowstart=")
                append(30 * (page - 1))
            }
        } else {
            val currentFilters = if (filters.isEmpty()) getFilterList() else filters
            val genreList = currentFilters.firstInstanceOrNull<GenreList>()
            val orderFilter = currentFilters.firstInstanceOrNull<OrderBy>()

            val genres = genreList?.state
                ?.filter { it.state }
                ?.joinToString("+") { it.id }
                .orEmpty()

            val orderIndex = orderFilter?.state?.index ?: 1
            val path = if (genres.isNotEmpty()) {
                "tags/$genres${TAG_ORDERS[orderIndex]}"
            } else {
                ALL_ORDERS[orderIndex]
            }
            "$baseUrl/$path&rowstart=${30 * (page - 1)}"
        }

        val response = client.get(url, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404 && query.isNotBlank() && cookieManager.getCookie(baseUrl)?.contains("fusion_user") != true) {
                    throw Exception("Поиск доступен только для авторизированных пользователей")
                } else {
                    throw HttpException(response.code)
                }
            }
            response.asJsoup()
        }
        return searchMangaParse(response)
    }

    private val mangaSelector = "table.news_pic2"
    private val nextPageSelector = "a.small:contains(>)"

    private fun parseMangaElement(element: Element): SManga? = SManga.create().apply {
        element.selectFirst("a:has(h2)")?.let {
            title = it.text().substringBefore(" / ").substringBefore(" №")
            setUrlWithoutDomain(it.absUrl("href"))
        } ?: return null
        thumbnail_url = element.selectFirst("a img")?.attr("abs:src")
    }

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select(mangaSelector).mapNotNull(::parseMangaElement)
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val firstSegment = url.pathSegments.firstOrNull() ?: return null
        if (url.host == domain && firstSegment.endsWith(".html")) {
            val tmpManga = SManga.create().apply {
                this.url = "/$firstSegment"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ======================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = manga.url
        val response = client.get(getMangaUrl(manga)).asJsoup()

        val newManga = mangaDetailsParse(response, mangaUrl)
        val newChapters = if (fetchChapters) {
            chapterListParse(response, mangaUrl)
        } else {
            chapters
        }

        return SMangaUpdate(newManga, newChapters)
    }

    private fun mangaDetailsParse(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(mangaSelector)
        url = mangaUrl
        title = document.selectFirst("h1")?.text()?.substringBefore(" / ")?.substringBefore(" №")!!
        author = infoElement?.selectFirst("a[href*=mangaka]")?.text()
        genre = infoElement?.select("div.tag-links a")?.joinToString { it.text() }
        description = document.selectFirst(".description")?.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("abs:content")
    }

    // ============================== Chapters ======================================
    private suspend fun chapterListParse(document: Document, mangaUrl: String): List<SChapter> {
        val allChaptersButton = document.selectFirst("td.button a:contains(Все главы)")
            ?: return listOf(chapterFromSinglePage(document, mangaUrl))

        val chapters = mutableListOf<SChapter>()
        var pageListLink = allChaptersButton.absUrl("href")

        while (true) {
            val data = client.get(pageListLink).asJsoup()
            val pageChapters = data.select(mangaSelector).mapNotNull { element ->
                SChapter.create().apply {
                    val nameAndUrl = element.selectFirst("tr[valign=top] a:has(h2)")
                    name = nameAndUrl?.selectFirst("h2")?.text() ?: return@mapNotNull null
                    setUrlWithoutDomain(nameAndUrl.absUrl("href"))
                    val informBlock = element.selectFirst("tr[valign=top] td[align=left]")
                    scanlator = informBlock?.selectFirst("a[href*=perevod]")?.text()

                    date_upload = dateFormat.tryParseDate(
                        informBlock?.selectFirst("""span.small2:matches((0[1-9]|[12][0-9]|3[01])*(19|20)\d{2})""")?.text(),
                    )

                    chapter_number = name.substringAfter("№").substringBefore(" ").replace("-", ".").toFloatOrNull() ?: -1f
                }
            }
            chapters.addAll(pageChapters)

            val nextPageElement = data.selectFirst(nextPageSelector) ?: break
            pageListLink = nextPageElement.absUrl("href")
        }

        if (chapters.isEmpty()) {
            chapters.add(chapterFromSinglePage(document, mangaUrl))
        }

        return chapters
    }

    private fun chapterFromSinglePage(document: Document, mangaUrl: String): SChapter = SChapter.create().apply {
        val chapterName = document.selectFirst("table td.bg_style1 h1")?.text()?.substringAfter("/")?.trim()
        name = "Сингл $chapterName"
        url = mangaUrl
        scanlator = document.selectFirst("table.news_pic2 a[href*=perevod]")?.text()
        date_upload = dateFormat.tryParseDate(
            document.selectFirst("""td:has(img[src*=time]) span.small2:matches((0[1-9]|[12][0-9]|3[01])*(19|20)\d{2})""")?.text(),
        )
        chapter_number = 0F
    }

    // ============================== Pages ======================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = document.select("""img[title~=.+][loading="lazy"]""").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:data-src"))
        }
        if (pages.isEmpty() && cookieManager.getCookie(baseUrl)?.contains("fusion_user") != true) {
            throw Exception("Страницы не найдены. Возможно необходима авторизация в WebView")
        }
        return pages
    }

    // ============================== Filters ======================================
    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================== Utilities ======================================
    companion object {
        private val TAG_ORDERS = arrayOf("&date", "&views", "&like")
        private val ALL_ORDERS = arrayOf("all_manga?date", "all_manga?views", "all_manga?like")
        private val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendText(
                ChronoField.MONTH_OF_YEAR,
                mapOf(
                    1L to "январь", 1L to "января",
                    2L to "февраль", 2L to "февраля",
                    3L to "март", 3L to "марта",
                    4L to "апрель", 4L to "апреля",
                    5L to "май", 5L to "мая",
                    6L to "июнь", 6L to "июня",
                    7L to "июль", 7L to "июля",
                    8L to "август", 8L to "августа",
                    9L to "сентябрь", 9L to "сентября",
                    10L to "октябрь", 10L to "октября",
                    11L to "ноябрь", 11L to "ноября",
                    12L to "декабрь", 12L to "декабря",
                ),
            )
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter(Locale.forLanguageTag("ru"))
    }
}
