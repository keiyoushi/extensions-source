package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class StoneScape : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(
        "$apiUrl/series/popular?page=$page&period=week&contentType=manhwa&limit=24",
    ).toMangasPage()

    private fun Response.toMangasPage(): MangasPage {
        val result = parseAs<SeriesResponse>()

        val mangas = result.data.map {
            it.toSManga(baseUrl)
        }

        val hasNextPage =
            (result.pagination?.current ?: 1) <
                (result.pagination?.total ?: 1)

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(
        "$apiUrl/series?page=$page&limit=24&contentType=manhwa",
    ).toMangasPage()

    // ============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val typeIndex = url.pathSegments.indexOfFirst {
            it == "series"
        }

        if (typeIndex == -1 || typeIndex + 1 >= url.pathSize) return null

        val slug = url.pathSegments[typeIndex + 1]

        return client.get("$apiUrl/series/by-slug/$slug")
            .parseAs<SeriesDto>()
            .toSManga(baseUrl)
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$apiUrl/series"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("contentType", "manhwa")

        filters.firstInstanceOrNull<StatusFilter>()?.let { filter ->
            if (filter.state != 0) {
                url.addQueryParameter("status", filter.toUriPart())
            }
        }

        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.map { it.slug }
            .orEmpty()
            .toMutableList()

        if (query.isNotEmpty()) {
            val matchedGenre = findGenre(query)
            if (matchedGenre != null) {
                if (matchedGenre.slug !in selectedGenres) {
                    selectedGenres += matchedGenre.slug
                }
            } else {
                url.addQueryParameter("search", query)
            }
        }

        if (selectedGenres.isNotEmpty()) {
            url.addQueryParameter("genres", selectedGenres.joinToString(","))
        }

        return client.get(url.build()).toMangasPage()
    }

    // ============================== Details ==============================

    override fun getMangaUrl(
        manga: SManga,
    ): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringAfterLast("/")

        val details = async {
            if (fetchDetails) {
                client.get("$apiUrl/series/by-slug/$slug")
                    .parseAs<SeriesDto>()
                    .toSMangaDetails(baseUrl)
            } else {
                manga
            }
        }

        val chapterList = async {
            if (fetchChapters) {
                client.get("$apiUrl/series/by-slug/$slug/chapters")
                    .parseAs<ChapterListResponse>()
                    .chapters
                    .map {
                        it.toSChapter(slug)
                    }
                    .reversed()
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(
        chapter: SChapter,
    ): String = "$baseUrl${
        chapter.url.substringBefore("#")
    }"

    // =============================== Pages ===============================

    override suspend fun getPageList(
        chapter: SChapter,
    ): List<Page> {
        val chapterId = chapter.url.substringAfter("#")

        val result = client.get("$apiUrl/chapters/$chapterId/pages")
            .parseAs<ChapterDetailsDto>()

        return result.allPages.mapIndexed { index, page ->
            Page(
                index = if (page.pageNumber > 0) page.pageNumber - 1 else index,
                imageUrl = baseUrl + page.url,
            )
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
        GenreFilter(getGenreList()),
    )
}
