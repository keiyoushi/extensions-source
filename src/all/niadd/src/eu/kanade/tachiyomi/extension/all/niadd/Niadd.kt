package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
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

    override val name = "Niadd$nameSuffix"
    override val baseUrl = baseUrlParam
    override val lang = langParam
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    override fun imageUrlParse(document: Document): String = ""

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/list/Hot-Manga.html", headers)

    override fun popularMangaSelector() = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            title = element.select("div.manga-name").text()
            val rawUrl = element.select("a").first()?.attr("href").orEmpty()
            url = rawUrl.removePrefix(baseUrl)
            thumbnail_url = element.select("div.manga-img img").attr("abs:src")
        }

    override fun popularMangaNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/search/?name=$query", headers)

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
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.bookside-general, div.detail-general")

        title = document.select("h1, .book-headline-name").first()?.text().orEmpty()
        author = infoElement.select(".detail-general-cell:contains(Autor) span, [itemprop=author] span").text()
            .replace("Autor (es):", "", ignoreCase = true).trim()
        artist = infoElement.select(".detail-general-cell:contains(Artista) span").text()
            .replace("Artista:", "", ignoreCase = true).trim()
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
        }?.select("span")?.text()?.trim().orEmpty()

        val yearClean = yearRaw
            .let { text ->
                yearKeywords.fold(text) { acc, keyword -> acc.replace(keyword, "", ignoreCase = true) }
            }
            .trim()

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
                val titleText = title.text().trim()
                if (synopsisKeywords.any { keyword -> titleText.contains(keyword, ignoreCase = true) }) {
                    val nextSection = title.nextElementSibling()
                    if (nextSection != null && nextSection.hasClass("detail-section")) {
                        // evita pegar a seção de gêneros
                        if (!nextSection.select("a[itemprop=genre]").any()) {
                            return@run nextSection.text().trim()
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

        thumbnail_url = document.select("div.detail-img img, div.bookside-img img").attr("abs:src")
        status = SManga.ONGOING
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val chaptersUrl = baseUrl + manga.url.removeSuffix(".html") + "/chapters.html"
        return GET(chaptersUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterListContainer = document.select("ul.chapter-list")

        chapterListContainer.removeClass("dis-hide")
        document.select(".chp-warn-box").remove()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "ul.chapter-list a.hover-underline"

    private fun parseDate(dateString: String): Long {
        if (dateString.contains("atrás") || dateString.contains("ago")) {
            return System.currentTimeMillis()
        }
        return try {
            val format = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            format.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // Pages
    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            val rawUrl = element.attr("href")
            url = rawUrl.removePrefix(baseUrl)

            name = element.select("span.chapter-name, span.name").text()
                .ifBlank { element.text() }

            val dateText = element.select("span.chapter-time, span.time").text()
            date_upload = parseDate(dateText)

            chapter_number = Regex("""Capítulo\s+(\d+(\.\d+)?)""")
                .find(name)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
                ?: -1f
        }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val currentUrl = document.location()
        val html = document.html()
        if (html.contains("all_imgs_url")) {
            val arrayPattern = Regex("""all_imgs_url\s*:\s*\[([\s\S]*?)\]""")
            val match = arrayPattern.find(html)
            if (match != null) {
                val content = match.groupValues[1]
                val urls = content.split(",")
                    .map { it.replace(Regex("""["'\s]"""), "").trim() }
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
                if (!response.isSuccessful) throw Exception("Falha ao seguir bloqueio: ${response.code}")
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
