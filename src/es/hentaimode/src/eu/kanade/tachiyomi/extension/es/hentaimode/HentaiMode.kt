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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiMode : HttpSource() {

    override val name = "HentaiMode"

    override val baseUrl = "https://hentaimode.com"

    override val lang = "es"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.row div[class*=\"book-list\"] > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".book-description > p")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
        val id = query.removePrefix(PREFIX_SEARCH)
        client.newCall(GET("$baseUrl/g/$id", headers))
            .asObservableSuccess()
            .map(::searchMangaByIdParse)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val details = mangaDetailsParse(doc).apply {
            setUrlWithoutDomain(doc.location())
        }
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        require(query.length >= 3) { "Please use at least 3 characters!" }

        val url = "$baseUrl/buscar".toHttpUrl()
            .newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    private val additionalInfos = listOf("Serie", "Tipo", "Personajes", "Idioma")

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
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
                        append("\n")
                    }
                }
            }
        }
    }

    private fun Element.getInfo(text: String): String? = select("div.tag-container:containsOwn($text) a.tag")
        .joinToString { it.text() }
        .takeIf(String::isNotEmpty)

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url.replace("/g/", "/leer/")
            chapter_number = 1F
            name = "Chapter"
        }

        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(page_image)")!!.data()
        val pagePaths = script.substringAfter("pages = [")
            .substringBefore(",]")
            .substringBefore("]") // Just to make sure
            .split(',')
            .map {
                it.substringAfter(":").substringAfter('"').substringBefore('"')
            }

        return pagePaths.mapIndexed { index, path ->
            Page(index, imageUrl = path)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
