package eu.kanade.tachiyomi.extension.pt.tiamanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TiaManhwa :
    Madara(
        "Tia Manhwa",
        "https://tiamanhwa.com",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ) {

    override val mangaSubString = "manhwa"

    // Search
    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = GET(
        "$baseUrl/page/$page/?s=$query&post_type=wp-manga",
        headers,
    )

    override fun searchMangaSelector() = "div.page-item-detail.manga"

    // The site returns absolute URLs; Madara requires relative paths to avoid baseUrl duplication.
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".post-title a")!!.text()
        setUrlWithoutDomain(
            element.selectFirst(".post-title a")!!.attr("href"),
        )
        thumbnail_url = element.selectFirst(".item-thumb img")?.attr("src")
    }

    override fun searchMangaNextPageSelector() = "a.next, a.page-numbers.next"

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "#manga-slider-2 .slider__item"

    override fun popularMangaFromElement(element: Element): SManga {
        val anchor = element.selectFirst("a")!!
        return SManga.create().apply {
            title = element.selectFirst("h4 a")?.text()
                ?: anchor.attr("href")
                    .substringAfter("/manhwa/")
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() }

            setUrlWithoutDomain(anchor.attr("href"))
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    // Override to fetch truly updated titles instead of the homepage slider used by default Madara.
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesSelector(): String = "#loop-content .page-listing-item .page-item-detail.manga"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val titleElement = element.selectFirst(".post-title h3 a")
        val thumbElement = element.selectFirst(".item-thumb img")

        return SManga.create().apply {
            title = titleElement?.text() ?: ""
            setUrlWithoutDomain(titleElement?.attr("href") ?: "")
            thumbnail_url = thumbElement?.attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = "a.nextpostslink"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = org.jsoup.Jsoup.parse(
            response.body!!.string(),
            response.request.url.toString(),
        )

        val mangas = document.select(latestUpdatesSelector())
            .map(::latestUpdatesFromElement)

        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()!!) != null

        return MangasPage(mangas, hasNextPage)
    }

    // Chapters
    override fun chapterListSelector() = "li.wp-manga-chapter, li.chapter-item, div.chapter"
}
