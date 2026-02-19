package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class YomuMangas : HttpSource() {

    override val name = "Yomu Mangás"

    override val baseUrl = "https://yomumangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .rateLimitHost(API_URL.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .rateLimitHost(CDN_URL.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
        .build()

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("[class*=styles_Container]:has(h1:contains(capítulos)) [class*=styles_Card]").map { element ->
            SManga.create().apply {
                with(element.selectFirst("a[class*=styles_Title]")!!) {
                    title = text()
                    setUrlWithoutDomain(absUrl("href"))
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val apiUrl = "$API_URL/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())

        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { it.addQueryParameter(apiUrl) }

        return GET(apiUrl.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<YomuMangasSearchDto>()
        val seriesList = result.mangas.map(YomuMangasSeriesDto::toSManga)
        return MangasPage(seriesList, result.hasNextPage)
    }

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$API_URL${manga.url.substringBeforeLast("/")}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<YomuMangasDetailsDto>().manga.toSManga()

    // ================================ Chapters =======================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    private fun chapterListApiRequest(mangaId: Int): Request = GET("$API_URL/mangas/$mangaId/chapters", apiHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.parseAs<YomuMangasDetailsDto>().manga

        return client.newCall(chapterListApiRequest(series.id)).execute()
            .parseAs<YomuMangasChaptersDto>().chapters
            .sortedByDescending(YomuMangasChapterDto::chapter)
            .map { it.toSChapter(series) }
    }

    // ================================ Pages =======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("[class*=reader_Pages] img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ================================ Filters =======================================

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(statusList),
        TypeFilter(typesList),
        NsfwContentFilter(),
        AdultContentFilter(),
        GenreFilter(genresList),
    )

    companion object {
        private const val ACCEPT_JSON = "application/json"

        private const val API_URL = "https://api.yomumangas.com"
        const val CDN_URL = "https://s3.yomumangas.com"
    }
}
