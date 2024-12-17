package eu.kanade.tachiyomi.extension.es.manhwalatino

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaLatino : Madara(
    "Manhwa-Latino",
    "https://manhwa-latino.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1)
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("Accept-Encoding")
                .build()
            val response = chain.proceed(request.newBuilder().headers(headers).build())
            if (response.headers("Content-Type").contains("application/octet-stream") && response.request.url.toString().endsWith(".jpg")) {
                val orgBody = response.body.bytes()
                val newBody = orgBody.toResponseBody("image/jpeg".toMediaTypeOrNull())
                response.newBuilder()
                    .body(newBody)
                    .build()
            } else {
                response
            }
        }
        .build()

    override val useNewChapterEndpoint = true

    override val chapterUrlSelector = "div.mini-letters > a"

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado del comic) > div.summary-content"
    override val mangaDetailsSelectorDescription = "div.post-content_item:contains(Resumen) div.summary-container"
    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"

    private val chapterListNextPageSelector = "div.pagination > span.current + span"
    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url
        var document = response.asJsoup()
        launchIO { countViews(document) }

        val chapterList = mutableListOf<SChapter>()
        var page = 1

        do {
            val chapterElements = document.select(chapterListSelector())
            if (chapterElements.isEmpty()) break
            chapterList.addAll(chapterElements.map { chapterFromElement(it) })
            val hasNextPage = document.select(chapterListNextPageSelector).isNotEmpty()
            if (hasNextPage) {
                page++
                val nextPageUrl = mangaUrl.newBuilder().setQueryParameter("t", page.toString()).build()
                document = client.newCall(GET(nextPageUrl, headers)).execute().asJsoup()
            } else { break }
        } while (true)

        return chapterList
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            selectFirst(chapterUrlSelector)!!.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.wholeText().substringAfter("\n")
            }

            chapter.date_upload = selectFirst("img:not(.thumb)")?.attr("alt")?.let { parseRelativeDate(it) }
                ?: selectFirst("span a")?.attr("title")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(selectFirst(chapterDateSelector())?.text())
        }

        return chapter
    }
}
