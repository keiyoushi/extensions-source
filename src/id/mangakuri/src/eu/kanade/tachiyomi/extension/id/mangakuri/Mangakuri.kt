package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

private const val DOMAIN = "mangakuri.online"

@Source
abstract class Mangakuri : KeiSource() {
    private val apiUrl get() = "https://api.$DOMAIN/api"

    private var bearerToken: String? = null

    private val apiHeaders get() =
        headersBuilder().apply {
            bearerToken?.let { set("Authorization", "Bearer $it") }
        }.build()

    // ======================= Popular + Latest ==============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selected)
                    url.addQueryParameter("order", filter.order)
                }
                is StatusFilter -> {
                    val status = filter.selectedValue()
                    if (status.isNotEmpty()) url.addQueryParameter("status", status)
                }
                is GenreFilter -> {
                    val genre = filter.selectedValue()
                    if (genre.isNotEmpty()) url.addQueryParameter("genre", genre)
                }
                is TypeFilter -> {
                    val type = filter.selectedValue()
                    if (type.isNotEmpty()) url.addQueryParameter("comic_type", type)
                }
                is ColorFilter -> {
                    val color = filter.selectedValue()
                    if (color.isNotEmpty()) url.addQueryParameter("color_format", color)
                }
                is ReadingFilter -> {
                    val reading = filter.selectedValue()
                    if (reading.isNotEmpty()) url.addQueryParameter("reading_format", reading)
                }
                is TextFilter -> {
                    if (filter.state.isNotEmpty()) url.addQueryParameter(filter.queryKey, filter.state)
                }
                else -> {}
            }
        }

        return parseSearchManga(page, client.get(url.build(), apiHeaders))
    }

    private fun parseSearchManga(page: Int, response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()
        val mangas = dto.data.map { it.toSManga() }
        return MangasPage(mangas, page < dto.totalPages)
    }

    // ========================= Details + Chapters ==============================

    override val supportRelatedMangasBySearch = true

    private suspend fun getMangaDetails(slug: String) = client.get("$apiUrl/series/comic/$slug", apiHeaders).parseAs<SeriesDetailDto>()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return getMangaDetails(slug).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/').trim('/')
        val dto = getMangaDetails(slug)
        val comicSlug = dto.slug
        return SMangaUpdate(
            manga = dto.toSManga(),
            chapters = dto.units.map { it.toSChapter(comicSlug) },
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        bearerToken = bearerToken ?: getLocalStorage(baseUrl, "token")

        val response = client.get("$apiUrl/series${chapter.url}", apiHeaders)
        val chapter = response.parseAs<ChapterDetailDto>().chapter
        if (chapter.pages.isEmpty()) {
            if (chapter.passwordRequired == true) error("Password required")

            if (chapter.loginRequired == true) {
                bearerToken = null
                error("Login in WebView and retry")
            }
        }

        return chapter.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl)
        }
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$apiUrl/genres").parseAs<List<Filter>>().associate {
        it.name to it.slug
    }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            StatusFilter(),
            data?.parseAs<Map<String, String>>()?.let { GenreFilter(it) },
            TypeFilter(),
            ColorFilter(),
            ReadingFilter(),
            TextFilter("Author", "author"),
            TextFilter("Artist", "artist"),
            TextFilter("Publisher", "publisher"),
        ),
    )
}
