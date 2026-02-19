package eu.kanade.tachiyomi.extension.ja.mangagun

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

private const val DOMAIN = "nihonkuni.com"

/**
 * This plugin is for the website originally named MangaGun(漫画軍), which was later renamed to NihonKuni(日本国). Please do not be confused by the names.
 */
class MangaGun : FMReader("NihonKuni", "https://$DOMAIN", "ja") {

    // Formerly "MangaGun(漫画軍)"
    override val id = 3811800324362294701

    override val infoElementSelector = "div.manga-detail-container"
    override val mangaDetailsSelectorDescription = ".description-text-content, .manga-info-list > li:nth-child(1) .info-field-value"

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(CookieInterceptor(DOMAIN, "smartlink_shown" to "1")).build()

    // source is picky about URL format
    private fun mangaRequest(sortBy: String, page: Int): Request = GET(
        "$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&magazine=&sort=$sortBy&sort_type=DESC",
        headers,
    )

    override fun popularMangaRequest(page: Int): Request = mangaRequest("views", page)

    override fun latestUpdatesRequest(page: Int): Request = mangaRequest("last_update", page)

    override fun popularMangaSelector() = "div.manga-grid div.manga-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst(".manga-title")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
        thumbnail_url = getImgAttr(element.selectFirst(".manga-cover"))
    }

    override fun getImgAttr(element: Element?): String? = when {
        element == null -> null

        element.hasAttr("data-original") -> element.attr("abs:data-original")

        element.hasAttr("data-src") -> element.attr("abs:data-src")

        element.hasAttr("data-bg") -> element.attr("abs:data-bg")

        element.hasAttr("data-srcset") -> element.attr("abs:data-srcset")

        element.hasAttr("style") -> element.attr("style").substringAfter("url(")
            .substringBefore(")").trim('\'', '"')

        else -> element.attr("abs:src")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val index = manga.url.indexOf("manga-")
        // Handling version compatibility, previous version used the 'raw-' prefix.
        val slug = if (index >= 0) {
            manga.url.substring(index + 6)
        } else {
            manga.url.substringAfter("raw-")
        }.substringBefore(".html")

        return client.newCall(
            GET(
                "$baseUrl/app/manga/controllers/cont.Listchapter.php?slug=$slug",
                headers,
            ),
        )
            .asObservableSuccess()
            .map { res ->
                res.asJsoup().select(".at-series a").map {
                    SChapter.create().apply {
                        name = it.select(".chapter-name").text()
                        url = it.attr("abs:href").substringAfter("controllers")
                        date_upload = parseChapterDate(it.select(".chapter-time").text())
                    }
                }
            }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val chapterDate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (date.split(' ')[dateWordIndex]) {
            "mins", "minutes" -> chapterDate.add(Calendar.MINUTE, -value)
            "hours" -> chapterDate.add(Calendar.HOUR_OF_DAY, -value)
            "days" -> chapterDate.add(Calendar.DATE, -value)
            "weeks" -> chapterDate.add(Calendar.DATE, -value * 7)
            "months" -> chapterDate.add(Calendar.MONTH, -value)
            "years" -> chapterDate.add(Calendar.YEAR, -value)
            else -> return 0
        }

        return chapterDate.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val html = document.select("script:containsData(window.chapterImages)").first()!!.data()
            .substringAfter("window.chapterImages=\"").substringBefore("\"")
            .replace("\\/", "/")
            .replace("\\n", "")
            .replace("\\r", "")
        return Jsoup.parse(html).select("img.chapter-img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:data-srcset").trim())
        }
    }
}
