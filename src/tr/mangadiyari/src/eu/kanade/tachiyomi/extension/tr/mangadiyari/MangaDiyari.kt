package eu.kanade.tachiyomi.extension.tr.mangadiyari

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaDiyari : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/series/latest-updates".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()

        val response = client.get(url).parseAs<LatestUpdatesResponse>()
        return MangasPage(response.updates.map { it.toSManga(baseUrl) }, response.hasMore)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "latest"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
        return seriesPage(page, query.trim(), sort, type, status, genres)
    }

    private suspend fun seriesPage(
        page: Int,
        query: String = "",
        sort: String,
        type: String = "",
        status: String = "",
        genres: String = "",
    ): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            if (genres.isNotBlank()) {
                addQueryParameter("genre", genres)
            }
            addQueryParameter("sort", sort)
            if (status.isNotBlank()) {
                addQueryParameter("status", status)
            }
            if (type.isNotBlank()) {
                addQueryParameter("type", type)
            }
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("page", page.toString())
        }.build()

        val response = client.get(url).parseAs<SeriesListResponse>()
        return MangasPage(response.series.map { it.toSManga(baseUrl) }, response.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        if (url.pathSegments.getOrNull(0)?.lowercase() !in listOf("series", "seri")) {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/seri/${chapter.memo.getString("slug")}/bolum/${chapter.memo.getString("number")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/api/series/${manga.url}").parseAs<SeriesDetailsResponse>()
        return SMangaUpdate(
            details.series.toSManga(baseUrl),
            details.chapters
                .sortedByDescending { it.chapterNumber }
                .map { it.toSChapter(details.series.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/chapters/${chapter.url}").parseAs<ChapterPagesResponse>()
        return response.pages
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page ->
                Page(index, url = getChapterUrl(chapter), imageUrl = baseUrl.resolveImage(page.displayImage))
            }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 24
    }
}
