package eu.kanade.tachiyomi.extension.tr.mangaportali

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaPortali : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesPage(page, sort = "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "new"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
        val tag = filters.firstInstanceOrNull<TagFilter>()?.toUriPart().orEmpty()
        return seriesPage(page, query.trim(), sort, type, status, genre, tag)
    }

    private suspend fun seriesPage(
        page: Int,
        query: String = "",
        sort: String,
        type: String = "",
        status: String = "",
        genre: String = "",
        tag: String = "",
    ): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("sort", sort)
            if (type.isNotBlank()) {
                addQueryParameter("type", type)
            }
            if (status.isNotBlank()) {
                addQueryParameter("status", status)
            }
            if (genre.isNotBlank()) {
                addQueryParameter("genre", genre)
            }
            if (tag.isNotBlank()) {
                addQueryParameter("tag", tag)
            }
            addQueryParameter("page", page.toString())
        }.build()

        val response = client.get(url).parseAs<PagedResponse<SeriesDto>>()
        return MangasPage(response.items.map { it.toSManga() }, response.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        if (url.pathSegments.getOrNull(0) != "series") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.memo.getString("series")}/${chapter.memo.getString("slug")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl/api/series/${manga.url}").parseAs<SeriesDto>().toSManga()
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) fetchChapters(manga.url) else chapters
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchChapters(slug: String): List<SChapter> = buildList {
        var page = 1
        do {
            val url = "$baseUrl/api/series/$slug/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", CHAPTER_PAGE_SIZE.toString())
                .addQueryParameter("page", page.toString())
                .build()
            val response = client.get(url).parseAs<PagedResponse<ChapterDto>>()
            response.items
                .filterNot { it.isEarlyAccessLocked }
                .mapTo(this) { it.toSChapter(slug) }
            page++
        } while (response.hasNextPage)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/chapters/${chapter.url}").parseAs<ChapterPagesResponse>()
        return response.pages
            .sortedBy { it.index }
            .mapIndexed { index, page ->
                Page(index, url = getChapterUrl(chapter), imageUrl = page.imageUrl)
            }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
        TagFilter(),
    )

    companion object {
        private const val CHAPTER_PAGE_SIZE = 100
    }
}
