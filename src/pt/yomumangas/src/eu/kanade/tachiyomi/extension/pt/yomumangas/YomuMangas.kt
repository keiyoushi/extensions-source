package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
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

    private val json: Json by injectLazy()

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$API_URL/mangas/home", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<YomuMangasHomeDto>()
        val seriesList = result.votes.map(YomuMangasSeriesDto::toSManga)

        return MangasPage(seriesList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<YomuMangasHomeDto>()
        val seriesList = result.updates.map(YomuMangasSeriesDto::toSManga)

        return MangasPage(seriesList, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val apiUrl = "$API_URL/mangas/search".toHttpUrl().newBuilder()
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

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url
            .substringAfter("/manga/")
            .substringBefore("/")

        return GET("$API_URL/mangas/$id", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<YomuMangasDetailsDto>().manga.toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    private fun chapterListApiRequest(mangaId: Int): Request {
        return GET("$API_URL/mangas/$mangaId/chapters", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.parseAs<YomuMangasDetailsDto>().manga

        return client.newCall(chapterListApiRequest(series.id)).execute()
            .parseAs<YomuMangasChaptersDto>().chapters
            .sortedByDescending(YomuMangasChapterDto::chapter)
            .map { it.toSChapter(series) }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val urlParts = chapter.url.split("/", "#")
        val seriesId = urlParts[2]
        val chapterNumber = urlParts[6]

        return GET("$API_URL/mangas/$seriesId/chapters/$chapterNumber", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<YomuMangasChapterDetailsDto>()
            .chapter.images.orEmpty()
            .mapIndexed { i, image -> Page(i, "", "$CDN_URL/${image.uri}") }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(getStatusList()),
        TypeFilter(getTypesList()),
        NsfwContentFilter(),
        AdultContentFilter(),
        GenreFilter(getGenresList()),
    )

    private fun getStatusList(): List<Status> = listOf(
        Status("Todos", ""),
        Status("Lançando", "RELEASING"),
        Status("Finalizado", "FINISHED"),
        Status("Cancelado", "CANCELLED"),
        Status("Hiato", "HIATUS"),
        Status("Não lançado", "NOT_YET_RELEASED"),
        Status("Traduzindo", "TRANSLATING"),
        Status("Desconhecido", "UNKNOWN"),
    )

    private fun getTypesList(): List<Type> = listOf(
        Type("Todos", ""),
        Type("Mangá", "MANGA"),
        Type("Manhwa", "MANHWA"),
        Type("Mangá em hiato", "MANGA_HIATUS"),
        Type("Webcomic", "WEBCOMIC"),
        Type("Webtoon", "WEBTOON"),
        Type("Hentai", "HENTAI"),
        Type("Doujinshi", "DOUJIN"),
        Type("One-shot", "ONESHOT"),
    )

    private fun getGenresList(): List<Genre> = listOf(
        Genre("Ação", "1"),
        Genre("Aventura", "8"),
        Genre("Comédia", "2"),
        Genre("Drama", "3"),
        Genre("Ecchi", "15"),
        Genre("Esportes", "14"),
        Genre("Fantasia", "6"),
        Genre("Hentai", "19"),
        Genre("Horror", "4"),
        Genre("Mahou shoujo", "18"),
        Genre("Mecha", "17"),
        Genre("Mistério", "7"),
        Genre("Música", "16"),
        Genre("Psicológico", "9"),
        Genre("Romance", "13"),
        Genre("Sci-fi", "11"),
        Genre("Slice of life", "10"),
        Genre("Sobrenatural", "5"),
        Genre("Suspense", "12"),
    )

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json"

        private const val API_URL = "https://api.yomumangas.com"
        const val CDN_URL = "https://images.yomumangas.com"
    }
}
