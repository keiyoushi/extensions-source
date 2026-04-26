package eu.kanade.tachiyomi.extension.all.foamgirl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class FoamGirl : HttpSource() {
    override val baseUrl = "https://foamgirl.net"
    override val lang = "all"
    override val name = "FoamGirl"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".update_area .i_list").map { element ->
            SManga.create().apply {
                thumbnail_url = element.select("img").attr("data-original")
                title = element.select("a.meta-title").text()
                setUrlWithoutDomain(element.select("a").attr("href"))
                initialized = true
            }
        }
        val hasNextPage = document.selectFirst("a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("page")
            addPathSegment("$page")
            addQueryParameter("post_type", "post")
            addQueryParameter("s", query)
        }.build(),
        headers,
    )

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ======================================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ============================== Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(document.select("link[rel=canonical]").attr("abs:href"))
                chapter_number = 0F
                name = "GALLERY"
                date_upload = getDate(document.select("span.image-info-time").text().substring(1))
            },
        )
    }

    // ============================== Pages ======================================

    override fun pageListParse(response: Response): List<Page> {
        val allPages = mutableListOf<Page>()
        var document = response.asJsoup()
        var pageIndex = 0

        while (true) {
            document.select(".imageclick-imgbox").forEach { element ->
                allPages.add(Page(pageIndex++, imageUrl = element.absUrl("href")))
            }

            val nextPageUrl = document.selectFirst(".page-numbers[title=Next page]")
                ?.absUrl("href")
                ?.takeIf { HAS_NEXT_PAGE_REGEX in it }
                ?: break

            document = client.newCall(GET(nextPageUrl, headers)).execute().asJsoup()
        }

        return allPages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ======================================

    private fun getDate(str: String): Long = try {
        DATE_FORMAT.parse(str)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }

    companion object {
        private val HAS_NEXT_PAGE_REGEX = """(\d+_\d+)""".toRegex()
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy.M.d", Locale.ENGLISH)
        }
    }
}
