package eu.kanade.tachiyomi.multisrc.spicytheme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

abstract class SpicyTheme(
    override val name: String,
    override val baseUrl: String,
    protected val apiBaseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun filterUrlBuilder(
        page: Int,
        orderBy: String = SortFilter.ID_LATEST,
    ): HttpUrl.Builder = "$apiBaseUrl/filtrar".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", PAGE_SIZE.toString())
        .addQueryParameter("orderBy", orderBy)
        .addQueryParameter("sort", "desc")
        .addQueryParameter("gendersId", "")
        .addQueryParameter("origin", "")
        .addQueryParameter("state", "")
        .addQueryParameter("loading", "true")

    override fun popularMangaRequest(page: Int): Request {
        val url = filterUrlBuilder(page, SortFilter.ID_POPULAR).build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = response.parseAs<FilterResponseDto>().toMangasPage()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length < 2) {
                throw Exception("Escribe al menos 2 caracteres para buscar")
            }
            val url = "$apiBaseUrl/home/buscar".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()

            return GET(url, headers)
        }

        val url = filterUrlBuilder(page)
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.setQueryParameter("orderBy", filter.toUriPart())
                    url.setQueryParameter("sort", filter.getSortDirection())
                }

                is UriMultiSelectFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) {
                        url.setQueryParameter(filter.queryParameter, filter.toUriPart())
                    }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isTextSearch = response.request.url.queryParameter("query") != null
        if (isTextSearch) {
            val result = response.parseAs<List<MangaDto>>()
            return MangasPage(
                mangas = result.map { it.toSManga() },
                hasNextPage = false,
            )
        }

        return response.parseAs<FilterResponseDto>().toMangasPage()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = filterUrlBuilder(page, SortFilter.ID_LATEST).build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBaseUrl/serie/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesResponseDto>()
        return result.series.toSMangaDetails()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<SeriesResponseDto>().series
        return result.chapters.orEmpty().map {
            it.toSChapter(result.slug, dateFormat)
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBaseUrl/serie/${chapter.url}/")

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PagesResponseDto>()
        val pages = result.pages.rawImages.parseAs<List<String>>()

        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros no se aplican a la búsqueda por texto"),
        SortFilter(),
        Filter.Separator(),
        OriginFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic/${chapter.url}"

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val PAGE_SIZE = 12
    }
}
