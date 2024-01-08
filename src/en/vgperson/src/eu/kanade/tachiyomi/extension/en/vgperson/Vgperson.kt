package eu.kanade.tachiyomi.extension.en.vgperson

import android.os.Build.VERSION
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import rx.Observable

class Vgperson : ParsedHttpSource() {

    override val name = "vgperson"

    override val lang = "en"

    override val supportsLatest = false

    override val baseUrl = "https://vgperson.com/other/mangaviewer.php"

    private val userAgent = "Mozilla/5.0 " +
        "(Android ${VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${AppInfo.getVersionName()}"

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", userAgent)
        add("Referer", baseUrl)
    }

    override fun popularMangaSelector() = ".content a[href^=?m]"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.text()
        url = element.attr("href")
        thumbnail_url = getCover(title)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map {
            mangaDetailsParse(it).apply {
                url = manga.url
                title = manga.title
                thumbnail_url = manga.thumbnail_url
                initialized = true
            }
        }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        status = when (document.select(".chaptername").first()!!.text()) {
            "(Complete)" -> SManga.COMPLETED
            "(Series in Progress)" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = document.select(".content").first()!!
            .childNodes().drop(5).takeWhile {
                it.nodeName() != "table"
            }.joinToString("") {
                if (it is TextNode) {
                    it.text()
                } else {
                    when ((it as Element).tagName()) {
                        "br" -> "\n"
                        else -> it.text()
                    }
                }
            }
    }

    override fun chapterListSelector() = ".chaptertable tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.select("td > a").first()!!.let {
            name = it.text()
            url = it.attr("href")
        }
        // append the name if it exists & remove the occasional hyphen
        element.select(".chaptername").first()?.let {
            name += " - ${it.text().substringAfter("- ")}"
        }
        // hardcode special chapter numbers for Three Days of Happiness
        chapter_number = url.substringAfterLast("c=").toFloatOrNull()
            ?: (16.5f + "0.${url.substringAfterLast("b=")}".toFloat())
        scanlator = "vgperson"
        date_upload = 0L
    }

    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).sortedByDescending(SChapter::chapter_number)

    override fun pageListParse(document: Document) =
        document.select("img").mapIndexed { i, img -> Page(i, "", img.attr("src")) }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1)
        .map { mp -> MangasPage(mp.mangas.filter { it.title.contains(query, ignoreCase = true) }, false) }

    // get known manga covers from imgur
    private fun getCover(title: String) = when (title) {
        "The Festive Monster's Cheerful Failure" -> "kEK10GL.png"
        "Azure and Claude" -> "buXnlmh.jpg"
        "Three Days of Happiness" -> "kL5dvnp.jpg"
        else -> null
    }?.let { "https://i.imgur.com/$it" }

    override fun latestUpdatesSelector() = ""

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaSelector() = ""

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")
}
