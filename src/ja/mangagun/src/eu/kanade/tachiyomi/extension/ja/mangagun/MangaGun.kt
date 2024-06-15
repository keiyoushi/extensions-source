package eu.kanade.tachiyomi.extension.ja.mangagun

import android.webkit.CookieManager
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import rx.Observable
import java.util.Calendar

private const val domain = "mangagun.net"

class MangaGun : FMReader("MangaGun", "https://$domain/", "ja") {
    override val infoElementSelector = "div.row div.row"

    // source is picky about URL format
    private fun mangaRequest(sortBy: String, page: Int): Request {
        return GET(
            "$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&magazine=&sort=$sortBy&sort_type=DESC",
            headers,
        )
    }

    override fun popularMangaRequest(page: Int): Request = mangaRequest("views", page)

    override fun latestUpdatesRequest(page: Int): Request = mangaRequest("last_update", page)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val slug = manga.url.substringAfter("manga-").substringBefore(".html")

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
            "mins", "minutes" -> chapterDate.add(Calendar.MINUTE, value * -1)
            "hours" -> chapterDate.add(Calendar.HOUR_OF_DAY, value * -1)
            "days" -> chapterDate.add(Calendar.DATE, value * -1)
            "weeks" -> chapterDate.add(Calendar.DATE, value * 7 * -1)
            "months" -> chapterDate.add(Calendar.MONTH, value * -1)
            "years" -> chapterDate.add(Calendar.YEAR, value * -1)
            else -> return 0
        }

        return chapterDate.timeInMillis
    }

    private fun handleDdosProtect(document: Document): Document {
        val key = document.select("script:containsData(document.cookie)").first()?.html()
            ?.substringAfter("escape('")?.substringBefore("'")
        // save anti_ddos_key
        CookieManager.getInstance()
            .setCookie(baseUrl, "ct_anti_ddos_key=$key; Domain=$domain; Path=/")
        return document.select("noscript div a").attr("abs:href").let { url ->
            client.newCall(GET(url, headers)).execute().asJsoup()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val ddosProtect = document.select("title").first()?.text()
            ?.contains("Manga Gun - DDoS protection") ?: false
        return if (ddosProtect) {
            handleDdosProtect(document)
        } else {
            document
        }.select("script:containsData(load_image)")
            .html()
            .substringAfter("(")
            .substringBefore(",")
            .let { cid ->
                client.newCall(
                    GET(
                        "$baseUrl/app/manga/controllers/cont.Showimage.php?cid=$cid",
                        headers,
                    ),
                ).execute().asJsoup()
            }
            .select(".lazyload")
            .mapIndexed { i, e ->
                Page(i, "", e.attr("abs:data-src"))
            }
    }
}
