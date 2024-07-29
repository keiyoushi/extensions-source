package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Atsumaru : HttpSource() {

    override val name = "Atsumaru"

    override val baseUrl = "https://atsu.moe"
    private val apiUrl = "$baseUrl/api/v1"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", apiUrl.toHttpUrl().host)
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    private val json: Json by injectLazy()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/layouts/s1/sliders/hotUpdates", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseMangaDto>().items

        return MangasPage(data.map { it.manga.toSManga() }, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/layouts/s1/latest-updates", apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .build()

        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResultsDto>().hits

        return MangasPage(data.map { it.info.toSManga() }, false)
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(apiUrl + manga.url, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaObjectDto>().manga.toSManga()
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = response.parseAs<MangaObjectDto>().manga.chapters!!.map {
            it.toSChapter(response.request.url.pathSegments.last())
        }

        return chapterList.sortedWith(
            compareBy(
                { it.chapter_number },
                { it.scanlator },
            ),
        ).reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/s1/$slug/$name/1"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, name) = chapter.url.split("/")
        return GET("$apiUrl/manga/s1/$slug#$name", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<MangaObjectDto>().manga.chapters!!.first {
            it.name == response.request.url.fragment
        }

        return chapter.pages.map { page ->
            Page(page.name.toInt(), imageUrl = page.pageURLs.first())
        }.sortedBy { it.index }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
