package eu.kanade.tachiyomi.extension.en.mangafre

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.select.Evaluator
import rx.Observable

abstract class MangafreGlobal(
    override val lang: String,
    val supportsSearch: Boolean = true,
) : ParsedHttpSource() {
    override val baseUrl = "https://mangafre.com/"

    override val name = "Mangafre"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().build()


    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=hot_novel&page_num=$page&language=$lang")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        // Mangafre always has a next page button. So we give MangasPage.mangas empty.
        return MangasPage(mangas, true)
    }

    override fun popularMangaSelector(): String {
        return ".list .row"
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("a").attr("alt")
            thumbnail_url = element.selectFirst("img")!!.imgAttr()
            setUrlWithoutDomain(element.select("a").attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? {
        return ".pagination > .next"
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }


    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=last_release&page_num=$page&language=$lang")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        return MangasPage(mangas, true)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }


}

// ============================= Utilities ==============================

private fun Element.imgAttr(): String = when {
    hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
    hasAttr("data-src") -> attr("abs:data-src")
    else -> attr("abs:src")
}

private fun parseChapterDate(date: String): Long {
    // Uppercase the first letter of the string
    val formattedDate = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
    return SimpleDateFormat("MMMM d, yyyy", Locale.FRANCE).parse(formattedDate)?.time ?: 0
}
