package eu.kanade.tachiyomi.extension.en.asuratoons

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Asuratoons : KeiSource() {

    // The pages' data as React Server Components, without the HTML
    private val rscHeaders: Headers
        get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int) = getBrowse(page, "popular", "", "")

    override suspend fun getLatestUpdates(page: Int) = getBrowse(page, "latest", "", "")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return getBrowse(
                page,
                filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular",
                filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
                filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty(),
            )
        }
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, rscHeaders, Fetch.XHR).extractNextJs<SearchDto>()!!
        return MangasPage(result.manga.map { it.toSManga() }, result.hasNextPage)
    }

    private suspend fun getBrowse(page: Int, sort: String, status: String, genre: String): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sort)
            .apply {
                if (status.isNotEmpty()) addQueryParameter("status", status)
                if (genre.isNotEmpty()) addQueryParameter("genre", genre)
            }
            .build()
        val result = client.get(url, rscHeaders, Fetch.XHR).extractNextJs<BrowseDto>()!!
        return MangasPage(result.manga.map { it.toSManga() }, result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val slug = when (url.pathSegments[0]) {
            "manga" -> id
            "chapter" -> getChapterPage(id).manga.slug
            else -> return null
        }
        return getMangaPage(slug).toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = getMangaPage(manga.url)
        return SMangaUpdate(details.toSManga(), details.chapters.map { it.toSChapter() })
    }

    private suspend fun getMangaPage(slug: String): MangaDetailsDto {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().addPathSegment(slug).build()
        return client.get(url, rscHeaders, Fetch.XHR)
            .extractNextJs<MangaPageDto> { (it as? JsonObject)?.get("manga")?.let { manga -> manga is JsonObject && "chapters" in manga } == true }!!
            .manga
    }

    // The latest chapters of some series have no pages yet, on the site too
    override suspend fun getPageList(chapter: SChapter): List<Page> = getChapterPage(chapter.url.substringAfterLast('/'))
        .chapter.images.mapIndexed { i, image -> Page(i, imageUrl = image) }

    private suspend fun getChapterPage(slug: String): ChapterPageDto {
        val url = "$baseUrl/chapter".toHttpUrl().newBuilder().addPathSegment(slug).build()
        return client.get(url, rscHeaders, Fetch.XHR).extractNextJs<ChapterPageDto>()!!
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
