package eu.kanade.tachiyomi.extension.ja.comictop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicTop : ParsedHttpSource() {

    override val name = "ComicTop"

    override val baseUrl = "https://comic-top.com"

    override val lang = "ja"

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular/page/$page", headers)

    override fun popularMangaSelector() = ".animposx > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val img = element.selectFirst("img")!!
        thumbnail_url = img.imgAttr()
        title = img.attr("title")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = "#nextpagination"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapter/page/$page/", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query).build(),
        headers,
    )

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst(".infoanime")!!
        with(infoElement) {
            title = getElementsByTag("h1").first()!!.text()
            author = select(".author-info > a").eachText().joinToString()
            genre = select(".genre-info > a[rel=tag]").eachText().joinToString()
            thumbnail_url = select("img").first()?.imgAttr()
            description = select("div[itemprop=description]").text()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector(): String = "#chapter_list li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        with(element.selectFirst("a[title]")!!) {
            url = "/" + attr("abs:href").toHttpUrl().queryParameter("ct")!!
            name = attr("title")
        }
        date_upload = dateFormat.tryParse(element.selectFirst(".date")?.text())
        chapter_number = element.getElementsByTag("chapter").text().toFloatOrNull()!!
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#imagech").map { it ->
            Page(it.attr("img-id").toInt(), imageUrl = it.imgAttr()!!)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // ============================== Filters ===============================

    // No filters

    // =============================== Utils ==================================
    private fun Element.imgAttr(): String? {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
            .substringBefore("?")
    }

    private val dateFormat = SimpleDateFormat("M\u6708 d, yyyy", Locale.JAPANESE)
}
