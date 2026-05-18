package eu.kanade.tachiyomi.extension.ru.mangashi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaShi : HttpSource() {

    override val name = "Manga-shi"

    override val baseUrl = "https://manga-shi.org"

    override val lang = "ru"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET(catalogUrl(page, SORT_POPULAR), headers)

    override fun popularMangaParse(response: Response): MangasPage = catalogParse(response)

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET(catalogUrl(page, SORT_LATEST), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = catalogParse(response)

    // ========================= Search =========================

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull()
        if (url != null) {
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported URL")
            }
            val slug = url.pathSegments.getOrNull(1) ?: throw Exception("Invalid manga URL")
            return client.newCall(GET("$baseUrl/manga/$slug/", headers))
                .asObservableSuccess()
                .map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        setUrlWithoutDomain("$baseUrl/manga/$slug/")
                    }
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addPathSegment("")
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query.trim())
        } else {
            filters.filterIsInstance<UriFilter>().forEach { it.addToUri(url) }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = catalogParse(response)

    // ========================= Filters =========================

    override fun getFilterList(): FilterList = getMangaShiFilters()

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()

            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
                ?.let { if (it.startsWith("/")) "$baseUrl$it" else it }

            val authorLinks = document.select("a[href*=\"?author=\"]")
            author = authorLinks.firstOrNull()?.text()?.takeIf(String::isNotEmpty)

            val badges = document.select("span.tracking-widest").map { it.text().trim() }
            val statusText = badges.firstOrNull { txt ->
                val lower = txt.lowercase()
                lower.contains("онгоинг") || lower.contains("выпускается") || lower.contains("заверш") || lower.contains("заморож") || lower.contains("приостановл") || lower.contains("заброш")
            }

            status = parseStatus(statusText)

            val typeText = badges.firstOrNull { txt ->
                val lower = txt.lowercase()
                lower.contains("манга") || lower.contains("манхва") || lower.contains("маньхуа") || lower.contains("комикс")
            }

            val genreLinks = document.select("a[href*=manga-genre]").eachText()
            val tagLinks = document.select("a[href*=\"?tag=\"]").eachText()
            genre = (listOfNotNull(typeText) + genreLinks + tagLinks)
                .filter(String::isNotEmpty)
                .distinct()
                .joinToString()

            description = document.selectFirst("p.leading-relaxed, .leading-relaxed")
                ?.wholeText()?.trim()
        }
    }

    private fun parseStatus(raw: String?): Int {
        val txt = raw?.lowercase() ?: return SManga.UNKNOWN
        return when {
            txt.contains("онгоинг") || txt.contains("выпускается") || txt.contains("продолжается") -> SManga.ONGOING
            txt.contains("завершен") || txt.contains("завершён") || txt.contains("завершена") -> SManga.COMPLETED
            txt.contains("заморож") || txt.contains("приостановл") || txt.contains("хиатус") -> SManga.ON_HIATUS
            txt.contains("заброш") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ========================= Chapters =========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("#chapters-list > a[href*=\"/glava\"]")
            .mapNotNull(::parseChapter)
            .toMutableList()

        var nextUrl = document.selectFirst("#chapters-load-more[hx-get], #chapters-load-more [hx-get]")
            ?.attr("hx-get")

        while (nextUrl != null) {
            client.newCall(GET("$baseUrl$nextUrl", headers)).execute().use { nextResponse ->
                val fragment = Jsoup.parseBodyFragment(nextResponse.body.string(), baseUrl)
                chapters.addAll(fragment.select("a[href*=\"/glava\"]").mapNotNull(::parseChapter))
                nextUrl = fragment.selectFirst("#chapters-load-more[hx-get], #chapters-load-more [hx-get]")
                    ?.attr("hx-get")
            }
        }

        return chapters
    }

    private fun parseChapter(link: Element): SChapter? {
        val href = link.attr("href")
        if (!CHAPTER_URL_REGEX.containsMatchIn(href)) return null
        if (link.selectFirst("span.chapter-title") == null) return null
        return SChapter.create().apply {
            setUrlWithoutDomain(
                if (href.startsWith("/")) "$baseUrl$href" else link.absUrl("href"),
            )
            name = link.selectFirst("span.chapter-title span")?.text()?.trim()
                ?: link.ownText().trim().ifEmpty { href.substringAfterLast("/manga/") }
            val dateText = link.select("span span").eachText()
                .lastOrNull { ABSOLUTE_DATE_REGEX.containsMatchIn(it) || RELATIVE_DATE_REGEX.containsMatchIn(it) }
            date_upload = parseChapterDate(dateText?.trim())
            chapter_number = CHAPTER_NUMBER_REGEX.find(href)
                ?.groupValues?.get(1)
                ?.replace(",", ".")
                ?.toFloatOrNull() ?: -1f
        }
    }

    private fun parseChapterDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val trimmed = dateStr.trim()
        if (ABSOLUTE_DATE_REGEX.matches(trimmed)) {
            return dateFormat.tryParse(trimmed)
        }
        val lower = trimmed.lowercase()
        val amount = RELATIVE_NUMBER_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (amount != null) {
            val cal = Calendar.getInstance()
            when {
                "сек" in lower -> cal.add(Calendar.SECOND, -amount)
                "мин" in lower -> cal.add(Calendar.MINUTE, -amount)
                "час" in lower -> cal.add(Calendar.HOUR_OF_DAY, -amount)
                "дн" in lower || "день" in lower || "дня" in lower -> cal.add(Calendar.DAY_OF_YEAR, -amount)
                "недел" in lower -> cal.add(Calendar.WEEK_OF_YEAR, -amount)
                "месяц" in lower -> cal.add(Calendar.MONTH, -amount)
                "год" in lower || "лет" in lower -> cal.add(Calendar.YEAR, -amount)
                else -> return 0L
            }
            return cal.timeInMillis
        }
        return 0L
    }

    // ========================= Pages =========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.reader-image").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun catalogUrl(page: Int, sort: String): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("catalog")
        .addPathSegment("")
        .addQueryParameter("sort", sort)
        .addQueryParameter("page", page.toString())
        .build()
        .toString()

    private fun catalogParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val grid = document.selectFirst("#manga-grid") ?: return MangasPage(emptyList(), false)
        val mangas = grid.select("> a[href*=\"/manga/\"]")
            .map(::cardToSManga)
            .distinctBy { it.url }
        val hasNextPage = mangas.size >= MIN_PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    private fun cardToSManga(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h3, h4, .title")?.text()
            ?: element.text().lines().firstOrNull { it.isNotBlank() }!!
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    companion object {
        private const val SORT_POPULAR = "popular"
        private const val SORT_LATEST = "updated"
        private const val MIN_PAGE_SIZE = 20

        private val CHAPTER_URL_REGEX = Regex("""/glava[-_]""")
        private val CHAPTER_NUMBER_REGEX = Regex("""/glava[-_]([\d,]+)""")
        private val ABSOLUTE_DATE_REGEX = Regex("""\d{2}\.\d{2}\.\d{4}""")
        private val RELATIVE_DATE_REGEX = Regex("""\d+\s*(сек|мин|час|дн|день|дня|недел|месяц|год|лет)""")
        private val RELATIVE_NUMBER_REGEX = Regex("""(\d+)""")

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
    }
}
