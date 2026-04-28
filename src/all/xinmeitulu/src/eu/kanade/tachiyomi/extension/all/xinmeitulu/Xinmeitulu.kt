package eu.kanade.tachiyomi.extension.all.xinmeitulu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import rx.Observable

class Xinmeitulu : HttpSource() {
    override val baseUrl = "https://www.xinmeitulu.com"
    override val lang = "all"
    override val name = "Xinmeitulu"
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder().addInterceptor(::contentTypeIntercept).build()

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container > .row > div:has(figure)").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("figure > a").attr("abs:href"))
                title = element.select("figcaption").text()
                thumbnail_url = element.select("img").attr("abs:data-original-")
                genre = element.select("a.tag").joinToString(", ") { it.text() }
            }
        }
        val hasNextPage = document.selectFirst(".next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page?s=$query", headers)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith("SLUG:")) {
        val slug = query.removePrefix("SLUG:")
        client.newCall(GET("$baseUrl/photo/$slug", headers)).asObservableSuccess()
            .map { response -> MangasPage(listOf(mangaDetailsParse(response)), false) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    // Details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.attr("abs:href"))
        title = document.select(".container > h1").text()
        description = document.select(".container > *:not(div)").text()
        status = SManga.COMPLETED
        thumbnail_url = document.selectFirst("figure img")!!.attr("abs:data-original")
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.attr("abs:href"))
                name = document.select(".container > h1").text()
            },
        )
    }

    // Pages

    override fun pageListParse(response: Response) = response.asJsoup()
        .select(".container > div > figure img")
        .mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:data-original"))
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private fun contentTypeIntercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.header("content-type")?.startsWith("image") == true) {
                val body = response.body.source().asResponseBody(jpegMediaType)
                return response.newBuilder().body(body).build()
            }
            return response
        }

        private val jpegMediaType = "image/jpeg".toMediaType()
    }
}
