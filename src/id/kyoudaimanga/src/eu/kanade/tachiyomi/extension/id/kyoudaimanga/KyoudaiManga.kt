package eu.kanade.tachiyomi.extension.id.kyoudaimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class KyoudaiManga : MangaThemesia(
    "KyoudaiManga",
    "https://www.kyoudaimanga.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id", "ID")),
) {

    override val hasProjectPage = true

    override val client: okhttp3.OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaSelector() = "#wpop-items > div[data-id='weekly'] > ul > li"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val a = element.selectFirst("a")!!
        manga.setUrlWithoutDomain(a.attr("abs:href"))
        manga.title = a.ownText().trim()
        manga.thumbnail_url = element.selectFirst("img")?.attr("abs:src") ?: element.selectFirst("img")?.attr("abs:data-src")
        return manga
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesSelector() = ".post-outer, .blog-posts .post"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/p/daftar-komik.html", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isBlank()) "$baseUrl/p/daftar-komik.html" else "$baseUrl/search/label/Komik?q=${encodeURI(query)}"
        return GET(url, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        manga.title = manga.title.replace(Regex("_\\d+\\.?html?$"), "").trim()
        manga.thumbnail_url = document.selectFirst(".post-thumbnail img, .entry-content img")?.attr("abs:src")
        manga.status = when {
            document.text().contains("Ongoing", true) -> SManga.ONGOING
            document.text().contains("Completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun chapterListSelector() = "ul li a[href*='.html'], .entry-content a[href*='.html']:not([href*='/p/'])"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val a = element
        chapter.setUrlWithoutDomain(a.attr("abs:href"))
        chapter.name = a.text().trim()
        chapter.date_upload = parseChapterDate(a.parent()?.text() ?: "")
        return chapter
    }

    private fun parseChapterDate(dateText: String): Long {
        return try {
            dateFormat.parse(dateText)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".entry-content img, .post-body img")
            .mapIndexed { i, img ->
                val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                Page(i, imageUrl = url)
            }
            .filter { it.imageUrl!!.isNotBlank() }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun encodeURI(query: String) = java.net.URLEncoder.encode(query, "UTF-8")
}