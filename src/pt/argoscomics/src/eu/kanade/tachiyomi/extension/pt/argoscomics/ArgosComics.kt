package eu.kanade.tachiyomi.extension.pt.argoscomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArgosComics : HttpSource() {

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3, 2.seconds)
        .build()

    private val rscHeaders by lazy {
        headersBuilder().set("rsc", "1").build()
    }

    // ======================== Popular =============================

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("projetos")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = response.extractNextJs<MangasListDto>()!!.toMangasPage()

    // ======================== Latest =============================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, rscHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = response.extractNextJs<LatestMangas>()!!.toMangasPage()

    // ======================== Search =============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchHeaders = headers.newBuilder()
            .set("Next-Action", SEARCH_TOKEN)
            .build()
        val payload = listOf(query).toJsonRequestBody()
        return POST(baseUrl, searchHeaders, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.extractNextJs<List<MangaDto>>() ?: emptyList()
        return MangasPage(dto.map(MangaDto::toSManga), false)
    }

    // ======================== Details =============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = getMangaUrl(manga)
        val payload = url.toHttpUrl().pathSegments.toJsonRequestBody()
        val detailsHeaders = headers.newBuilder()
            .set("Next-Action", DETAILS_TOKEN)
            .build()
        return POST(url, detailsHeaders, payload)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.extractNextJs<MangaDetailsDto>()!!.toSManga()

    // ======================== Chapters =============================

    override fun chapterListRequest(manga: SManga): Request {
        val chaptersHeaders = headers.newBuilder()
            .set("Next-Action", CHAPTERS_TOKEN)
            .build()
        return mangaDetailsRequest(manga).newBuilder()
            .headers(chaptersHeaders)
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pathSegment = response.request.url.toString().substringAfter(baseUrl)
        return response.extractNextJs<VolumeChapterDto>()!!.toChapterList(pathSegment)
    }

    // ======================== Pages =============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = getChapterUrl(chapter).toHttpUrl().pathSegments
        val payload = listOf(segments.first(), segments.last()).toJsonRequestBody()
        val pagesHeaders = headers.newBuilder()
            .set("Next-Action", PAGES_TOKEN)
            .build()
        return POST(getChapterUrl(chapter), pagesHeaders, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (response.request.url.encodedPath == "/login") error("Acesse sua conta")

        val body = response.body.string()
        val dto = body.extractNextJsRsc<MangaDetailsDto>()
        if (dto?.isUpcoming == true) error("Capítulo em desenvolvimento")
        return body.extractNextJsRsc<PagesDto>()!!.toPageList()
    }

    override fun imageUrlParse(response: Response) = ""

    companion object {
        private const val SEARCH_TOKEN = "40d9f16929718dd0e02ec0bcdc2393de860707fa79"
        private const val CHAPTERS_TOKEN = "607bcd9f90d5db5edaa2cf1aff7a002b5b14ead30a"
        private const val DETAILS_TOKEN = "60d532a2a6a7a0ff42de5f69dcdf2db5860a2f76b0"
        private const val PAGES_TOKEN = "60390ae612bb67d3d0614b47c7fa396fa4201aa323"
    }
}
