package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaScan : HttpSource() {

    override val name = "Manhwa Scan"

    override val baseUrl = "https://manhwascans.lat"

    override val lang = "es"

    override val supportsLatest = true

    override val id: Long = 7172992930543738693

    private val apiUrl = "$baseUrl/api"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "*/*")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/series?page=$page&limit=48&sort=views&q=", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<BrowseResponse>()
        val mangas = dto.series.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/series?page=$page&limit=48&sort=updated&q=", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "48")
            addQueryParameter("q", query.trim())

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            addQueryParameter("sort", sortFilter?.toUriPart()?.takeIf { it.isNotEmpty() } ?: "updated")

            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("genre", it)
            }

            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("status", it)
            }

            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("type", it)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String = if (isNewFormat(manga.url)) {
        "$baseUrl/manga/${getMangaSlug(manga.url)}/"
    } else {
        "$baseUrl${manga.url}"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!isNewFormat(manga.url)) {
            return GET("$apiUrl/series?q=${manga.title}", headers)
        }
        return GET("$apiUrl/series/${getMangaId(manga.url)}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (isMigrationRequest(response)) {
            val browseDto = response.parseAs<BrowseResponse>()
            val query = response.request.url.queryParameter("q") ?: ""
            val match = findSeriesMatch(browseDto, query)

            return client.newCall(GET("$apiUrl/series/${match.id}", headers)).execute().use { detailsRes ->
                detailsRes.parseAs<DetailsResponse>().series.toSManga(baseUrl)
            }
        }

        return response.parseAs<DetailsResponse>().series.toSManga(baseUrl)
    }

    // ============================= Chapters ==============================
    override fun getChapterUrl(chapter: SChapter): String = if (isNewFormat(chapter.url)) {
        "$baseUrl/manga/${chapter.url.substringAfter("#")}"
    } else {
        "$baseUrl${chapter.url}"
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (!isNewFormat(manga.url)) {
            return GET("$apiUrl/series?q=${manga.title}", headers)
        }
        return GET("$apiUrl/series/${getMangaId(manga.url)}/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (isMigrationRequest(response)) {
            val browseDto = response.parseAs<BrowseResponse>()
            val query = response.request.url.queryParameter("q") ?: ""
            val match = findSeriesMatch(browseDto, query)

            return client.newCall(GET("$apiUrl/series/${match.id}/chapters", headers)).execute().use { chaptersRes ->
                parseChaptersResponse(chaptersRes)
            }
        }

        return parseChaptersResponse(response)
    }

    private fun parseChaptersResponse(response: Response): List<SChapter> = response.parseAs<ChaptersResponse>().chapters.map { it.toSChapter(dateFormat) }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        if (!isNewFormat(chapter.url)) {
            throw Exception("Por favor, actualiza la lista de capítulos para leer este capítulo.")
        }
        val chapterId = chapter.url.substringBefore("#")
        return GET("$apiUrl/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<PagesResponse>().pages

        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.toPageImageUrl(baseUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
        TypeFilter(),
    )

    // ============================= Utilities =============================
    private fun isNewFormat(url: String) = url.contains("#")

    private fun getMangaId(url: String) = url.substringBefore("#")

    private fun getMangaSlug(url: String) = url.substringAfter("#")

    private fun isMigrationRequest(response: Response) = response.request.url.queryParameter("q") != null

    private fun findSeriesMatch(browseDto: BrowseResponse, query: String): SeriesDto = browseDto.series.find { it.title.contains(query, ignoreCase = true) }
        ?: throw Exception("No se pudo migrar el manga '$query'. Búscalo de nuevo.")
}
