package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Ikiru : ParsedHttpSource() {
    // Formerly "MangaTale"
    override val id = 1532456597012176985

    override val name = "Ikiru"
    override val baseUrl = "https://01.ikiru.wtf"
    override val lang = "id"
    override val supportsLatest = true

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(12, 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-update/?the_page=$page", headers)
    }

    override fun latestUpdatesSelector() = "#search-results > div:not(.col-span-full)"

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "#search-results ~ div.col-span-full a:has(svg):last-of-type"

    // Search
    private var searchNonce: String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // TODO: Filter

        if (searchNonce.isNullOrEmpty()) {
            val document = client.newCall(
                GET("$baseUrl/ajax-call?type=search_form&action=get_nonce", headers),
            ).execute().asJsoup()
            searchNonce = document.selectFirst("input[name=search_nonce]")!!.attr("value")
        }

        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("query", query)
            .addFormDataPart("page", "$page")
            .addFormDataPart("nonce", searchNonce!!)
            .build()

        return POST("$baseUrl/ajax-call?action=advanced_search", body = requestBody)
    }

    override fun searchMangaSelector() = "div.overflow-hidden:has(a.font-medium)"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")?.absUrl("href") ?: "")
        thumbnail_url = element.selectFirst("img")?.absUrl("src") ?: ""
        title = element.selectFirst("a.font-medium")?.text() ?: ""
        status = parseStatus(element.selectFirst("div span ~ p")?.text() ?: "")
    }

    override fun searchMangaNextPageSelector() = "div button:has(svg)"

    // Manga Details
    private fun Element.getMangaId() = selectFirst("#gallery-list")?.attr("hx-get")
        ?.substringAfter("manga_id=")?.substringBefore("&")

    override fun mangaDetailsParse(document: Document): SManga {
        document.selectFirst("article > section").let { element ->
            return SManga.create().apply {
                thumbnail_url = element!!.selectFirst(".contents img")?.absUrl("src") ?: ""
                title = element.selectFirst("h1.font-bold")?.text() ?: ""
                // TODO: prevent status value from browse change back to default

                val altNames = element.selectFirst("h1 ~ .line-clamp-1")?.text() ?: ""
                val synopsis = element.selectFirst("#tabpanel-description div[data-show='false']")?.text() ?: ""
                description = buildString {
                    append(synopsis)
                    if (altNames.isNotEmpty()) {
                        append("\n\nAlternative Title: ", altNames)
                    }
                    document.getMangaId()?.also {
                        append("\n\nID: ", it) // for fetching chapter list
                    }
                }
                genre = element.select(".space-y-2 div:has(img) p, #tabpanel-description .flex-wrap span").joinToString { it.text() }
            }
        }
    }

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val mangaId = manga.description
            ?.substringAfterLast("ID: ", "")
            ?.takeIf { it.toIntOrNull() != null }
            ?: client.newCall(mangaDetailsRequest(manga)).execute().asJsoup().getMangaId()
            ?: throw Exception("Could not find manga ID")

        val chapterListUrl = "$baseUrl/ajax-call".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", "") // keep empty for loading hidden chapter
            .addQueryParameter("action", "chapter_list")
            .build()

        val response = client.newCall(GET(chapterListUrl, headers)).execute()

        response.asJsoup().select("#chapter-list .cursor-pointer a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.select("span").text()
                date_upload = dateFormat.tryParse(element.select("time").attr("datetime"))
            }
        }
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("main .relative section > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Others
    private fun parseStatus(element: String?): Int {
        if (element.isNullOrEmpty()) {
            return SManga.UNKNOWN
        }
        return when (element.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on hiatus" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
    }
}
