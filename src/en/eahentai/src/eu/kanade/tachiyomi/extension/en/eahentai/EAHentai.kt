package eu.kanade.tachiyomi.extension.en.eahentai

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class EAHentai : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/image"

    override suspend fun getPopularManga(page: Int): MangasPage = fetchPopular(page, SORT_ALL_TIME)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/latest/".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("take", PAGE_SIZE.toString())
            .build()

        return client.get(url, Fetch.XHR).parseAs<AlbumListDto>().toMangasPage(page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: SORT_ALL_TIME
        // The search needs a query: without one, the sort picks the latest or a popular list
        if (query.isBlank()) {
            return if (sort == SORT_LATEST) getLatestUpdates(page) else fetchPopular(page, sort)
        }

        val url = "$apiUrl/search/v2/".toHttpUrl().newBuilder()
            .addQueryParameter("type", filters.firstInstanceOrNull<SearchTypeFilter>()?.value ?: "gallery")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("take", PAGE_SIZE.toString())
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("orderby", sort)
            .build()

        return client.get(url, Fetch.XHR).parseAs<AlbumListDto>().toMangasPage(page)
    }

    private suspend fun fetchPopular(page: Int, sort: String): MangasPage {
        val url = "$apiUrl/popular/".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("take", PAGE_SIZE.toString())
            .addQueryParameter("orderby", sort)
            .build()
        val albums = client.get(url, Fetch.XHR).parseAs<List<AlbumDto>>()

        // This list comes without a page count
        return MangasPage(albums.map { it.toSManga() }, albums.size == PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "a") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null

        return fetchAlbum(id.toString()).toDetailsSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/a/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/a/${chapter.url}/0"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val album = fetchAlbum(manga.url)
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = DATE_FORMAT.tryParseDateTime(album.addDt, ZoneOffset.UTC)
        }

        return SMangaUpdate(album.toDetailsSManga(), listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchAlbum(chapter.url).images
        .sortedBy { it.sort }
        .mapIndexed { index, image -> Page(index, imageUrl = imageUrl(image.imageUri)) }

    private suspend fun fetchAlbum(id: String): AlbumDto = client.get("$apiUrl/album/$id", Fetch.XHR).parseAs<List<AlbumDto>>().first()

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        Filter.Header("Search in only applies to text search"),
        SearchTypeFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 42
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
