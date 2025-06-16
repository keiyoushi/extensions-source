package eu.kanade.tachiyomi.multisrc.yuyu

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

abstract class YuYu(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val client = network.cloudflareClient

    override val supportsLatest = true

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".top10-section .top10-item a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = ".manga-list .manga-card"

    override fun latestUpdatesNextPageSelector() = "a.page-link:contains(>)"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val url = element.selectFirst("a.manga-cover")!!.absUrl("href")
        val uri = Uri.parse(url)
        val pathSegments = uri.pathSegments
        val lastSegment = URLEncoder.encode(pathSegments.last(), "UTF-8")
        val encodedUrl = uri.buildUpon()
            .path(pathSegments.dropLast(1).joinToString("/") + "/$lastSegment")
            .toString()

        title = element.selectFirst("a.manga-title")!!.text()
        thumbnail_url = element.selectFirst("a.manga-cover img")?.absUrl("data-src")
        setUrlWithoutDomain(encodedUrl)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        return MangasPage(mangas, document.hasNextPage())
    }

    private fun Document.hasNextPage() =
        selectFirst(latestUpdatesNextPageSelector())?.absUrl("href")?.let {
            selectFirst("a.page-link.active")
                ?.absUrl("href")
                .equals(it, ignoreCase = true).not()
        } ?: false

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/manga/$slug", headers))
                .asObservableSuccess()
                .map {
                    val manga = mangaDetailsParse(it.asJsoup())
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = ".search-result-item"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".search-result-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(
            element.attr("onclick").let {
                SEARCH_URL_REGEX.find(it)?.groups?.get(1)?.value!!
            },
        )
    }

    override fun searchMangaNextPageSelector() = null

    // ============================== Manga Details =========================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val details = document.selectFirst(".manga-banner .container")!!
        title = details.selectFirst("h1")!!.text()
        thumbnail_url = details.selectFirst("img")?.absUrl("src")
        genre = details.select(".genre-tag").joinToString { it.text() }
        description = details.selectFirst(".sinopse p")?.text()
        details.selectFirst(".manga-meta > div")?.ownText()?.let {
            status = it.toStatus()
        }
    }

    protected fun String.toStatus(): Int {
        return when (lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "cancelado" -> SManga.CANCELLED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    protected open fun getMangaId(manga: SManga): String {
        val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()
        return document.select("script")
            .map(Element::data)
            .firstOrNull(MANGA_ID_REGEX::containsMatchIn)
            ?.let { MANGA_ID_REGEX.find(it)?.groups?.get(1)?.value }
            ?: throw Exception("Manga ID não encontrado")
    }

    // ============================== Chapters ===============================

    override fun chapterListSelector() = "a.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".capitulo-numero")!!.ownText()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val mangaId = getMangaId(manga)
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val dto = fetchChapterListPage(mangaId, page++).parseAs<ChaptersDto<String>>()
            val document = Jsoup.parseBodyFragment(dto.chapters, baseUrl)
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (dto.hasNext())
        return Observable.just(chapters)
    }

    private fun fetchChapterListPage(mangaId: String, page: Int): Response {
        val url = "$baseUrl/ajax/lzmvke.php?order=DESC".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", page.toString())
            .build()

        return client
            .newCall(GET(url, headers))
            .execute()
    }

    // ============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> {
        return document.select("picture img").mapIndexed { idx, element ->
            Page(idx, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Utilities ===========================

    @Serializable
    class ChaptersDto<T>(val chapters: T, private val remaining: Int) {
        fun hasNext() = remaining > 0
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val SEARCH_URL_REGEX = "'([^']+)".toRegex()
        val MANGA_ID_REGEX = """obra_id:\s+(\d+)""".toRegex()
    }
}
