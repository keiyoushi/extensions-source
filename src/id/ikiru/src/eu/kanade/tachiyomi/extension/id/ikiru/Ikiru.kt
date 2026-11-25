package eu.kanade.tachiyomi.extension.id.ikiru

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Ikiru : KeiSource() {
    override val supportRelatedMangasBySearch = true

    // ikiru.wtf redirects to the current subdomain
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOfNotNull(
        client.get(HUB_URL, Fetch.DOCUMENT).use { it.request.url.takeIf { url -> url.host.endsWith(".$DOMAIN") }?.toString() },
    )

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url, Fetch.XHR)
        return parseSearchResponse(response, page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "updated")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url, Fetch.XHR)
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
            val data = client.get(url, Fetch.XHR).parseData<IkiruProjectDataDto>()
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

        val response = client.get(url.build(), Fetch.XHR)
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga".toHttpUrl().newBuilder()
        .addPathSegment(manga.slug())
        .build()
        .toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // links from earlier numbered domains still resolve
        if (url.host != baseUrl.toHttpUrl().host && !url.host.endsWith(".$DOMAIN")) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "manga") return null
        val manga = SManga.create().apply { this.url = segments[1] }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
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
        val detail = client.get(url, Fetch.XHR).parseData<IkiruMangaDetailDto>()

        return SMangaUpdate(
            manga = detail.toSManga(),
            chapters = detail.chapters.chapters.map { it.toSChapter(slug) },
        )
    }

    // legacy library entries stored the absolute web URL on an earlier domain
    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) {
        chapter.url.toHttpUrl().newBuilder()
            .host(baseUrl.toHttpUrl().host)
            .build()
            .toString()
    } else {
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(chapter.url.trim('/'))
            .build()
            .toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        val document = response.asJsoup()
        val images = document.select("img[src*='cdn.ikiru.id']")

        return images.mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src")) }
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
        private const val DOMAIN = "ikiru.wtf"
        private const val HUB_URL = "https://$DOMAIN/"
    }
}
