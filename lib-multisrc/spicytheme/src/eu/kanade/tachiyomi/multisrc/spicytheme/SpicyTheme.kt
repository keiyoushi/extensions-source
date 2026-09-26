package eu.kanade.tachiyomi.multisrc.spicytheme

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class SpicyTheme : KeiSource() {

    protected open val apiBaseUrl: String
        get() = baseUrl.replace("https://", "https://api.")

    override val supportsLatest = true

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

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(filterUrlBuilder(page, SortFilter.ID_POPULAR).build())
        .parseAs<FilterResponseDto>().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            if (query.length < 2) {
                throw Exception("Escribe al menos 2 caracteres para buscar")
            }
            val url = "$apiBaseUrl/home/buscar".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()

            val result = client.get(url).parseAs<List<MangaDto>>()
            return MangasPage(
                mangas = result.map { it.toSManga() },
                hasNextPage = false,
            )
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

        return client.get(url.build()).parseAs<FilterResponseDto>().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(filterUrlBuilder(page, SortFilter.ID_LATEST).build())
        .parseAs<FilterResponseDto>().toMangasPage()

    // details and chapters come from the same endpoint
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get("$apiBaseUrl/serie/${manga.url}").parseAs<SeriesResponseDto>().series
        return SMangaUpdate(
            series.toSMangaDetails(),
            series.chapters.orEmpty().map { it.toSChapter(series.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$apiBaseUrl/serie/${chapter.url}/", Headers.headersOf()).parseAs<PagesResponseDto>()
        val pages = result.pages.rawImages.parseAs<List<String>>()

        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Los filtros no se aplican a la búsqueda por texto"),
        SortFilter(),
        Filter.Separator(),
        OriginFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic/${chapter.url}"

    companion object {
        private const val PAGE_SIZE = 12
    }
}
