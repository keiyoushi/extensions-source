package eu.kanade.tachiyomi.extension.pt.animy

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Animy : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v1"

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/mangas/meta/genres", Fetch.XHR).parseAs<GenresDto>().genres.toJsonElement()

    override suspend fun getPopularManga(page: Int): MangasPage = listMangas(page) {
        addQueryParameter("sort", "popular")
    }

    // "released" order shows the titles with the newest chapter uploads first
    override suspend fun getLatestUpdates(page: Int): MangasPage = listMangas(page) {
        addQueryParameter("sort", "released")
        addQueryParameter("released", "1")
    }

    private suspend fun listMangas(
        page: Int,
        params: okhttp3.HttpUrl.Builder.() -> Unit,
    ): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            params()
        }.build()

        val response = client.get(url, Fetch.XHR).parseAs<MangaListResponse>()
        val offset = (page - 1) * PAGE_SIZE
        return MangasPage(response.items.map { it.toSManga() }, offset + response.items.size < response.total)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = listMangas(page) {
        addQueryParameter("search", query)
        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(this)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            TypeFilter(),
            StatusFilter(),
            GenreFilter(genres),
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.title
            .normalize()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return "$baseUrl/manga/$slug/"
    }

    private fun String.normalize(): String {
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "").lowercase()
    }

    // Details endpoint already includes the full chapter list
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$apiUrl/mangas/${manga.url}", Fetch.XHR).parseAs<MangaDetails>()
        return SMangaUpdate(
            details.toSManga(),
            details.chapters.map { it.toSChapter(manga.url.toLong()) }.asReversed(),
        )
    }

    // Site URLs are /manga/{slug}/; the API keys on the numeric id, resolved via the slug endpoint
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return client.get("$apiUrl/mangas/slug/$slug", Fetch.XHR).parseAs<MangaRef>().toSManga()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaId, number) = chapter.url.split("/")
        return client.get("$apiUrl/mangas/$mangaId/chapters/$number", Fetch.XHR)
            .parseAs<ChapterDetails>()
            .toPageList()
    }

    companion object {
        private const val PAGE_SIZE = 24
    }
}
