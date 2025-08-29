package eu.kanade.tachiyomi.extension.en.rdscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RDScans : Madara(
    "RD Scans",
    "https://rdscans.com",
    "en",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
) {
    override val mangaSubString = "new"

    override fun genresRequest(): Request = GET("$baseUrl/?s=&post_type=wp-manga", headers)
    override fun popularMangaSelector() = "div.page-item-detail"
    override fun searchMangaSelector() = "div.c-tabs-item__content"

    // Filter Webnovels
    private fun parseAndFilterMangasPage(response: Response, selector: String, fromElement: (Element) -> SManga): MangasPage {
        val document = response.asJsoup()
        val manga = document.select(selector)
            .filterNot { it.selectFirst("a")?.attr("title")?.contains("(WN)") ?: false }
            .map(fromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage =
        parseAndFilterMangasPage(response, popularMangaSelector(), this::popularMangaFromElement)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage =
        parseAndFilterMangasPage(response, searchMangaSelector(), this::searchMangaFromElement)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        launchIO { countViews(document) }
        val mangaUrl = document.location().removeSuffix("/")
        val xhrRequest = POST("$mangaUrl/ajax/chapters/", xhrHeaders)
        val xhrResponse = client.newCall(xhrRequest).execute()
        val chapterListDocument = xhrResponse.asJsoup()
        return chapterListDocument.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override val mangaDetailsSelectorStatus = ".post-content_item:has(.summary-heading:contains(Status)) .summary-content"
    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }
        return document.select("div.reading-content .separator img").mapIndexed { i, img ->
            Page(i, document.location(), imageFromElement(img))
        }
    }
}
