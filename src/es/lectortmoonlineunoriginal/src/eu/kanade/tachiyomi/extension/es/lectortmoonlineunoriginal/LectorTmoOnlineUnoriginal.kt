package eu.kanade.tachiyomi.extension.es.lectortmoonlineunoriginal

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class LectorTmoOnlineUnoriginal : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    // The site shows its covers and pages with referrerpolicy="no-referrer": their CDN refuses a Referer from the site
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Referer")

    override suspend fun getPopularManga(page: Int) = getMangaList(page) { addQueryParameter("order", "views") }

    override suspend fun getLatestUpdates(page: Int) = getMangaList(page) { addQueryParameter("order", "date") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangaList(page) {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        addQueryParameter("order", filters.firstInstance<SortFilter>().value)
        listOfNotNull(
            "type" to filters.firstInstance<TypeFilter>().value,
            "status" to filters.firstInstance<StatusFilter>().value,
            filters.firstInstanceOrNull<GenreFilter>()?.let { "genre" to it.value },
        ).filter { it.second.isNotEmpty() }.forEach { addQueryParameter(it.first, it.second) }
    }

    // The site also has an English catalogue: lang keeps the Spanish one
    private suspend fun getMangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .apply(params)
            .addQueryParameter("lang", "es")
            .addQueryParameter("per_page", "30")
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when {
            segments.size == 2 && segments[0] == "manga" -> segments[1]
            // Chapter pages sit at the root of the site, next to its other pages
            segments.size == 1 -> {
                val response = client.get("$apiUrl/chapters/${segments[0]}", Fetch.XHR, ensureSuccess = false)
                if (!response.isSuccessful) {
                    response.close()
                    return null
                }
                response.parseAs<ChapterPagesDto>().data.manga.slug
            }
            else -> return null
        }
        return getManga(slug).toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = getManga(manga.url)
        return SMangaUpdate(details.toSManga(), details.chapters.map { it.toSChapter() })
    }

    private suspend fun getManga(slug: String) = client.get("$apiUrl/mangas/$slug", Fetch.XHR).parseAs<MangaDetailsDto>().data

    override suspend fun getPageList(chapter: SChapter): List<Page> = getChapter(chapter.url).pages.orEmpty()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    private suspend fun getChapter(slug: String) = client.get("$apiUrl/chapters/$slug", Fetch.XHR).parseAs<ChapterPagesDto>().data

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres?lang=es", Fetch.XHR).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        // The site leaves out tags that aren't genres, as its own genre list does
        val genres = data?.parseAs<List<GenreDto>>()?.filterNot {
            it.slug.first().isDigit() || it.name.length > 22 || NOT_GENRE_REGEX.containsMatchIn(it.name)
        }
        return FilterList(
            listOfNotNull(
                SortFilter(),
                TypeFilter(),
                StatusFilter(),
                genres?.let { GenreFilter(it) },
            ),
        )
    }

    companion object {
        private val NOT_GENRE_REGEX = Regex("""\d{2,}|ads|admin|exoclick|acceso|olvidaste|option|reporte|uncategor|sin categor""", RegexOption.IGNORE_CASE)
    }
}
