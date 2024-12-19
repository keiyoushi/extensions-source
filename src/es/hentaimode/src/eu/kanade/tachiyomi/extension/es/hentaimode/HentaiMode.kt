package eu.kanade.tachiyomi.extension.es.hentaimode

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

class HentaiMode : ParsedHttpSource() {

    override val name = "HentaiMode"

    override val baseUrl = "https://hentaimode.com"

    override val lang = "es"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "div.row div[class*=\"book-list\"] > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".book-description > p")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

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
        val doc = response.asJsoup()
        val details = mangaDetailsParse(doc)
            .apply { setUrlWithoutDomain(doc.location()) }
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        require(query.length >= 3) { "Please use at least 3 characters!" }
        return GET("$baseUrl/buscar?s=$query")
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    // =========================== Manga Details ============================
    private val additionalInfos = listOf("Serie", "Tipo", "Personajes", "Idioma")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.selectFirst("div#cover img")?.absUrl("src")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        with(document.selectFirst("div#info-block > div#info")!!) {
            title = selectFirst("h1")!!.text()
            genre = getInfo("Categorías")
            author = getInfo("Grupo")
            artist = getInfo("Artista")

            description = buildString {
                additionalInfos.forEach { info ->
                    getInfo(info)?.also {
                        append(info)
                        append(": ")
                        append(it)
                    }
                }
            }
        }
    }

    private fun Element.getInfo(text: String): String? {
        return select("div.tag-container:containsOwn($text) a.tag")
            .joinToString { it.text() }
            .takeUnless(String::isBlank)
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url.replace("/g/", "/leer/")
            chapter_number = 1F
            name = "Chapter"
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
        val script = document.selectFirst("script:containsData(page_image)")!!.data()
        val pagePaths = script.substringAfter("pages = [")
            .substringBefore(",]")
            .substringBefore("]") // Just to make sure
            .split(',')
            .map {
                it.substringAfter(":").substringAfter('"').substringBefore('"')
            }

        return pagePaths.mapIndexed { index, path ->
            Page(index, imageUrl = baseUrl + path)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
