package eu.kanade.tachiyomi.extension.ru.nudemoon

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class Nudemoon : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val isUserAuthenticated: Boolean
        get() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any { it.name == "fusion_user" }

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
        val offset = (30 * (page - 1)).toString()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("stext", query)
                addQueryParameter("rowstart", offset)
            } else {
                val genres = filters.firstInstanceOrNull<GenreList>()
                    ?.state
                    ?.filter { it.state }
                    ?.joinToString("+") { it.id }
                    .orEmpty()
                val order = filters.firstInstanceOrNull<OrderBy>()?.selected
                if (genres.isNotEmpty()) {
                    addPathSegment("tags")
                    addPathSegment("$genres&$order&rowstart=$offset")
                } else {
                    addPathSegment("all_manga")
                    encodedQuery("$order&rowstart=$offset")
                }
            }
        }.build()

        val response = client.get(url, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404 && query.isNotBlank() && !isUserAuthenticated) {
                    throw Exception("Поиск доступен только для авторизированных пользователей")
                } else {
                    throw HttpException(response.code)
                }
            }
            response.asJsoup()
        }
        return searchMangaParse(response)
    }

    private fun parseMangaElement(element: Element): SManga? = SManga.create().apply {
        element.selectFirst("a:has(h2)")?.let {
            title = it.text().substringBefore(" / ").substringBefore(" №")
            setUrlWithoutDomain(it.absUrl("href"))
        } ?: return null
        thumbnail_url = element.selectFirst("a img")?.absUrl("src")
    }

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select(MANGA_SELECTOR).mapNotNull(::parseMangaElement)
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
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
        val infoElement = document.selectFirst(MANGA_SELECTOR)
        url = mangaUrl
        title = document.selectFirst("h1")?.text()?.substringBefore(" / ")?.substringBefore(" №")!!
        author = infoElement?.selectFirst("a[href*=mangaka]")?.text()
        genre = infoElement?.select("div.tag-links a")?.joinToString { it.text() }
        description = document.selectFirst(".description")?.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
    }

    // ============================== Chapters ======================================
    private suspend fun chapterListParse(document: Document, mangaUrl: String): List<SChapter> {
        val allChaptersButton = document.selectFirst("td.button a:contains(Все главы)")
            ?: return listOf(chapterFromSinglePage(document, mangaUrl))

        val chapters = mutableListOf<SChapter>()
        var pageListLink = allChaptersButton.absUrl("href")

        while (true) {
            val data = client.get(pageListLink).asJsoup()
            val pageChapters = data.select(MANGA_SELECTOR).mapNotNull { element ->
                SChapter.create().apply {
                    val nameAndUrl = element.selectFirst("tr[valign=top] a:has(h2)")
                    name = nameAndUrl?.selectFirst("h2")?.text() ?: return@mapNotNull null
                    setUrlWithoutDomain(nameAndUrl.absUrl("href"))
                    val informBlock = element.selectFirst("tr[valign=top] td[align=left]")
                    scanlator = informBlock?.selectFirst("a[href*=perevod]")?.text()

                    date_upload = informBlock?.selectFirst("span.small2")?.text()?.let { text ->
                        dateFormat.tryParseDate(DATE_REGEX.find(text)?.value)
                    } ?: 0L

                    chapter_number = name.substringAfter("№").substringBefore(" ").replace("-", ".").toFloatOrNull() ?: -1f
                }
            }
            chapters.addAll(pageChapters)

            val nextPageElement = data.selectFirst(NEXT_PAGE_SELECTOR) ?: break
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
        date_upload = document.selectFirst("td:has(img[src*=time]) span.small2")?.text()?.let { text ->
            dateFormat.tryParseDate(DATE_REGEX.find(text)?.value)
        } ?: 0L
        chapter_number = 0F
    }

    // ============================== Pages ======================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = document.select("""img[title~=.+][loading="lazy"]""").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
        if (pages.isEmpty() && !isUserAuthenticated) {
            throw Exception("Страницы не найдены. Возможно необходима авторизация в WebView")
        }
        return pages
    }

    // ============================== Filters ======================================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = client.get("$baseUrl/tags/").asJsoup()
        return data.select("input[name*=tag]").map { it.attr("value").trim() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(OrderBy())
        data?.parseAs<List<String>>()?.let { d ->
            if (d.isNotEmpty()) filters.add(GenreList(d.map { Genre(it) }))
        }
        return FilterList(filters)
    }

    // ============================== Utilities ======================================
    companion object {
        private const val MANGA_SELECTOR = "table.news_pic2"
        private const val NEXT_PAGE_SELECTOR = "a.small:contains(>)"
        private val DATE_REGEX = """\b\d{1,2}\s+[А-Яа-яЁё]+\s+(?:19|20)\d{2}\b""".toRegex()
        private val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("[d MMMM yyyy][dd MMMM yyyy]")
            .optionalStart()
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendText(
                ChronoField.MONTH_OF_YEAR,
                mapOf(
                    1L to "январь",
                    2L to "февраль",
                    3L to "март",
                    4L to "апрель",
                    5L to "май",
                    6L to "июнь",
                    7L to "июль",
                    8L to "август",
                    9L to "сентябрь",
                    10L to "октябрь",
                    11L to "ноябрь",
                    12L to "декабрь",
                ),
            )
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .optionalEnd()
            .toFormatter(Locale.forLanguageTag("ru"))
    }
}
