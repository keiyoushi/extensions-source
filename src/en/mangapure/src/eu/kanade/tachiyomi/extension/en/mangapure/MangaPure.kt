package eu.kanade.tachiyomi.extension.en.mangapure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPure : Madara(
    "MangaPure",
    "https://mangapure.net",
    "en",
    dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.ENGLISH),
) {
    override val useNewChapterEndpoint = false
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun searchMangaNextPageSelector(): String? = ".pagination a[rel=next]"

    override fun searchPage(page: Int): String = "search?page=$page"

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/popular-manga?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-manga?page=$page", headers)

    // Copied from IsekaiScan.top (unoriginal)
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaId = chaptersWrapper.attr("data-id")
            val xhrHeaders = headersBuilder()
                .add("Referer", "$baseUrl/")
                .add("X-Requested-With", "XMLHttpRequest")
                .build()
            val xhrRequest = GET("$baseUrl/ajax-list-chapter?mangaID=$mangaId", xhrHeaders)
            val xhrResponse = client.newCall(xhrRequest).execute()

            chapterElements = xhrResponse.asJsoup().select(chapterListSelector())
            xhrResponse.close()
        }

        countViews(document)
        return chapterElements.map(::chapterFromElement)
    }

    // Copied from IsekaiScan.top (unoriginal)
    override fun pageListParse(document: Document): List<Page> {
        val stringArray = document.select("p#arraydata").text().split(",").toTypedArray()
        return stringArray.mapIndexed { index, url ->
            Page(
                index,
                document.location(),
                url,
            )
        }
    }

    // Some thumbnails expect harimanga.com, which has hotlink protection
    override fun headersBuilder() = super.headersBuilder()
        .removeAll("Referer")

    // OnGoing => Ongoing
    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            document.select(mangaDetailsSelectorStatus).lastOrNull()?.text()
                .takeIf { it == "Ongoing" }
                ?.let { status = SManga.ONGOING }
        }
    }
}
