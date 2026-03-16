package eu.kanade.tachiyomi.extension.all.foamgirl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class FoamGirl : ParsedHttpSource() {
    override val baseUrl = "https://foamgirl.net"
    override val lang = "all"
    override val name = "FoamGirl"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // Popular
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").attr("data-original")
        title = element.select("a.meta-title").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
        initialized = true
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = "a.next"
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page", headers)

    override fun popularMangaSelector() = ".update_area .i_list"

    // Search

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("page")
            addPathSegment("$page")
            addQueryParameter("post_type", "post")
            addQueryParameter("s", query)
        }.build(),
        headers,
    )

    override fun searchMangaSelector() = popularMangaSelector()

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select(".imageclick-imgbox")
            .mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("href"))
            }

        val nextPageUrl = document.selectFirst(".page-numbers[title=Next page]")
            ?.absUrl("href")
            ?.takeIf { HAS_NEXT_PAGE_REGEX in it }
            ?: return pages

        val nextDoc = client.newCall(GET(nextPageUrl, headers))
            .execute()
            .asJsoup()

        return pages + pageListParse(nextDoc)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("link[rel=canonical]").attr("abs:href"))
        chapter_number = 0F
        name = "GALLERY"
        date_upload = getDate(element.select("span.image-info-time").text().substring(1))
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    private fun getDate(str: String): Long = try {
        DATE_FORMAT.parse(str)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }

    companion object {
        val HAS_NEXT_PAGE_REGEX = """(\d+_\d+)""".toRegex()
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy.M.d", Locale.ENGLISH)
        }
    }
}
