package eu.kanade.tachiyomi.extension.en.mangade

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDE : HttpSource() {

    override val name = "MangaDE"
    override val baseUrl = "https://mangade.io"
    override val lang = "en"
    override val supportsLatest = true

    private var genresList: List<Genre> = emptyList()
    private var genresFetched: Boolean = false
    private var fetchGenresAttempts: Int = 0

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    // ===============================
    // Popular
    // ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$API_URL/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "most-viewed")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Latest
    // ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$API_URL/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "newest")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$API_URL/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")

        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genres[]", it.id) }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("comic_status", filter.toUriPart())
                    }
                }
                is TypeFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("category", filter.toUriPart())
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }
                is YearFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("year", filter.toUriPart())
                    }
                }
                is ChapterCountFilter -> {
                    url.addQueryParameter("min_chapter_count", filter.toUriPart())
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<PayloadDto<MangaListPageDto>>()
        val data = payload.data
        val totalPage = data.totalPage
        val currentPage = data.page.toInt()

        val mangas = data.list.map { manga ->
            SManga.create().apply {
                title = manga.name
                thumbnail_url = manga.image
                url = "/${manga.slug}?mid=${manga.id}"
            }
        }

        return MangasPage(mangas, currentPage < totalPage)
    }

    // ===============================
    // Details
    // ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("mid=")
        return GET("$API_URL/comics/$id/view", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val payload = response.parseAs<PayloadDto<MangaDto>>()
        val manga = payload.data

        return SManga.create().apply {
            title = manga.name
            thumbnail_url = manga.image
            description = manga.description
            genre = manga.genre_names?.replace(",", ", ")
            status = when (manga.status) {
                "Ongoing", "Releasing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                "On Hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBefore("?").removePrefix("/")
        val mid = manga.url.substringAfter("mid=")

        return "$baseUrl/comic/$slug-pid$mid"
    }

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("mid=")
        return GET("$API_URL/comics/$id/view?size=500", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<PayloadDto<MangaDto>>()
        val manga = payload.data
        val mangaUrl = manga.slug?.let { "/$it" } ?: ""
        val chapterList = manga.news_chapters

        return chapterList.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                chapter_number = chapter.chapter_number?.toFloatOrNull() ?: -1f
                url = "/${manga.slug}/${chapter.slug}?cid=${chapter.id}&mid=${manga.id}"
                date_upload = dateFormat.tryParse(chapter.published_date)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val path = chapter.url.substringBefore("?").removePrefix("/")
        val mid = chapter.url.substringAfter("mid=")

        val parts = path.split("/")
        val mangaSlug = parts[0]
        val chapterSlug = parts.getOrNull(1) ?: ""

        return "$baseUrl/comic/$mangaSlug-$mid/$chapterSlug"
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfter("cid=").substringBefore("&")
        return GET("$API_URL/chapters/$id/view", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.parseAs<PayloadDto<ChapterDto>>()
        val chapter = payload.data
        val images = chapter.chapter_images

        return images.mapIndexed { index, page ->
            Page(index, "", page.image)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ===============================
    // Filters
    // ===============================

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            YearFilter(),
            ChapterCountFilter(),
        )

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                GenreFilter(genresList),
            )
        } else {
            filters += listOf(
                Filter.Header("Press 'Reset' to load genres"),
            )
        }

        return FilterList(filters)
    }

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && !genresFetched) {
            try {
                client.newCall(GET("$API_URL/genres?size=500", headers)).execute().use { response ->
                    val payload = response.parseAs<PayloadDto<GenreListPageDto>>()
                    genresList = payload.data.genres.map { Genre(it.name, it.id) }
                    genresFetched = true
                }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    companion object {
        private const val API_URL = "https://api.mangade.io/api"
    }
}
