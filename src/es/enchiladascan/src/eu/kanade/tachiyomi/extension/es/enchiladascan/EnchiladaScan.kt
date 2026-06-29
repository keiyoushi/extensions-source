package eu.kanade.tachiyomi.extension.es.enchiladascan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import kotlin.concurrent.Volatile

class EnchiladaScan : HttpSource() {

    override val name = "EnchiladaScan"
    private val domainUrl = "https://enchiladascan.github.io"
    override val baseUrl = "$domainUrl/enchiladaweb"
    override val lang = "es"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    @Volatile
    private var catalog: List<Manga> = emptyList()

    @Synchronized
    private fun fetchCatalog() {
        if (catalog.isEmpty()) {
            val result = client.newCall(GET("$baseUrl/catalogo.json")).execute()
            if (!result.isSuccessful) {
                throw Exception("Failed to fetch catalog: HTTP ${result.code}")
            }
            catalog = result.parseAs<Catalog>().items
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        fetchCatalog()
        val mangaList = catalog.map { it.toSManga(baseUrl) }
        return Observable.just(MangasPage(mangaList, false))
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fetchCatalog()
        val mangaList = catalog
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSManga(baseUrl) }

        return Observable.just(MangasPage(mangaList, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val container = document.selectFirst("main.container")!!
        return SManga.create().apply {
            title = container.selectFirst(".manga-title")!!.text()
            thumbnail_url = container.selectFirst(".manga-cover img")!!.attr("abs:src")
            author = container.selectFirst(".manga-meta-list > li:contains(Autor)")?.ownText()
            artist = container.selectFirst(".manga-meta-list > li:contains(Arte)")?.ownText()
            genre = container.selectFirst(".manga-meta-list > li:contains(Género)")?.ownText()
            status = parseStatus(container.selectFirst(".manga-meta-list > li:contains(Estado)")?.ownText())
            description = container.selectFirst(".manga-sinopsis")?.text()
        }
    }

    private fun parseStatus(text: String?): Int = when (text?.trim()?.lowercase()) {
        "en publicación" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getChapterUrl(chapter: SChapter) = "$domainUrl${chapter.url}"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul#chaptersList > li").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                name = element.selectFirst(".cap-title")!!.text()
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = (domainUrl + chapter.url.removeSuffix("/")).toHttpUrl()
        val segments = url.pathSegments
        val mangaSlug = segments[segments.size - 2]
        val chapterSlug = segments.last()
        return GET("$baseUrl/assets/mangas/$mangaSlug/$chapterSlug/images.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<List<String>>()
        return result.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
