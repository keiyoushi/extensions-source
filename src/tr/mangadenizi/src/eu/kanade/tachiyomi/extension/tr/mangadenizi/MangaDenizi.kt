package eu.kanade.tachiyomi.extension.tr.mangadenizi

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
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDenizi : HttpSource() {
    override val name = "MangaDenizi"
    override val baseUrl = "https://www.mangadenizi.net"
    override val lang = "tr"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)

    // ===============================
    // Popular
    // ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.asJsoup().extractInertia<MangaIndexDto>().manga
        val mangas = json.data.map { it.toSManga() }
        val hasNextPage = json.currentPage < json.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // ===============================
    // Latest
    // ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ===============================
    // Details
    // ===============================

    override fun mangaDetailsParse(response: Response): SManga = response.asJsoup().extractInertia<MangaDetailsDto>().manga.toSManga()

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.asJsoup().extractInertia<MangaDetailsDto>().manga
        return json.chapters.map { it.toSChapter(json.slug, dateFormat) }
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        // Compatibility for old saved URLs
        val url = if (chapter.url.startsWith("/manga/")) {
            chapter.url.replace("/manga/", "/read/")
        } else {
            chapter.url
        }
        return GET(baseUrl + url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val json = response.asJsoup().extractInertia<ReaderDto>()
        return json.pages.mapIndexed { index, page -> page.toPage(index) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ===============================
    // Utilities
    // ===============================

    private inline fun <reified T> Document.extractInertia(): T {
        val data = selectFirst("div#app")!!.attr("data-page")
        return data.parseAs<InertiaDto<T>>().props
    }
}
