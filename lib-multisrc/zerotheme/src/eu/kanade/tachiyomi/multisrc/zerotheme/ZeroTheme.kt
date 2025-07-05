package eu.kanade.tachiyomi.multisrc.zerotheme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

abstract class ZeroTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    open val cdnUrl: String = "https://cdn.${baseUrl.substringAfterLast("/")}"

    override val supportsLatest = false

    // =========================== Popular ================================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =========================== Latest ===================================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // =========================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val mangas = dto.mangas.map { it.toSManga(sourceLocation) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // =========================== Details =================================

    override fun mangaDetailsParse(response: Response) = response.toDto<MangaDetailsDto>().toSManga(sourceLocation)

    // =========================== Chapter =================================

    override fun chapterListParse(response: Response) = response.toDto<MangaDetailsDto>().toSChapterList()

    // =========================== Pages ===================================

    open val imageLocation: String = "images"

    private val sourceLocation: String by lazy { "$cdnUrl/$imageLocation" }

    override fun pageListParse(response: Response): List<Page> =
        response.toDto<PageDto>().toPageList(sourceLocation)

    override fun imageUrlParse(response: Response) = ""

    // =========================== Utilities ===============================

    inline fun <reified T> Response.toDto(): T {
        val jsonString = asJsoup().selectFirst("[data-page]")!!.attr("data-page")
        return jsonString.parseAs<T>()
    }

    companion object {
        const val CDN_URL = "https://cdn.egotoons.com"
    }
}
