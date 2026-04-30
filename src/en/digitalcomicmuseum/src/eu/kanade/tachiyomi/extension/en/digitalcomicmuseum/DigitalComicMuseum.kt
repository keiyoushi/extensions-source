package eu.kanade.tachiyomi.extension.en.digitalcomicmuseum

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class DigitalComicMuseum : HttpSource() {
    override val baseUrl = "https://digitalcomicmuseum.com"
    override val lang = "en"
    override val name = "Digital Comic Museum"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::errorIntercept)
        .build()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/stats.php?ACT=latest&start=${page - 1}00&limit=100", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("tbody > .mainrow").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
            }
        }
        val hasNextPage = document.selectFirst("img[alt=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/stats.php?ACT=topdl&start=${page - 1}00&limit=100", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("tbody > .mainrow").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
            }
        }
        val hasNextPage = document.selectFirst("img[alt=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("terms", query)
            .build()
        val requestHeaders: Headers = headers.newBuilder()
            .add("Content-Type", "multipart/form-data")
            .build()
        val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("ACT", "dosearch")
            .build()
        return POST(url.toString(), requestHeaders, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#search-results tbody > tr").map { element ->
            SManga.create().apply {
                val baseElement = element.selectFirst("td > a")!!
                setUrlWithoutDomain(baseElement.attr("abs:href"))
                title = baseElement.text()
            }
        }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        val elements = document.select(".tableborder")
        val firstElement = elements.first() ?: return manga

        manga.title = firstElement.select("#catname").text()
        manga.setUrlWithoutDomain(firstElement.selectFirst("#catname > a")!!.attr("abs:href"))
        manga.thumbnail_url = firstElement.selectFirst("table img")?.attr("abs:src")

        elements.forEach {
            when (it.selectFirst("#catname")?.text()) {
                "Description" -> manga.description = it.selectFirst("table")?.text()
            }
        }
        return manga
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".tableborder:first-of-type").map { element ->
            SChapter.create().apply {
                name = element.select("#catname").text()
                setUrlWithoutDomain(element.selectFirst(".tablefooter a:first-of-type")!!.attr("abs:href"))
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".latest-slide > .slick-slide > a").mapIndexed { index, element ->
            Page(index, url = element.attr("abs:href"))
        }
    }

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        return document.selectFirst("body > a:nth-of-type(2) > img")?.attr("abs:src") ?: ""
    }

    // Interceptor

    private fun errorIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 403) {
            response.close()
            return client.newCall(response.request).execute()
        }
        return response
    }
}
