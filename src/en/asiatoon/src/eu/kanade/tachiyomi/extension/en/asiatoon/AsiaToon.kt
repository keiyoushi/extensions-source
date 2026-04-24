package eu.kanade.tachiyomi.extension.en.asiatoon

import eu.kanade.tachiyomi.multisrc.hotcomics.HotComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AsiaToon :
    HotComics(
        "AsiaToon",
        "en",
        "https://asiatoon.net",
    ) {
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
    private val blockedHeadings = setOf("Log in", "Sign up", "Description", "Details", "Genres", "Tags", "Episodes Details")
    private val blockedTitleTokens = setOf("Popular", "Newest series", "Family Safe", "Login", "More", "Home", "My Library", "Genres", "Daily", "New")
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

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en/genres?page=$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/genres/New?page=$page", headers)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        checkForChallenge(document)

        val mangas = document
            .select("a[href*='/en/'][href$='.html']:not([href*='/episode-'])")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url }

        if (mangas.isEmpty()) {
            throw Exception("AsiaToon returned an empty page. In Suwayomi this usually means a Cloudflare challenge; enable FlareSolverr or Byparr.")
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val nextPage = currentPage + 1
        val hasNextPage = document.select("a[href*='page=$nextPage']").isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        checkForChallenge(document)

        title = document.select("h1, h2")
            .map { it.text() }
            .firstOrNull { it.isNotBlank() && it !in blockedHeadings && !it.endsWith("at Asiatoon.net") }
            ?: ""

        thumbnail_url = document.selectFirst(".thumb-wrapper a.thumb.js-thumbnail img, a.thumb.js-thumbnail img")?.imgAttr()
            ?.takeUnless { it.startsWith("data:", ignoreCase = true) }
            ?: document.selectFirst("meta[property=og:image], meta[name=twitter:image]")?.absUrl("content")
                ?.takeUnless { it.startsWith("data:", ignoreCase = true) }
            ?: document.selectFirst("img[alt]:not([alt=icon]):not([alt=''])")?.imgAttr()
                ?.takeUnless { it.startsWith("data:", ignoreCase = true) }

        genre = document.select("a[href*='/en/genres/']")
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
        checkForChallenge(document)

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
                    date_upload = parseDate(dateText)
                }
            }
            .distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        checkForChallenge(document)

        val candidates = document.select(
            "article.viewer__body img.content__img[data-index], " +
                "article.js-episode-article img.content__img[data-index], " +
                ".viewer__body img.content__img[data-index]",
        ).ifEmpty {
            document.select(
                "article.viewer__body img.content__img, " +
                    "article.js-episode-article img.content__img, " +
                    ".viewer__body img.content__img",
            )
        }

        val extractedPages = candidates
            .mapNotNull { img ->
                val url = img.imgAttr()
                if (!isPageImage(url)) return@mapNotNull null

                val index = img.attr("data-index").toIntOrNull()
                index to url
            }
            .let { pages ->
                val dominantHost = pages
                    .mapNotNull { (_, url) -> urlHost(url) }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

                pages.filter { (_, url) ->
                    dominantHost == null || urlHost(url) == dominantHost
                }
            }
            .distinctBy { it.second }
            .sortedWith(compareBy<Pair<Int?, String>> { it.first ?: Int.MAX_VALUE }.thenBy { it.second })

        val pages = extractedPages
            .mapIndexed { index, (_, url) -> Page(index, imageUrl = url) }

        if (pages.isNotEmpty()) {
            return pages
        }

        val hasVipBlock = document.select("*").any {
            it.ownText().contains("VIP Access Required", ignoreCase = true) ||
                it.ownText().contains("Subscribe to unlock", ignoreCase = true) ||
                it.ownText().contains("Unlock this chapter", ignoreCase = true)
        }

        if (hasVipBlock) {
            throw Exception("This AsiaToon chapter requires VIP access.")
        }

        throw Exception("No readable pages found for this AsiaToon chapter.")
    }

    override val browseList = browseEntries

    private fun mangaFromElement(element: Element): SManga? {
        val url = element.absUrl("href").substringBefore("#")
        if (url.isBlank() || "/episode-" in url || !url.contains("/en/")) return null

        val title = sequenceOf(
            element.attr("title"),
            element.selectFirst("img[alt]:not([alt=icon]):not([alt=''])")?.attr("alt"),
            element.selectFirst("h1, h2, h3, h4, h5, h6")?.text(),
            cleanupTitle(element.text()),
        )
            .filterNotNull()
            .map(String::trim)
            .map(::cleanupTitle)
            .firstOrNull(::isValidTitle)
            ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(url)
            this.title = title
            thumbnail_url = element.selectFirst("img")?.imgAttr()
                ?: element.parent()?.selectFirst("img")?.imgAttr()
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

    private fun checkForChallenge(document: Document) {
        val text = document.text()
        if (
            document.title().contains("Just a moment", ignoreCase = true) ||
            text.contains("Enable JavaScript and cookies to continue", ignoreCase = true) ||
            text.contains("cf-mitigated", ignoreCase = true) ||
            document.selectFirst("script[src*='/cdn-cgi/challenge-platform'], #challenge-error-text, form#challenge-form") != null
        ) {
            throw Exception("AsiaToon is behind Cloudflare. In Suwayomi enable FlareSolverr or Byparr, or open the source in WebView on Mihon.")
        }
    }

    private fun isValidTitle(title: String): Boolean {
        if (title.isBlank()) return false
        if (title in blockedTitleTokens || title in genreNames) return false
        if (title.matches(NUMERIC_TITLE_REGEX)) return false
        return title.any { it.isLetter() }
    }

    private fun cleanupTitle(title: String): String = title
        .replace(TITLE_MARKER_REGEX, " ")
        .replace(TRAILING_COUNT_REGEX, "")
        .replace(MULTISPACE_REGEX, " ")
        .trim()

    private fun parseDate(date: String?) = dateFormat.tryParse(date)

    private fun isPageImage(url: String): Boolean {
        if (url.isBlank()) return false

        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false

        return listOf(
            "icon",
            "logo",
            "facebook",
            "twitter",
            "reddit",
            "email",
            "thumbsup",
            "search",
            "close",
            "drop",
            "check",
        ).none { lower.contains(it) }
    }

    private fun urlHost(url: String): String? = runCatching {
        java.net.URI(url).host?.lowercase()
    }.getOrNull()

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
        val TITLE_MARKER_REGEX = Regex("\\b(?:UP|NEW|18\\+)\\b")
        val TRAILING_COUNT_REGEX = Regex("\\s+\\d+(?:[.,]\\d+)?[KM]?$")
        val MULTISPACE_REGEX = Regex("\\s{2,}")
        val NUMERIC_TITLE_REGEX = Regex("^[0-9.]+[KM]?$")
    }
}
