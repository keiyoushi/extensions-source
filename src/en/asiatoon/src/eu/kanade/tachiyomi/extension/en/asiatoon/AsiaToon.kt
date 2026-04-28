package eu.kanade.tachiyomi.extension.en.asiatoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AsiaToon : HttpSource() {
    override val name = "AsiaToon"
    override val lang = "en"
    override val baseUrl = "https://asiatoon.net"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
    private val blockedHeadings = setOf("Log in", "Sign up", "Description", "Details", "Genres", "Tags", "Episodes Details")
    private val genreNames = setOf(
        "All",
        "Vanilla",
        "Monster Girls",
        "School Life",
        "Horror Thriller",
        "Slice of Life",
        "Supernatural",
        "New",
        "Office",
        "Sexy",
        "MILF",
        "In-Law",
        "Harem",
        "Cheating",
        "College",
        "Isekai",
        "UNCENSORED",
        "GL",
        "sexy comics",
        "Sci-fi",
        "Sports",
        "School life",
        "Historical",
        "Action",
        "Thriller",
        "Horror",
        "Fantasy",
        "Comedy",
        "Drama",
        "BL",
        "Romance",
    )

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, "hc_vfs" to "Y"))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en/genres?page=$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/genres/New?page=$page", headers)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addEncodedPathSegments("en/search")
                addQueryParameter("keyword", query.trim())
            } else {
                val filter = filters.filterIsInstance<BrowseFilter>().first()
                addEncodedPathSegments(filter.selected)
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = browseFilters()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isTextSearch = !response.request.url.queryParameter("keyword").isNullOrBlank()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val nextPage = currentPage + 1

        val selector = if (isTextSearch) {
            "li.search-item-wrap"
        } else {
            "article.component-item"
        }

        val mangas = document.select(selector)
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url }

        val hasNextPage = !isTextSearch && document.select("a[href*='page=$nextPage']").isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst(".info__right h1, .info__right h2, h1, h2")?.text().orEmpty()

        thumbnail_url = document.selectFirst(".info__left .thumb-wrapper img, .thumb-wrapper a.thumb.js-thumbnail img, a.thumb.js-thumbnail img")
            ?.imgAttr()
            ?.takeUnless { it.startsWith("data:", ignoreCase = true) }

        genre = document.select(".info__right a[href*='/en/genres/'], a[href*='/en/genres/']")
            .map { it.text() }
            .filter { it in genreNames }
            .distinct()
            .joinToString()
            .ifBlank { null }

        description = extractSectionText(document, "Description")
            ?: extractSectionText(document, "Details")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a[href*='/episode-']")
            .mapNotNull { element ->
                val url = element.absUrl("href")
                if (url.isBlank()) return@mapNotNull null

                val text = element.text().replace(WHITESPACE_REGEX, " ")
                val dateText = MONTH_DATE_REGEX.find(text)?.value
                val name = text.substringBefore(dateText ?: "").trim().ifBlank { text }

                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    this.name = name
                    date_upload = dateFormat.tryParse(dateText)
                }
            }
            .distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(
            "article.viewer__body img.content__img[data-index], " +
                "article.js-episode-article img.content__img[data-index], " +
                ".viewer__body img.content__img[data-index]",
        )
        .sortedBy { it.attr("data-index").toIntOrNull() ?: Int.MAX_VALUE }
        .mapIndexed { index, img -> Page(index, imageUrl = img.imgAttr()) }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun mangaFromElement(element: Element): SManga? {
        val anchor = element.selectFirst("a.thumb.js-thumbnail, a.thumb, a[href$='.html']") ?: return null
        val title = sequenceOf(
            anchor.attr("title"),
            element.selectFirst("[title]")?.attr("title"),
            element.selectFirst("p.line-clamp-3, p.webtoon-title")?.text(),
            element.selectFirst(".title")?.text(),
            element.selectFirst("img[alt]:not([alt=icon]):not([alt=img-thumb]):not([alt=wuf])")?.attr("alt"),
        )
            .filterNotNull()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        if (title.isBlank()) return null

        return SManga.create().apply {
            setUrlWithoutDomain(anchor.absUrl("href"))
            this.title = title
            thumbnail_url = anchor.selectFirst("img")?.imgAttr()
                ?: element.selectFirst("img")?.imgAttr()
        }
    }

    private fun extractSectionText(document: Document, heading: String): String? {
        val headingElement = document.select("*")
            .firstOrNull { it.ownText().trim().equals(heading, ignoreCase = true) }
            ?: return null

        var sibling = headingElement.nextElementSibling()
        while (sibling != null) {
            val text = sibling.text()
            if (text.isNotBlank() && text !in blockedHeadings) {
                return text
            }
            sibling = sibling.nextElementSibling()
        }

        return null
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-original") -> absUrl("data-original")
        else -> absUrl("src")
    }

    private fun SimpleDateFormat.tryParse(date: String?): Long = try {
        if (date == null) return 0L
        parse(date)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private companion object {
        val MONTH_DATE_REGEX = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{2},\\s+\\d{4}")
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
