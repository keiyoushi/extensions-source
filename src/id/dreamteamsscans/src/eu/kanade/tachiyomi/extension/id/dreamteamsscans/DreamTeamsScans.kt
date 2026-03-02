package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class DreamTeamsScans : HttpSource() {

    override val name = "DreamTeams Scans"

    override val baseUrl = "https://dreamteams.space"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    private val apiBaseUrl = "https://api.dreamteams.space/api"

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBaseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("order", "desc")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MangaListDto>()
        val mangas = dto.data.map { it.toSManga() }
        return MangasPage(mangas, dto.page < dto.total_pages)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiBaseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "update")
            .addQueryParameter("order", "desc")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val slug = when {
            query.startsWith(PREFIX_ID_SEARCH) -> query.removePrefix(PREFIX_ID_SEARCH)
            query.startsWith("$baseUrl/comic/") -> query.substringAfter("/comic/").substringBefore("/")
            else -> null
        }

        return if (slug != null) {
            client.newCall(GET("$apiBaseUrl/series/comic/$slug", headers))
                .asObservableSuccess()
                .map { response ->
                    val dto = response.parseAs<MangaDetailsDto>()
                    MangasPage(listOf(dto.toSManga()), false)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiBaseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genre = filter.toUriPart()
                    if (genre.isNotEmpty()) {
                        url.addQueryParameter("genre", genre)
                    }
                }
                is StatusFilter -> {
                    val status = filter.toUriPart()
                    if (status.isNotEmpty()) {
                        url.addQueryParameter("status", status)
                    }
                }
                is TypeFilter -> {
                    val type = filter.toUriPart()
                    if (type.isNotEmpty()) {
                        url.addQueryParameter("comic_type", type)
                    }
                }
                is ColorFilter -> {
                    val color = filter.toUriPart()
                    if (color.isNotEmpty()) {
                        url.addQueryParameter("color_format", color)
                    }
                }
                is ReadingFormatFilter -> {
                    val reading = filter.toUriPart()
                    if (reading.isNotEmpty()) {
                        url.addQueryParameter("reading_format", reading)
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                    url.addQueryParameter("order", "desc")
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBaseUrl/series/comic${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<MangaDetailsDto>()
        val mangaUrl = dto.slug
        return dto.units.map { it.toSChapter(mangaUrl) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBaseUrl/series${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PageListDto>()
        return dto.chapter.pages.map {
            Page(it.page_number - 1, imageUrl = it.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Filters ===============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        Filter.Separator(),
        StatusFilter(),
        TypeFilter(),
        ColorFilter(),
        ReadingFormatFilter(),
        GenreFilter(),
    )

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
