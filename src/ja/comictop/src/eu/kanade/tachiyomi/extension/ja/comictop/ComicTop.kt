package eu.kanade.tachiyomi.extension.ja.comictop

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.PrintWriter
import java.io.StringWriter

class ComicTop : ParsedHttpSource() {

    override val name = "ComicTop"

    override val baseUrl = "https://comic-top.com/"

    override val lang = "ja"

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".ホットマンガ a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        Log.i("ComicTop", element.html())
        val img = element.selectFirst("img")!!
        thumbnail_url = img.absUrl("data-src").substringBefore("?")
        title = img.attr("title")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapter/page/$page/", headers)

    override fun latestUpdatesSelector() = ".animepost a"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = "#nextpagination"

    override fun latestUpdatesParse(response: Response): MangasPage {
        try {
            val res = super.latestUpdatesParse(response)
            Log.i("ComicTop", res.toString())
            return res
        } catch (e: Exception) {
            Log.e("ComicTop", getStackTraceAsString(e))
            throw e
        }
    }

    private fun getStackTraceAsString(e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        return sw.toString()
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query).build(),
        headers,
    )

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // ============================== Filters ===============================

    // No filters
}
