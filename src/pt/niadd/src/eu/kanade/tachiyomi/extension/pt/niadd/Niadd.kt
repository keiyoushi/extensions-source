package eu.kanade.tachiyomi.extension.pt.niadd

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

class Niadd : ParsedHttpSource() {

    override val name = "Niadd"
    override val baseUrl = "https://br.niadd.com"
    override val lang = "pt"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)
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
            url = rawUrl.removePrefix(baseUrl).removePrefix("https://br.niadd.com")
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

        val yearRaw = infoElement.select(".detail-general-cell:contains(Liberado) span").text().trim()
        val yearClean = yearRaw.replace("Liberado:", "", ignoreCase = true).trim()
        val synopsisText = run {
            val titles = document.select(".detail-cate-title")
            for (title in titles) {
                if (title.text().contains("Sinopse", ignoreCase = true)) {
                    val nextSection = title.nextElementSibling()
                    if (nextSection != null && nextSection.hasClass("detail-section")) {
                        return@run nextSection.text().trim()
                    }
                }
            }

            val sections = document.select("section.detail-section")
            for (section in sections) {
                val text = section.text().trim()
                if (text.length > 50 && text.length < 2000 && !text.contains("Capítulo") && !text.contains("Género") &&
                    !text.contains("Autor")
                ) {
                    return@run text
                }
            }
            return@run ""
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

        document.select("div.pic_box img, div.reading-content img").forEach { img ->
            val url = img.attr("abs:src")
            if (url.isNotBlank() && !url.contains("cover") && !url.contains("logo")) {
                pages.add(Page(pages.size, "", url))
            }
        }

        val otherSubPages = document.select("select.sl-page option")
            .map { it.attr("value") }
            .filter { it.isNotBlank() && !document.location().contains(it) }

        otherSubPages.forEach { subPagePath ->
            val subPageUrl = if (subPagePath.startsWith("http")) {
                subPagePath
            } else {
                baseUrl + subPagePath
            }

            try {
                val subDocument = client
                    .newCall(GET(subPageUrl, headers))
                    .execute()
                    .asJsoup()

                subDocument.select("div.pic_box img, div.reading-content img").forEach { img ->
                    val url = img.attr("abs:src")
                    if (url.isNotBlank() && !url.contains("cover") && !url.contains("logo")) {
                        if (pages.none { it.imageUrl == url }) {
                            pages.add(Page(pages.size, "", url))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return pages
    }
}
