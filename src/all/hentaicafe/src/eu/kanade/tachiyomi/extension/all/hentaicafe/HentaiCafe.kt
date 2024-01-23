package eu.kanade.tachiyomi.extension.all.hentaicafe

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiCafe : ParsedHttpSource() {

    override val name = "Hentai Cafe"

    override val baseUrl = "https://hentaicafe.xxx"

    override val lang = "all"

    override val supportsLatest = true

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            // Image CDN
            .rateLimitHost("https://cdn.hentaibomb.com".toHttpUrl(), 2)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "en-US,en;q=0.5")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "div.index-popular > div.gallery > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.selectFirst("img")?.getImageUrl()
        title = element.selectFirst("div.caption")!!.text()
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = "div.index-container:contains(new uploads) > div.gallery > a"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "section.pagination > a.last:not(.disabled)"

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/g/$id"))
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.index-container > div.gallery > a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.selectFirst("#cover > a > img")?.getImageUrl()

        with(document.selectFirst("div#bigcontainer > div > div#info")!!) {
            title = selectFirst("h1.title")!!.text()
            artist = getInfo("Artists")
            genre = getInfo("Tags")

            description = buildString {
                select(".title > span").eachText().joinToString("\n").also {
                    append("Full titles:\n$it\n")
                }

                getInfo("Groups")?.also { append("\nGroups: $it") }
                getInfo("Languages")?.also { append("\nLanguages: $it") }
                getInfo("Parodies")?.also { append("\nParodies: $it") }
                getInfo("Pages")?.also { append("\nPages: $it") }
            }
        }

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun Element.getInfo(item: String) =
        select("div.field-name:containsOwn($item) a.tag > span.name")
            .eachText()
            .takeUnless { it.isEmpty() }
            ?.joinToString()

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            chapter_number = 1F
        }

        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.thumbs a.gallerythumb > img").mapIndexed { index, item ->
            val url = item.getImageUrl()
            // Show original images instead of previews
            val imageUrl = url.substringBeforeLast('/') + "/" + url.substringAfterLast('/').replace("t.", ".")
            Page(index, "", imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private fun Element.getImageUrl() = absUrl("data-src").ifEmpty { absUrl("src") }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
