package eu.kanade.tachiyomi.extension.fr.manhwafreakfr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaFreakFR : MangaThemesia("ManhwaFreak", "https://manhwafreak.fr", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)) {
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/?type=comic", headers)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/?order=views&type=comic", headers)

    override fun searchMangaSelector() = ".listupd .lastest-serie"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/page/$page/?s=$query")

    override fun chapterListSelector() = ".chapter-li a:not(:has(svg))"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".chapter-info p:nth-child(1)").text().ifBlank { urlElements.first()!!.text() }
        date_upload = element.selectFirst(".chapter-info p:nth-child(2)")?.text().parseChapterDate()
    }

    override fun getFilterList() = FilterList()
}
