package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

open class Niadd(
    private val nameSuffix: String,
    baseUrlParam: String,
    langParam: String,
) : ParsedHttpSource() {

    override val name = "Niadd"
    override val baseUrl = baseUrlParam
    override val lang = langParam
    override val supportsLatest = true

    companion object {
        private val ALL_IMGS_URL_REGEX = Regex("""all_imgs_url\s*:\s*\[([\s\S]*?)\]""")
        private val CLEAN_IMG_URL_REGEX = Regex("""["'\s]""")
        private val CHAPTER_NUMBER_REGEX = Regex("""Capítulo\s+(\d+(\.\d+)?)""")
    }

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder()
            // Hardcoded User-Agent required to bypass Niadd hotlink protection for cover images.
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/list/Hot-Manga.html", headers)

    override fun popularMangaSelector() = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            title = element.selectFirst("div.manga-name")!!.text()
            val rawUrl = element.selectFirst("a")!!.absUrl("href")
            setUrlWithoutDomain(rawUrl)
            element.selectFirst("div.manga-img img")?.attr("abs:src")?.also { thumbnail_url = it }
        }

    override fun popularMangaNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update.html", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.bookside-general, div.detail-general")

        title = document.selectFirst("h1, .book-headline-name")!!.text()
        author = infoElement.select(".detail-general-cell:contains(Autor) span, [itemprop=author] span").text()
            .replace("Autor (es):", "", ignoreCase = true)
        artist = infoElement.select(".detail-general-cell:contains(Artista) span").text()
            .replace("Artista:", "", ignoreCase = true)
        genre = document.select("[itemprop=genre]").eachText().joinToString()

        val yearKeywords = listOf(
            "Released:",
            "Lanzado:",
            "Rilasciato:",
            "Выпущенный:",
            "Liberado:",
            "Freigegeben:",
        )

        val yearRaw = infoElement.select(".detail-general-cell").firstOrNull { cell ->
            yearKeywords.any { cell.text().contains(it, ignoreCase = true) }
        }?.selectFirst("span")?.text().orEmpty()

        val yearClean = yearRaw
            .let { text ->
                yearKeywords.fold(text) { acc, keyword -> acc.replace(keyword, "", ignoreCase = true) }
            }

        val synopsisKeywords = listOf(
            "Synopsis",
            "Sinopsis",
            "Sinossi",
            "конспект",
            "Sinopse",
            "Zusammenfassung",
        )

        val synopsisText = run {
            val titles = document.select(".detail-cate-title")
            for (title in titles) {
                val titleText = title.text()
                if (synopsisKeywords.any { keyword -> titleText.contains(keyword, ignoreCase = true) }) {
                    val nextSection = title.nextElementSibling()
                    if (nextSection != null && nextSection.hasClass("detail-section")) {
                        if (!nextSection.select("a[itemprop=genre]").any()) {
                            return@run nextSection.text()
                        }
                    }
                }
            }
            ""
        }

        description = buildString {
            if (yearClean.isNotBlank()) append("Ano: $yearClean\n\n")
            if (synopsisText.isNotBlank()) append(synopsisText)
        }

        document.selectFirst("div.detail-img img, div.bookside-img img")?.attr("abs:src").also { thumbnail_url = it }
        status = SManga.ONGOING
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = baseUrl + manga.url.removeSuffix(".html") + "/chapters.html"
        return GET(chaptersUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterListContainer = document.selectFirst("ul.chapter-list")!!

        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "ul.chapter-list a.hover-underline"
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)

    private fun parseDate(dateString: String): Long {
        if (dateString.contains("atrás", ignoreCase = true) ||
            dateString.contains("ago", ignoreCase = true)
        ) {
            // TODO: Parse relative date
            return 0L
        }

        return dateFormat.tryParse(dateString)
    }

    // Pages
    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            val rawUrl = element.attr("abs:href")
            setUrlWithoutDomain(rawUrl)

            name = element.selectFirst("span.chapter-name, span.name")?.text()
                ?.takeIf(String::isNotBlank)
                ?: element.text()

            element.selectFirst("span.chapter-time, span.time")?.text()
                ?.also { date_upload = parseDate(it) }

            chapter_number = CHAPTER_NUMBER_REGEX.find(name)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val currentUrl = document.location()
        val html = document.html()

        if (html.contains("all_imgs_url")) {
            val match = ALL_IMGS_URL_REGEX.find(html)
            if (match != null) {
                val content = match.groupValues[1]
                val urls = content.split(",")
                    .map { it.replace(CLEAN_IMG_URL_REGEX, "") }
                    .filter { it.startsWith("http") }

                urls.forEachIndexed { i, url ->
                    pages.add(Page(i, currentUrl, url))
                }
                if (pages.isNotEmpty()) return pages
            }
        }

        val sourceButton = document.selectFirst("a.cool-blue.vision-button")
        if (sourceButton != null) {
            val sourceUrl = sourceButton.attr("abs:href")
            val requestHeaders = headersBuilder()
                .add("Referer", currentUrl)
                .build()

            return client.newCall(GET(sourceUrl, requestHeaders)).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to follow redirect: ${response.code}")
                pageListParse(response.asJsoup())
            }
        }

        document.select("div.pic_box img, div.reading-content img").forEach { img ->
            val url = img.attr("abs:src")
            if (url.isNotBlank() && !url.contains("cover") && !url.contains("logo")) {
                pages.add(Page(pages.size, currentUrl, url))
            }
        }

        val otherSubPages = document.select("select.sl-page option")
            .map { it.attr("value") }
            .filter { it.isNotBlank() && !currentUrl.contains(it) }

        if (otherSubPages.isNotEmpty()) {
            otherSubPages.forEach { subPath ->
                val subUrl = if (subPath.startsWith("http")) subPath else baseUrl + subPath
                try {
                    client.newCall(GET(subUrl, headers)).execute().use { resp ->
                        val subDoc = resp.asJsoup()
                        subDoc.select("div.pic_box img, div.reading-content img").forEach { img ->
                            val imgUrl = img.attr("abs:src")
                            if (imgUrl.isNotBlank() && !imgUrl.contains("cover") && !pages.any { it.imageUrl == imgUrl }) {
                                pages.add(Page(pages.size, subUrl, imgUrl))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return pages
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
