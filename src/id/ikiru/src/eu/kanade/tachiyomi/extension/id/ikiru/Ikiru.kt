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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

@Source
abstract class Ikiru : KeiSource() {
    override val supportRelatedMangasBySearch = true

    override suspend fun getPopularManga(page: Int): MangasPage = getSeriesList(page) {
        addQueryParameter("sort", "popular")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSeriesList(page) {
        addQueryParameter("sort", "update")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getSeriesList(page) {
        if (query.isNotBlank()) {
            addQueryParameter("q", query.trim())
        }
        filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
    }

    private suspend fun getSeriesList(page: Int, configure: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/api/v1/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply(configure)
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<IkiruResponseDto<List<IkiruSeriesItemDto>>>()
        return MangasPage(result.requireData().map { it.toSManga() }, result.pagination?.hasNextPage == true)
    }

    // library entries saved by older versions stored "/manga/<slug>", the absolute url, or a JSON blob
    private fun SManga.slug(): String {
        val raw = url.trim()
        return when {
            raw.startsWith("{") -> raw.parseAs<IkiruLegacyMangaUrlDto>().slug
            raw.startsWith("http") -> raw.toHttpUrlOrNull()?.pathSegments?.getOrNull(1) ?: raw
            else -> raw.removePrefix("/manga/").removePrefix("/")
        }.trim('/')
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series".toHttpUrl().newBuilder()
        .addPathSegment(manga.slug())
        .build()
        .toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // links from earlier numbered domains still resolve
        if (url.host != baseUrl.toHttpUrl().host && !url.host.endsWith(".$DOMAIN")) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] !in listOf("series", "manga")) return null
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
        val url = "$baseUrl/api/v1/series".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .build()

        val detail = client.get(url, Fetch.XHR).parseData<IkiruSeriesDetailDto>()

        return SMangaUpdate(
            manga = detail.toSManga(baseUrl),
            chapters = detail.chapters.map { it.toSChapter(slug) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, number) = chapter.parseKey()
        return "$baseUrl/series".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .addPathSegment("chapter")
            .addPathSegment(number)
            .build()
            .toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.parseKey()
        val url = "$baseUrl/api/v1/series".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .addPathSegment("chapters")
            .addPathSegment(number)
            .build()

        return client.get(url, Fetch.XHR).parseData<IkiruChapterPagesDto>().pages
            .sortedBy { it.pageNumber }
            .mapIndexed { idx, page -> Page(idx, imageUrl = page.imageUrl) }
    }

    // also matches the absolute keys saved by older versions
    private fun SChapter.parseKey(): Pair<String, String> {
        val match = chapterKeyRegex.find(url) ?: throw Exception("Refresh the chapter list")
        return match.groupValues[1] to match.groupValues[2]
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/v1/genres", Fetch.XHR)
        .parseData<List<IkiruGenreDto>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<IkiruGenreDto>>().orEmpty()
            .filterNot { it.slug in explicitGenres }
            .map { Genre(it.name, it.slug) }

        return FilterList(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            SourceFilter(),
            GenreFilter(genres),
        )
    }

    private inline fun <reified T> Response.parseData(): T = parseAs<IkiruResponseDto<T>>().requireData()

    companion object {
        private const val DOMAIN = "ikiru.wtf"

        // anchored so a suffixed older key like "chapter-12-5" fails instead of opening chapter 12
        private val chapterKeyRegex = Regex("""/manga/([^/]+)/chapter-([\d.]+)/?$""")

        // the extension is SFW
        private val explicitGenres = setOf("adult", "hentai", "lolicon", "shotacon", "smut")
    }
}
