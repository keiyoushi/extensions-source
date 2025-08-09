package eu.kanade.tachiyomi.multisrc.zerotheme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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

abstract class ZeroTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    open val cdnUrl: String = "https://cdn.${baseUrl.substringAfterLast("/")}"

    open val imageLocation: String = "images"

    open val mangaSubString: String = "comic"

    open val chapterSubString: String = "chapter"

    private val sourceLocation: String get() = "$cdnUrl/$imageLocation"

    // =========================== Popular ================================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =========================== Latest ===================================

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        MangasPage(response.toDto<LatestDto>().toSMangaList(sourceLocation), hasNextPage = false)

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

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$mangaSubString/${manga.url.substringAfterLast("/")}"

    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response) = response.toDto<MangaDetailsDto>().toSManga(sourceLocation)

    // =========================== Chapter =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$chapterSubString/${chapter.url.substringAfterLast("/")}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = response.toDto<MangaDetailsDto>().toSChapterList()

    // =========================== Pages ===================================

    override fun pageListRequest(chapter: SChapter) = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> =
        response.toDto<PageDto>().toPageList(sourceLocation)

    override fun imageUrlParse(response: Response) = ""

    // =========================== Utilities ===============================

    inline fun <reified T> Response.toDto(): T {
        val jsonString = asJsoup().selectFirst("[data-page]")!!.attr("data-page")
        return jsonString.parseAs<T>()
    }
}
