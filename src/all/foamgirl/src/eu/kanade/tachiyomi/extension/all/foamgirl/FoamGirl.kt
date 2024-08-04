package eu.kanade.tachiyomi.extension.all.foamgirl

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class FoamGirl() : ParsedHttpSource() {
    override val baseUrl = "https://foamgirl.net"
    override val lang = "all"
    override val name = "FoamGirl"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").attr("data-original")
        val page_count = element.select(".postlist-imagenum").text()
        title = element.select("a.meta-title").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
        initialized = true
    }

    override fun popularMangaNextPageSelector() = "a.next"
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page", headers)
    }

    override fun popularMangaSelector() = ".update_area .i_list"

    // Search

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("page")
                addPathSegment("$page")
                addQueryParameter("post_type", "post")
                addQueryParameter("s", query)
            }.build(),
            headers,
        )
    }

    override fun searchMangaSelector() = popularMangaSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1").text()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imageCount = document.select(".post_title_topimg").text().split("(")[1].split('P')[0].toInt()
        val imageUrl = document.select(".imageclick-imgbox").attr("href").toHttpUrl()
        val imageId = imageUrl.pathSegments[imageUrl.pathSize - 1].split(".")[0].toLong() / 10
        for (i in 0 until imageCount) {
            pages.add(
                Page(
                    i,
                    imageUrl = imageUrl.newBuilder().apply {
                        removePathSegment(imageUrl.pathSize - 1)
                        addPathSegment("${imageId}${i + 2}.jpg")
                    }.build().toString(),
                ),
            )
        }
        return pages
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("link[rel=canonical]").attr("abs:href"))
        chapter_number = 0F
        name = "GALLERY"
        date_upload = getDate(element.select("span.image-info-time").text())
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    private fun getDate(str: String): Long {
        return try {
            DATE_FORMAT.parse(str.substring(1))?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy.M.d", Locale.ENGLISH)
        }
    }
}
