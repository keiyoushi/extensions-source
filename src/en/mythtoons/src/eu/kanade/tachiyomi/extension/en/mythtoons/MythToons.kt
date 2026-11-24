package eu.kanade.tachiyomi.extension.en.mythtoons

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MythToons : KeiSource() {

    override val supportsFilterFetching = true

    // The site's view ranking also holds removed series, so the catalogue is sorted by it
    override suspend fun getPopularManga(page: Int): MangasPage = coroutineScope {
        val views = async {
            val url = apiUrl("manga-views")
                .addQueryParameter("limit", "1000")
                .addQueryParameter("orderBy", "view_count")
                .build()
            client.get(url, Fetch.XHR).parseAs<List<ViewsDto>>().associate { it.id to it.viewCount }
        }
        val catalogue = fetchCatalogue()
        val counts = views.await()
        MangasPage(catalogue.sortedByDescending { counts[it.id] ?: 0 }.map { it.toSManga(baseHttpUrl) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl("manga-list")
            .addQueryParameter("limit", (PAGE_SIZE + 1).toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .build()
        val list = client.get(url, Fetch.XHR).parseAs<List<MangaDto>>()
        // The entry after the page tells whether there is a next one
        val mangas = list.take(PAGE_SIZE).filterNot { it.isNovel }.map { it.toSManga(baseHttpUrl) }
        return MangasPage(mangas, list.size > PAGE_SIZE)
    }

    // The site's search stops at 50 results and has no filters: the catalogue comes whole, and is filtered here
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        val mangas = fetchCatalogue()
            .filter { it.matches(query.trim(), status, genre) }
            .map { it.toSManga(baseHttpUrl) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseHttpUrl.host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val slug = when (url.pathSegments[0]) {
            "manga" -> id
            // A chapter's address is its own id: its page names the series
            "read" -> fetchReader(baseHttpUrl.newBuilder().addPathSegment("read").addPathSegment(id).build()).mangaId
            else -> return null
        }
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(getMangaUrl(manga), Fetch.DOCUMENT).extractNextJs<DetailsPageDto>()!!.dataDetails
        return SMangaUpdate(details.toSManga(baseHttpUrl), details.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = fetchReader(getChapterUrl(chapter).toHttpUrl())
        // The images of a locked chapter answer 403 until it is bought
        if (reader.isLocked) return emptyList()
        return reader.pages.mapIndexed { index, path -> Page(index, imageUrl = baseHttpUrl.resolve(path)!!.toString()) }
    }

    override suspend fun fetchFilterData(): JsonElement = fetchCatalogue()
        .flatMap { it.genres }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<String>>())

    // Novels are text only
    private suspend fun fetchCatalogue(): List<MangaDto> = client.get(apiUrl("manga-list").build(), Fetch.XHR)
        .parseAs<List<MangaDto>>()
        .filterNot { it.isNovel }

    private suspend fun fetchReader(url: HttpUrl): ReaderDto = client.get(url, Fetch.DOCUMENT).extractNextJs<ReaderPageDto>()!!.dataRead

    private fun apiUrl(endpoint: String) = baseHttpUrl.newBuilder().addPathSegment("api").addPathSegment(endpoint)

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    companion object {
        private const val PAGE_SIZE = 50
    }
}
