package eu.kanade.tachiyomi.extension.en.digitalcomicmuseum

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DigitalComicMuseum() : ParsedHttpSource() {
    override val baseUrl = "https://digitalcomicmuseum.com"
    override val lang = "en"
    override val name = "Digital Comic Museum"
    override val supportsLatest = true

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::errorIntercept)
        .build()

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        manga.title = element.select("a").text()
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "img[alt=Next]"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/stats.php?ACT=latest&start=${page - 1}00&limit=100")
    }

    override fun latestUpdatesSelector() = "tbody > .mainrow"

    // Popular

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/stats.php?ACT=topdl&start=${page - 1}00&limit=100")
    }

    // Search

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val baseElement = element.selectFirst("td > a")!!
        manga.setUrlWithoutDomain(baseElement.attr("abs:href"))
        manga.title = baseElement.text()
        return manga
    }

    override fun searchMangaNextPageSelector() = "Not supported"
    override fun searchMangaSelector() = "#search-results tbody > tr"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("terms", query)
            .build()
        val requestHeaders: Headers = Headers.Builder()
            .addAll(headers)
            .add("Content-Type", "multipart/form-data")
            .build()
        val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("ACT", "dosearch")
            .build()
        return POST(url.toString(), requestHeaders, requestBody)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val elements = document.select(".tableborder")
        manga.title = elements.first()!!.select("#catname").text()
        manga.setUrlWithoutDomain(elements.first()!!.select("#catname > a").attr("abs:href"))
        manga.thumbnail_url = elements.first()!!.selectFirst("table img")!!.attr("abs:src")
        elements.forEach {
            when (it.select("#catname").text()) {
                "Description" -> manga.description = it.selectFirst("table")!!.text()
            }
        }
        return manga
    }

    // Chapters

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.name = element.select("#catname").text()
        chapter.setUrlWithoutDomain(element.select(".tablefooter a:first-of-type").attr("abs:href"))
        return chapter
    }

    override fun chapterListSelector() = ".tableborder:first-of-type"

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".latest-slide > .slick-slide > a").forEachIndexed { index, element ->
            pages.add(Page(index, element.attr("abs:href")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("body > a:nth-of-type(2) > img").attr("src")
    }

    // Interceptor
    private fun errorIntercept(chain: Interceptor.Chain): Response {
        val response: Response = chain.proceed(chain.request())
        if (response.code == 403) {
            val newRequest = response.request
            return client.newCall(newRequest).execute()
        }
        return response
    }
}
