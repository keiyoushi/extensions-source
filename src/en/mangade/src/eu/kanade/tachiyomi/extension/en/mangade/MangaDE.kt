package eu.kanade.tachiyomi.extension.en.mangade

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDE : KeiSource() {

    private val apiUrl = "https://api.mangade.io/api"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    // ===============================
    // Popular
    // ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "most-viewed")
            .build()

        return client.get(url, headers).parseAs<PayloadDto<MangaListPageDto>>().data.toMangasPage()
    }

    // ===============================
    // Latest
    // ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "newest")
            .build()

        return client.get(url, headers).parseAs<PayloadDto<MangaListPageDto>>().data.toMangasPage()
    }

    // ===============================
    // Search
    // ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")

        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genres[]", it.id) }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("comic_status", filter.toUriPart())
                    }
                }
                is TypeFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("category", filter.toUriPart())
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }
                is YearFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("year", filter.toUriPart())
                    }
                }
                is ChapterCountFilter -> {
                    url.addQueryParameter("min_chapter_count", filter.toUriPart())
                }
                else -> {}
            }
        }

        return client.get(url.build(), headers).parseAs<PayloadDto<MangaListPageDto>>().data.toMangasPage()
    }

    // ===============================
    // Details
    // ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfter("mid=")
        val data = client.get("$apiUrl/comics/$id/view", headers).parseAs<PayloadDto<MangaDto>>().data
        return SMangaUpdate(data.toSManga(), data.toSChapterList(dateFormat))
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = "$baseUrl${manga.url}".toHttpUrl()
        val slug = url.pathSegments[0]
        val mid = url.queryParameter("mid")

        return "$baseUrl/comic/$slug-pid$mid"
    }

    // ===============================
    // Chapters
    // ===============================

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val mid = url.queryParameter("mid")
        val mangaSlug = url.pathSegments[0]
        val chapterSlug = url.pathSegments[1]

        return "$baseUrl/comic/$mangaSlug-$mid/$chapterSlug"
    }

    // ===============================
    // Pages
    // ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfter("cid=").substringBefore("&")
        return client.get("$apiUrl/chapters/$id/view", headers).parseAs<PayloadDto<ChapterDto>>().data.toPageList()
    }

    // ===============================
    // Filters
    // ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres?size=500", headers).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            YearFilter(),
            ChapterCountFilter(),
        )

        val genres = data?.parseAs<PayloadDto<GenreListPageDto>>()?.data?.genres.orEmpty()
        if (genres.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                GenreFilter(genres.map { Genre(it.name, it.id) }),
            )
        }

        return FilterList(filters)
    }
}
