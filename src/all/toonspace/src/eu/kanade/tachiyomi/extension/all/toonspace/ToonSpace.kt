package eu.kanade.tachiyomi.extension.all.toonspace

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ToonSpace : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getToons(toonsUrl(page).addQueryParameter("sort[0]", POPULAR_SORT).build())

    // The series of the latest episodes, newest first
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl("episodes")
            .addQueryParameter("fields[0]", "index")
            .addQueryParameter("populate[toon][fields][0]", "title")
            .addQueryParameter("populate[toon][fields][1]", "urlSlug")
            .addQueryParameter("populate[toon][populate][image][fields][0]", "url")
            .addQueryParameter("sort[0]", "releasedAt:desc")
            .addQueryParameter("pagination[pageSize]", "100")
            .build()
        val toons = client.get(url, Fetch.XHR).parseAs<ListDto<LatestEpisodeDto>>().data
            .mapNotNull { it.toon?.toSManga() }
            .distinctBy { it.url }

        return MangasPage(toons, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = toonsUrl(page).apply {
            if (query.isNotBlank()) addQueryParameter("filters[title][\$containsi]", query.trim())
            filters.firstInstanceOrNull<StatusFilter>()?.value?.let { addQueryParameter("filters[isComplete][\$eq]", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.value?.let { addQueryParameter("filters[genres][type][\$eq]", it) }
            addQueryParameter("sort[0]", filters.firstInstanceOrNull<SortFilter>()?.value ?: POPULAR_SORT)
        }.build()

        return getToons(url)
    }

    private fun toonsUrl(page: Int) = apiUrl("toons")
        .addQueryParameter("fields[0]", "title")
        .addQueryParameter("fields[1]", "urlSlug")
        .addQueryParameter("populate[image][fields][0]", "url")
        .addQueryParameter("pagination[page]", page.toString())
        .addQueryParameter("pagination[pageSize]", PAGE_SIZE.toString())

    // Series made only of videos have no episodes to read
    private suspend fun getToons(url: HttpUrl): MangasPage {
        val result = client.get(url, Fetch.XHR).parseAs<ListDto<ToonDto>>()
        val mangas = result.data.filter { it.episodesCount != 0 }.map { it.toSManga() }

        return MangasPage(mangas, result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val field = when (url.pathSegments[0]) {
            "t" -> "urlSlug"
            "p", "r", "v", "m" -> "documentId"
            else -> return null
        }

        return getToon(field, id)?.toSManga()?.apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/t/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { getToon("urlSlug", manga.url)!!.toSManga() } else null
        val chapterList = if (fetchChapters) async { getChapters(manga.url) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun getToon(field: String, value: String): ToonDto? {
        val url = apiUrl("toons")
            .addQueryParameter("filters[$field][\$eq]", value)
            .addQueryParameter("populate[image][fields][0]", "url")
            .addQueryParameter("populate[genres][fields][0]", "type")
            .build()

        return client.get(url, Fetch.XHR).parseAs<ListDto<ToonDto>>().data.firstOrNull()
    }

    // Episode pages only come with a filter on the episode index
    private suspend fun getChapters(slug: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val url = apiUrl("episodes")
                .addQueryParameter("filters[toon][urlSlug][\$eq]", slug)
                .addQueryParameter("filters[index][\$gte]", "0")
                .addQueryParameter("fields[0]", "index")
                .addQueryParameter("fields[1]", "title")
                .addQueryParameter("fields[2]", "releasedAt")
                .addQueryParameter("populate[pages][fields][0]", "url")
                .addQueryParameter("populate[toon][fields][0]", "documentId")
                .addQueryParameter("populate[toon][fields][1]", "title")
                .addQueryParameter("sort[0]", "index:desc")
                .addQueryParameter("pagination[page]", page++.toString())
                .addQueryParameter("pagination[pageSize]", "100")
                .build()
            val result = client.get(url, Fetch.XHR).parseAs<ListDto<EpisodeDto>>()
            result.data.mapTo(chapters) { it.toSChapter() }
        } while (result.hasNextPage)

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith(LOCK)) return emptyList()

        // Reader link: /r/<series id>/<episode id>/<index>
        val episodeId = getChapterUrl(chapter).toHttpUrl().pathSegments[2]
        val url = apiUrl("episodes")
            .addPathSegment(episodeId)
            .addQueryParameter("populate[pages][fields][0]", "url")
            .build()

        return client.get(url, Fetch.XHR).parseAs<DataDto<EpisodePagesDto>>().data.pages
            .mapIndexed { i, page -> Page(i, imageUrl = page.url) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private fun apiUrl(path: String) = API_URL.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .addQueryParameter("locale", lang)

    companion object {
        private const val API_URL = "https://master.toonspace.io/api"
        private const val PAGE_SIZE = 24
        private const val POPULAR_SORT = "viewsCount:desc"
    }
}
