package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.net.URLEncoder

@Source
abstract class Ikiru : KeiSource() {
    // support sugesttion komikku
    override val supportRelatedMangasBySearch = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseSearchResponse(response, page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "updated")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseSearchResponse(response, page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // the "project" option is a separate listing, the other filters do not apply to it
        if (filters.firstInstanceOrNull<ProjectFilter>()?.isProject == true) {
            val url = "$baseUrl/api/public/manga/project".toHttpUrl().newBuilder()
                .addQueryParameter("layout", "vertical")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", PROJECT_PAGE_SIZE.toString())
                .build()
            val data = client.get(url).parseData<IkiruProjectDataDto>()
            val projects = data.project.map { it.toSManga() }
            val hasNextPage = projects.isNotEmpty() && page * PROJECT_PAGE_SIZE < data.total
            return MangasPage(projects, hasNextPage)
        }

        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("query", query.trim())
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        val response = client.get(url.build())
        return parseSearchResponse(response, page)
    }

    private fun parseSearchResponse(response: Response, page: Int): MangasPage {
        val data = response.parseData<IkiruSearchDataDto>()
        val mangaList = data.mangas.map { it.toSManga() }
        val hasNextPage = mangaList.isNotEmpty() && page * mangaList.size < data.total
        return MangasPage(mangaList, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
        ProjectFilter(),
    )

    // library entries saved by older versions stored "/manga/<slug>", the absolute url, or a JSON blob
    private fun SManga.slug(): String {
        val raw = url.trim()
        return when {
            raw.startsWith("{") -> raw.parseAs<IkiruLegacyMangaUrlDto>().slug
            raw.startsWith("http") -> raw.toHttpUrlOrNull()?.pathSegments?.getOrNull(1) ?: raw
            else -> raw.removePrefix("/manga/").removePrefix("/")
        }.trim('/')
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.slug()
        return "$baseUrl/manga/${slug.encodePathSegments()}"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug()
        val url = "$baseUrl/api/public/manga".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .build()

        // the dedicated chapter endpoint returns an empty list for some manga, while this one
        // embeds the complete chapter list
        val detail = client.get(url).parseData<IkiruMangaDetailDto>()

        return SMangaUpdate(
            manga = detail.toSManga(),
            chapters = detail.chapters.chapters.map { it.toSChapter(slug) },
        )
    }

    // legacy library entries stored the absolute web URL
    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) {
        chapter.url
    } else {
        "$baseUrl/${chapter.url.trim('/').encodePathSegments()}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val document = response.asJsoup()
        val images = document.select("img[src*='cdn.ikiru.id']")

        return images.mapIndexed { idx, img -> Page(idx, imageUrl = img.attr("src")) }
    }

    // the API returns HTTP 200 with a failure envelope for bad input, so the flag must be checked explicitly
    private inline fun <reified T> Response.parseData(): T {
        val res = parseAs<IkiruResponseDto<T>>()
        val data = res.data
        if (!res.success || data == null) {
            throw Exception(res.message?.takeIf { it.isNotBlank() } ?: "Request failed")
        }
        return data
    }

    companion object {
        private const val PROJECT_PAGE_SIZE = 24
    }
}

// the API only accepts percent-encoded slugs, and some slugs contain spaces or non-ASCII characters
private fun String.encodePathSegments(): String = split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
