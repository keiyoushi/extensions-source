package eu.kanade.tachiyomi.extension.en.weebdex

import eu.kanade.tachiyomi.extension.en.weebdex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.en.weebdex.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.en.weebdex.dto.MangaDto
import eu.kanade.tachiyomi.extension.en.weebdex.dto.MangaListDto
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeebDex : HttpSource() {
    override val name = "WeebDex"
    override val baseUrl = "https://weebdex.org"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(WeebDexConstants.rateLimitUsed)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
        .add("Referer", "$baseUrl/")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // -------------------- Popular --------------------

    override fun popularMangaRequest(page: Int): Request {
        val url = WeebDexConstants.apiMangaUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .build()
        return GET(url.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val mangaListDto = json.decodeFromString<MangaListDto>(body)
        val mangas = mangaListDto.data.map { mangaFromDto(it) }
        return MangasPage(mangas, mangaListDto.hasNextPage)
    }

    // -------------------- Latest --------------------
    override fun latestUpdatesRequest(page: Int): Request {
        val url = WeebDexConstants.apiMangaUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "updatedAt")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Search --------------------
    override fun getFilterList(): FilterList = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = WeebDexConstants.apiMangaUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is TagList -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                WeebDexConstants.tags[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tag", tagId)
                                }
                            }
                        }
                    }
                    is TagsExcludeFilter -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                WeebDexConstants.tags[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tagx", tagId)
                                }
                            }
                        }
                    }
                    is TagModeFilter -> urlBuilder.addQueryParameter("tmod", filter.state.toString())
                    is TagExcludeModeFilter -> urlBuilder.addQueryParameter("txmod", filter.state.toString())
                    else -> { /* Do Nothing */ }
                }
            }
        }

        // Separated explicity to be applied even when a search query is applied.
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> urlBuilder.addQueryParameter("sort", filter.selected)
                is OrderFilter -> urlBuilder.addQueryParameter("order", filter.selected)
                is StatusFilter -> filter.selected?.let { urlBuilder.addQueryParameter("status", it) }
                is DemographicFilter -> filter.selected?.let { urlBuilder.addQueryParameter("demographic", it) }
                is ContentRatingFilter -> filter.selected?.let { urlBuilder.addQueryParameter("contentRating", it) }
                is LangFilter -> filter.query?.let { urlBuilder.addQueryParameter("lang", it) }
                is HasChaptersFilter -> if (filter.state) urlBuilder.addQueryParameter("hasChapters", "1")
                is YearFromFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearFrom", it) }
                is YearToFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearTo", it) }
                else -> { /* Do Nothing */ }
            }
        }

        return GET(urlBuilder.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Manga details --------------------

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("${WeebDexConstants.apiUrl}${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val manga = json.decodeFromString<MangaDto>(body)

        return SManga.create().apply {
            title = manga.title
            description = manga.description
            status = parseStatus(manga.status)
            thumbnail_url = buildCoverUrl(manga.id, manga.relationships?.cover)

            manga.relationships?.let { rel ->
                author = rel.authors.joinToString(", ") { it.name }
                artist = rel.artists.joinToString(", ") { it.name }
                genre = rel.tags.joinToString(", ") { it.name }
            }
        }
    }

    // -------------------- Chapters --------------------

    override fun chapterListRequest(manga: SManga): Request {
        // chapter list is paginated; get all pages
        return GET("${WeebDexConstants.apiUrl}${manga.url}/chapters?order=desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val chapters = mutableListOf<SChapter>()

        // Recursively parse pages
        fun parsePage(chapterListDto: ChapterListDto) {
            chapterListDto.data.forEach { ch ->
                val s = SChapter.create().apply {
                    url = "/chapter/${ch.id}"
                    val chapTitle = ch.title
                    name = if (chapTitle.isNullOrBlank()) "Chapter ${ch.chapter}" else chapTitle
                    date_upload = parseDate(ch.published_at)
                    scanlator = ch.relationships?.groups?.joinToString(", ") { it.name }
                }
                chapters.add(s)
            }

            if (chapterListDto.hasNextPage) {
                val nextUrl = response.request.url.newBuilder()
                    .setQueryParameter("page", (chapterListDto.page + 1).toString())
                    .build()
                val nextResponse = client.newCall(GET(nextUrl, headers)).execute()
                val nextChapterListDto = json.decodeFromString<ChapterListDto>(nextResponse.body.string())
                parsePage(nextChapterListDto)
            }
        }

        parsePage(json.decodeFromString<ChapterListDto>(body))
        return chapters
    }

    // -------------------- Pages --------------------

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("${WeebDexConstants.apiUrl}${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val chapter = json.decodeFromString<ChapterDto>(body)
        val pagesArray = chapter.data_optimized ?: chapter.data ?: emptyList()
        val pages = mutableListOf<Page>()

        pagesArray.forEachIndexed { index, pageData ->
            // pages in spec have 'name' field and images served from /data/{id}/{filename}
            val filename = pageData.name
            val chapterId = chapter.id
            val imageUrl = if (!filename.isNullOrBlank() && chapterId.isNotBlank()) {
                "${WeebDexConstants.cdnDataUrl}/$chapterId/$filename"
            } else {
                ""
            }
            pages.add(Page(index, imageUrl = imageUrl))
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // -------------------- Utilities --------------------

    private fun mangaFromDto(manga: MangaDto): SManga {
        return SManga.create().apply {
            title = manga.title
            url = "/manga/${manga.id}"
            thumbnail_url = buildCoverUrl(manga.id, manga.relationships?.cover)
            description = manga.description
        }
    }

    private fun buildCoverUrl(mangaId: String, cover: eu.kanade.tachiyomi.extension.en.weebdex.dto.CoverDto?): String? {
        if (cover == null) return null
        val ext = cover.ext
        return "${WeebDexConstants.cdnCoverUrl}/$mangaId/${cover.id}$ext"
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            // ISO8601 example: 2025-10-16T12:34:56.000Z
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                // fallback: 2024-03-22 17:03:52
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(dateStr)?.time ?: 0L
            } catch (e2: Exception) {
                try {
                    // fallback: unix timestamp in seconds
                    val sec = dateStr.toLong()
                    sec * 1000
                } catch (e3: Exception) { 0L }
            }
        }
    }
}
