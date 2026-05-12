package eu.kanade.tachiyomi.extension.ja.mangameets

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class MangaMeets : HttpSource() {
    override val name = "MangaMeets"
    override val baseUrl = "https://manga-meets.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"
    private val pageSize = "20"

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/comics/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "weekly_view_count")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", pageSize)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesResponse>()
        val mangas = result.getComics().map { it.toSManga() }
        return MangasPage(mangas, result.data.attributes.hasNextPage())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/episodes/latest.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", pageSize)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/comics/search.json".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("keywords", query)

            addQueryParameter("size", pageSize)
            addQueryParameter("page", page.toString())

            addFilter("sort", filters.firstInstance<SortFilter>())
            addTagGenreFilter(filters)
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comics/${manga.url}.json", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/comics/${manga.url}/episodes.json", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dirName = response.request.url.pathSegments[2]
        return response.parseAs<ChapterResponse>().data.map { it.attributes.toSChapter(dirName) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comics/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val dirName = parts.pathSegments.first()
        val chapterNr = parts.pathSegments.last()
        return GET("$apiUrl/comics/$dirName/episodes/$chapterNr/viewer.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        return result.episodePages.map {
            Page(it.orderIndex, imageUrl = it.image.originalUrl)
        }
    }

    private var tagGenreList: List<Pair<String, String>> = emptyList()

    private fun fetchTagsAndGenres() {
        if (tagGenreList.isNotEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val tags = client.newCall(GET("$apiUrl/official_tags.json", headers)).execute()
                    .parseAs<GenreResponse>().data
                    .map { Pair(it.attributes.name, "tag:${it.attributes.name}") }

                val genres = client.newCall(GET("$apiUrl/comic_genres.json", headers)).execute()
                    .parseAs<GenreResponse>().data
                    .map { Pair(it.attributes.name, "genre:${it.attributes.name}") }

                tagGenreList = tags + genres
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchTagsAndGenres()

        return if (tagGenreList.isEmpty()) {
            FilterList(
                Filter.Header("Note: Search and active filters are applied together"),
                Filter.Header("Press 'Reset' to load more filters"),
                SortFilter(),
            )
        } else {
            FilterList(
                Filter.Header("Note: Search and active filters are applied together"),
                SortFilter(),
                TagGenreFilter(tagGenreList),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
