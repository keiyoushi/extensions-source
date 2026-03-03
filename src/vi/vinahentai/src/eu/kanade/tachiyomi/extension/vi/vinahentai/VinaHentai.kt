package eu.kanade.tachiyomi.extension.vi.vinahentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class VinaHentai : HttpSource() {
    override val name = "VinaHentai"
    override val lang = "vi"
    override val baseUrl = "https://vinahentai.site"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
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
            return GET("$baseUrl/search?page=$page&q=$query", headers)
        }

        var genreSlug: String? = null
        var sort = "updatedAt"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genreSlug = filter.selected
                is SortFilter -> sort = filter.toUriPart()
                else -> {}
            }
        }

        return if (genreSlug != null) {
            GET("$baseUrl/genres/$genreSlug?page=$page&sort=$sort", headers)
        } else {
            GET("$baseUrl/danh-sach?page=$page&sort=$sort", headers)
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

    override fun getFilterList(): FilterList = getFilters()

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaList = document.select("a.group[href^=/truyen-hentai/], a.group[href^=/login?redirect=]")
            .filter { it.selectFirst("h3") != null }
            .map { element -> mangaFromGridElement(element) }

        val hasNextPage = document.selectFirst("button[title=Tới trang cuối]:not([disabled])") != null

        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseSearchPage(document: Document): MangasPage {
        val mangaList = document.select("a[href^=/truyen-hentai/], a[href^=/login?redirect=]")
            .filter { it.selectFirst("h2") != null }
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(resolveMangaUrl(element.attr("href")))
                    title = element.selectFirst("h2")!!.text()
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }

        val currentPage = """page=(\d+)""".toRegex()
            .find(document.location())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val hasNextPage = document.select("a[href*=page=]")
            .any { element ->
                """page=(\d+)""".toRegex()
                    .find(element.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                    ?.let { it > currentPage } == true
            }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromGridElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(resolveMangaUrl(element.attr("href")))
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun resolveMangaUrl(href: String): String {
        if (href.startsWith("/login")) {
            val redirect = href.substringAfter("redirect=", "")
            if (redirect.isNotEmpty()) {
                return java.net.URLDecoder.decode(redirect, "UTF-8")
            }
        }
        return href
    }

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()

            author = document.select("a[href^=/authors/]")
                .map { it.text().trim() }
                .filter { !it.startsWith("+") }
                .joinToString()
                .ifEmpty { null }

            genre = document.select("a[href^=/genres/]")
                .map { it.text().trim() }
                .filter { !it.startsWith("+") }
                .joinToString()
                .ifEmpty { null }

            thumbnail_url = document.selectFirst("img[alt*=Bìa]")?.absUrl("src")
                ?: document.selectFirst("img[src*=story-images]")?.absUrl("src")

            description = document.selectFirst("#manga-description-section .text-txt-secondary")
                ?.text()?.trim()

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
                    name = element.selectFirst("span")?.text()?.trim() ?: element.text().trim()
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

    companion object {
        private val NUMBER_REGEX = Regex("""\d+""")
    }
}
