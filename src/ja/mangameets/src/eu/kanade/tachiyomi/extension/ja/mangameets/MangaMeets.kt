package eu.kanade.tachiyomi.extension.ja.mangameets

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class MangaMeets : KeiSource() {
    private val apiUrl get() = "$baseUrl/api"
    private val pageSize = "20"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/comics/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "weekly_view_count")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", pageSize)
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/episodes/latest.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", pageSize)
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/comics/search.json".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("keywords", query)
            addQueryParameter("size", pageSize)
            addQueryParameter("page", page.toString())
            addFilter("sort", filters.firstInstance<SortFilter>())
            addTagGenreFilter(filters)
        }.build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<SeriesResponse>()
        val mangas = result.toSMangaList()
        return MangasPage(mangas, result.data.attributes.hasNextPage())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/comics/${manga.url}.json").parseAs<DetailsResponse>().toSManga()
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$apiUrl/comics/${manga.url}/episodes.json").parseAs<ChapterResponse>().data
                .map { it.attributes.toSChapter(manga.url) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comics/${chapter.memo["uuid"]!!.string}/${chapter.memo["chapter"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$apiUrl/comics/${chapter.memo["uuid"]!!.string}/episodes/${chapter.memo["chapter"]!!.string}/viewer.json").parseAs<ViewerResponse>()
        return result.episodePages.map {
            Page(it.orderIndex, imageUrl = it.image.originalUrl)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val tags = async {
            client.get("$apiUrl/official_tags.json").parseAs<GenreResponse>().data
                .map { it.attributes.name to "tag:${it.attributes.name}" }
        }

        val genres = async {
            client.get("$apiUrl/comic_genres.json").parseAs<GenreResponse>().data
                .map { it.attributes.name to "genre:${it.attributes.name}" }
        }

        (tags.await() + genres.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tagGenreList = data?.parseAs<List<Pair<String, String>>>().orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                if (tagGenreList.isNotEmpty()) add(TagGenreFilter(tagGenreList))
            },
        )
    }
}
