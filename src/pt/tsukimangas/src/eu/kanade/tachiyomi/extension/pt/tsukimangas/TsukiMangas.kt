package eu.kanade.tachiyomi.extension.pt.tsukimangas

import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.CompleteMangaDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.MangaListDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.PageListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class TsukiMangas : HttpSource() {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsuki-mangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/v2/mangas?page=$page&filter=0", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val item = response.parseAs<MangaListDto>()
        val mangas = item.data.map {
            SManga.create().apply {
                url = "/obra" + it.entryPath
                thumbnail_url = baseUrl + it.imagePath
                title = it.title
            }
        }
        val hasNextPage = item.page < item.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    // Yes, "lastests". High IQ move.
    // Also yeah, there's a "?format=0" glued to the page number. Without this,
    // the request will blow up with a HTTP 500.
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/v2/home/lastests?page=$page%3Fformat%3D0", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/api/v2/mangas/$id", headers))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun getFilterList() = TsukiMangasFilters.FILTER_LIST

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = TsukiMangasFilters.getSearchParameters(filters)
        val url = "$baseUrl/api/v2/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("title", query.trim())
            .addIfNotBlank("filter", params.filter)
            .addIfNotBlank("format", params.format)
            .addIfNotBlank("status", params.status)
            .addIfNotBlank("adult_content", params.adult)
            .apply {
                params.genres.forEach { addQueryParameter("genres[]", it) }
                params.tags.forEach { addQueryParameter("tags[]", it) }
            }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.getMangaId()
        return GET("$baseUrl/api/v2/mangas/$id", headers)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val mangaDto = response.parseAs<CompleteMangaDto>()
        url = "/obra" + mangaDto.entryPath
        thumbnail_url = baseUrl + mangaDto.imagePath
        title = mangaDto.title
        artist = mangaDto.staff
        genre = mangaDto.genres.joinToString { it.genre }
        status = parseStatus(mangaDto.status.orEmpty())
        description = buildString {
            mangaDto.synopsis?.also { append("$it\n\n") }
            if (mangaDto.titles.isNotEmpty()) {
                append("Títulos alternativos: ${mangaDto.titles.joinToString { it.title }}")
            }
        }
    }

    private fun parseStatus(status: String) = when (status) {
        "Ativo" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.getMangaId()
        return GET("$baseUrl/api/v2/chapters/$id/all", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val parsed = response.parseAs<ChapterListDto>()

        return parsed.chapters.reversed().map {
            SChapter.create().apply {
                name = "Capítulo ${it.number}"
                // Sometimes the "number" attribute have letters or other characters,
                // which could ruin the automatic chapter number recognition system.
                chapter_number = it.number.trim { char -> !char.isDigit() }.toFloatOrNull() ?: 1F

                url = "/api/v2/chapter/versions/${it.id}"

                date_upload = it.created_at.orEmpty().toDate()
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListDto>()
        return data.pages.sortedBy { it.url.substringAfterLast("/") }.mapIndexed { index, item ->
            val host = when (item.server) {
                1 -> "$MAIN_CDN/tsuki"
                else -> SECONDARY_CDN
            }

            Page(index, imageUrl = host + item.url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun HttpUrl.Builder.addIfNotBlank(query: String, value: String): HttpUrl.Builder {
        if (value.isNotBlank()) addQueryParameter(query, value)
        return this
    }

    private fun String.getMangaId() = substringAfter("/obra/").substringBefore("/")

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        const val PREFIX_SEARCH = "id:"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }

        private const val MAIN_CDN = "https://cdn.tsuki-mangas.com"
        private const val SECONDARY_CDN = "https://cdn2.tsuki-mangas.com"
    }
}
