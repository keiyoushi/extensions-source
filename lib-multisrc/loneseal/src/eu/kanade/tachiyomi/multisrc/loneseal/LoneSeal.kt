package eu.kanade.tachiyomi.multisrc.loneseal

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class UrlLayout(
    private val mangaPrefix: String,
    private val chapterPrefix: String,
) {
    SLUG("", "/comic/"),
    LEGACY_COMIC("/comic/", "/comic/"),
    LEGACY_ROOT("/", "/comic/"),
    LEGACY_SERIES("/series/", "/series/"),
    ;

    internal fun mangaUrl(slug: String): String = "$mangaPrefix$slug"

    internal fun chapterUrl(seriesSlug: String, chapterSlug: String): String = "$chapterPrefix$seriesSlug/chapter/$chapterSlug"
}

abstract class LoneSeal : KeiSource() {

    protected open val apiUrl: String
        get() {
            val base = baseUrl.toHttpUrl()
            return "https://api.${base.topPrivateDomain() ?: base.host}/api"
        }

    protected open val urlLayout = UrlLayout.SLUG
    protected open val mangaUrlDirectory = "comic"
    protected open val overloadedGenres = setOf("action", "adult", "drama", "fantasy", "romance", "smut")
    protected open val includeChapterTitle = false
    protected open val includeSeriesTagFilter = false
    protected open val includeProjectOnlyFilter = false

    override suspend fun getPopularManga(page: Int) = client.get(
        searchUrl(page) {
            addQueryParameter("sort", "views")
            addQueryParameter("order", "desc")
        },
    ).parseAs<SearchResponseDto>().toMangasPage(page, urlLayout)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/comic/home-sections".toHttpUrl().newBuilder()
            .addQueryParameter("sections", "latest_comic_updates")
            .addQueryParameter("updateLimit", "240") // Default: 12, 35. Max: 35, 100, 720.
            .build()
        val response = client.get(url).parseAs<HomeSectionsDto>()
        return MangasPage(response.latestComicUpdates.map { it.toSManga(urlLayout) }, false)
    }

    private fun searchUrl(
        page: Int,
        query: HttpUrl.Builder.() -> Unit,
    ) = "$apiUrl/search".toHttpUrl().newBuilder()
        .addQueryParameter("type", "COMIC")
        .addQueryParameter("limit", "20")
        .addQueryParameter("page", page.toString())
        .apply(query)
        .build()

    private val titleComparator = Comparator<SManga> { a, b ->
        String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val selectedSort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue()
        val result = client.get(
            searchUrl(page) {
                if (query.isNotBlank()) addQueryParameter("q", query)
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
            },
        ).parseAs<SearchResponseDto>().toMangasPage(page, urlLayout)

        return when (selectedSort) {
            "az" -> MangasPage(
                result.mangas.sortedWith(titleComparator),
                result.hasNextPage,
            )
            "za" -> MangasPage(
                result.mangas.sortedWith(titleComparator.reversed()),
                result.hasNextPage,
            )
            else -> result
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.encodedPathSegments
        if (segments.firstOrNull() != mangaUrlDirectory) return null
        if (segments.getOrNull(2) == "chapter") return null
        val slug = segments.getOrNull(1) ?: return null
        return client.get("$apiUrl/series/comic/$slug")
            .parseAs<SeriesDetailDto>()
            .toSManga(urlLayout)
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = client.get("$apiUrl/series/comic/${mangaSlug(manga.url)}")
            .parseAs<SeriesDetailDto>()
        return SMangaUpdate(
            detail.toSManga(urlLayout),
            detail.units.map { it.toSChapter(detail.slug, urlLayout, includeChapterTitle) },
        )
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val slug = mangaSlug(manga.url)
        val detail = client.get("$apiUrl/series/comic/$slug").parseAs<SeriesDetailDto>()
        val keywords = detail.synopsis?.relatedKeywords().orEmpty()
        val genre = detail.genres.firstOrNull { it.slug !in overloadedGenres }?.slug
        val results = coroutineScope {
            buildList {
                keywords.take(2).forEach { keyword -> add(async { searchByKeyword(keyword) }) }
                genre?.let { add(async { searchByGenre(it) }) }
            }.awaitAll()
        }
        return results
            .interleave()
            .filterNot { it.slug == slug }
            .distinctBy { it.slug }
            .map { it.toSManga(urlLayout) }
    }

    private suspend fun searchByKeyword(keyword: String): List<MangaDto> = client.get(searchUrl(1) { addQueryParameter("q", keyword) })
        .parseAs<SearchResponseDto>().data

    private suspend fun searchByGenre(genre: String): List<MangaDto> = client.get(searchUrl(1) { addQueryParameter("genre", genre) })
        .parseAs<SearchResponseDto>().data

    protected open suspend fun Headers.Builder.configureChapterHeaders() = this

    private suspend fun chapterHeaders() = headersBuilder().apply { configureChapterHeaders() }.build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesSlug, chapterSlug) = chapterParts(chapter.url)
        val dto = client.get("$apiUrl/series/comic/$seriesSlug/chapter/$chapterSlug", chapterHeaders())
            .parseAs<ChapterPagesResponseDto>()
        val pages = toPageList(dto)
        if (pages.isEmpty()) onEmptyPages(dto)
        return pages
    }

    protected open fun onEmptyPages(dto: ChapterPagesResponseDto) = Unit

    protected open fun toPageList(dto: ChapterPagesResponseDto) = dto.chapter.pages.mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$apiUrl/genres").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter(sortOptions))
            add(OrderFilter(orderOptions))
            add(StatusFilter(statusOptions))
            add(GenreFilter(genreOptions(data, overloadedGenres)))
            add(TypeFilter(typeOptions))
            add(ColorFilter(colorOptions))
            add(ReadingFilter(readingOptions))
            if (includeSeriesTagFilter) {
                add(SelectFilter("Tag", "series_tag", seriesTagOptions))
            }
            if (includeProjectOnlyFilter) {
                add(CheckBoxFilter("Project Only", "project_only"))
            }
            add(TextFilter("Author", "author"))
            add(TextFilter("Artist", "artist"))
            add(TextFilter("Publisher", "publisher"))
        },
    )

    private fun mangaSlug(url: String) = url.trim('/').substringAfterLast('/')

    private fun chapterParts(url: String): Pair<String, String> {
        val parts = url.trim('/').split("/chapter/")
        return parts[0].substringAfterLast('/') to parts[1]
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$mangaUrlDirectory/${mangaSlug(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesSlug, chapterSlug) = chapterParts(chapter.url)
        return "$baseUrl/$mangaUrlDirectory/$seriesSlug/chapter/$chapterSlug"
    }
}

private fun <T> List<List<T>>.interleave(): List<T> {
    val lists = this
    if (lists.isEmpty()) return emptyList()
    return buildList {
        repeat(lists.maxOf { it.size }) { index ->
            lists.forEach { list -> list.getOrNull(index)?.let(::add) }
        }
    }
}
