package eu.kanade.tachiyomi.extension.en.cloudrecess

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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
import rx.Observable

class CloudRecess : ParsedHttpSource() {

    override val name = "CloudRecess"

    override val baseUrl = "https://cloudrecess.io"

    override val lang = "en"

    override val supportsLatest = true

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    // To load images
    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "swiper-container#popular-cards div#card-real > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("h2.text-sm")?.text() ?: "Manga"
        thumbnail_url = element.selectFirst("img")?.run {
            absUrl("data-src").ifEmpty { absUrl("src") }
        }
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = "section:has(h2:containsOwn(Recent Chapters)) div#card-real > a"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "ul.pagination > li:last-child:not(.pagination-disabled)"

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/manga/$id"))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response.use { it.asJsoup() })
        return MangasPage(listOf(details), false)
    }

    override fun getFilterList() = CloudRecessFilters.FILTER_LIST

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga?title=$query&page=$page".toHttpUrl().newBuilder().apply {
            val params = CloudRecessFilters.getSearchParameters(filters)
            if (params.type.isNotEmpty()) addQueryParameter("type", params.type)
            if (params.status.isNotEmpty()) addQueryParameter("status", params.status)
            params.genres.forEach { addQueryParameter("genre[]", it) }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "main div#card-real > a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        // Absolutely required element, so throwing a NPE when it's not present
        // seems reasonable.
        with(document.selectFirst("main > section > div")!!) {
            thumbnail_url = selectFirst("div.relative img")?.absUrl("src")
            title = selectFirst("div.flex > h2")?.ownText() ?: "No name"
            genre = select("div.flex > a.inline-block").eachText().joinToString()
            description = selectFirst("div.comicInfoExtend__synopsis")?.text()
        }

        document.selectFirst("div#buttons + div.hidden")?.run {
            status = when (getInfo("Status").orEmpty()) {
                "Cancelled" -> SManga.CANCELLED
                "Completed" -> SManga.COMPLETED
                "Hiatus" -> SManga.ON_HIATUS
                "Ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            artist = getInfo("Artist")
            author = getInfo("Author")
        }
    }

    private fun Element.getInfo(text: String): String? =
        selectFirst("p:has(span:containsOwn($text)) span.capitalize")
            ?.ownText()
            ?.trim()

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "div#chapters-list > a[href]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val originalUrl = response.request.url.toString()

        val chapterList = buildList {
            var page = 1
            do {
                val doc = when {
                    isEmpty() -> response // First page
                    else -> {
                        page++
                        client.newCall(GET("$originalUrl?page=$page", headers)).execute()
                    }
                }.use { it.asJsoup() }

                addAll(doc.select(chapterListSelector()).map(::chapterFromElement))
            } while (doc.selectFirst(latestUpdatesNextPageSelector()) != null)
        }

        return chapterList
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span")?.ownText() ?: "Chapter"
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#chapter-container > img").map { element ->
            val id = element.attr("data-id").toIntOrNull() ?: 0
            val url = element.run {
                absUrl("data-src").ifEmpty { absUrl("src") }
            }
            Page(id, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used.")
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
