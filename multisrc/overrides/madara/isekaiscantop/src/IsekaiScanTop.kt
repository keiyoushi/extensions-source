package eu.kanade.tachiyomi.extension.en.isekaiscantop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class IsekaiScanTop : Madara(
    "IsekaiScan.top (unoriginal)",
    "https://isekaiscan.top",
    "en",
) {

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/popular-manga?page=$page",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/latest-manga?page=$page",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaId = chaptersWrapper.attr("data-id")
            val xhrHeaders = headersBuilder()
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

    override fun searchPage(page: Int): String = "search?page=$page"

    override fun searchMangaNextPageSelector(): String? = "ul.pagination li:last-child a"
}
