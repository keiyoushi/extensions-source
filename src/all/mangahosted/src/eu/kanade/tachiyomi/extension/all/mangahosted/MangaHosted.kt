package eu.kanade.tachiyomi.extension.all.mangahosted

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MangaHosted(private val langOption: LanguageOption) : HttpSource() {

    override val lang = langOption.lang

    override val name: String = "Manga Hosted${langOption.nameSuffix}"

    override val baseUrl: String = "https://mangago.fit/${langOption.infix}"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ================================= Popular ==========================================

    override fun popularMangaRequest(page: Int): Request {
        val maxResult = 24
        return GET("$apiUrl/${langOption.infix}/HomeTopFllow/$maxResult/${page - 1}")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<Pageable<MangaDto>>()
        val mangas = dto.data.map(::mangaParse)
        return MangasPage(
            mangas = mangas,
            hasNextPage = dto.hasNextPage(),
        )
    }

    // ================================= Latest ===========================================

    override fun latestUpdatesRequest(page: Int): Request {
        val maxResult = 24
        val url = "$apiUrl/${langOption.infix}/HomeLastUpdate".toHttpUrl().newBuilder()
            .addPathSegment("$maxResult")
            .addPathSegment("${page - 1}")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ================================= Search ===========================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val maxResult = 20
        val url = "$apiUrl/${langOption.infix}/SeachPage/$maxResult/${page - 1}".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .build()
        return GET(url, headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX)) {
            val url = "$baseUrl/${query.substringAfter(SEARCH_PREFIX)}"
            return client.newCall(GET(url, headers))
                .asObservableSuccess().map { response ->
                    val mangas = try { listOf(mangaDetailsParse(response)) } catch (_: Exception) { emptyList() }
                    MangasPage(mangas, false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        return MangasPage(
            dto.mangas.map(::mangaParse),
            false,
        )
    }

    // ================================= Details ==========================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/${langOption.infix}/getInfoManga".toHttpUrl().newBuilder()
            .addPathSegment(manga.slug())
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDetailsDto>()
        return mangaParse(dto.details)
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url.replace(langOption.infix, langOption.mangaSubstring)
    }

    // ================================= Chapter ==========================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var currentPage = 0
        do {
            val chaptersDto = fetchChapterListPageable(manga, currentPage++)
            chapters += chaptersDto.data.map { chapter ->
                SChapter.create().apply {
                    name = chapter.name
                    date_upload = chapter.date.toDate()
                    url = chapter.toChapterUrl(langOption.infix)
                }
            }
        } while (chaptersDto.hasNextPage())
        return Observable.just(chapters)
    }

    private fun fetchChapterListPageable(manga: SManga, page: Int): Pageable<ChapterDto> {
        val maxResult = 100
        val url = "$apiUrl/${langOption.infix}/GetChapterListFilter/${manga.slug()}/$maxResult/$page/all/${langOption.orderBy}"
        return client.newCall(GET(url, headers)).execute()
            .parseAs<Pageable<ChapterDto>>()
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // ================================= Pages ============================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterSlug = chapter.url.substringAfter(langOption.infix)
        val url = "$apiUrl/${langOption.infix}/GetImageChapter$chapterSlug"
        return GET(url, headers)
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .removeAll("Referer")
            .build()
        return super.imageRequest(page).newBuilder()
            .headers(imageHeaders)
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val location = response.request.url.toString()
        val dto = response.parseAs<PageDto>()
        return dto.pages.mapIndexed { index, url ->
            Page(index, location, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ================================= Utilities =======================================

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun SManga.slug() = this.url.split("/").last()

    private fun mangaParse(dto: MangaDto): SManga {
        return SManga.create().apply {
            title = dto.title
            thumbnail_url = dto.thumbnailUrl
            status = dto.status
            url = "/${dto.slug}"
            genre = dto.genres
            initialized = true
        }
    }

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    companion object {
        const val SEARCH_PREFIX = "slug:"
        val baseApiUrl = "https://api.mangago.fit"
        val apiUrl = "$baseApiUrl/api"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ENGLISH)
    }
}
