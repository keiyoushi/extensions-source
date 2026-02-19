package eu.kanade.tachiyomi.extension.en.mangadass

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDass :
    Madara(
        "Manga Dass",
        "https://mangadass.com",
        "en",
        SimpleDateFormat("dd MMM yyyy", Locale.US),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val filterNonMangaItems = false

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=trending", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=latest", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun chapterListSelector() = ".row-content-chapter li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(absUrl("href"))
        }
        date_upload = parseChapterDate(element.selectFirst(".chapter-time")?.text())
    }

    override fun pageListParse(document: Document): List<Page> = document.select(".read-content img").mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("src"))
    }
}
