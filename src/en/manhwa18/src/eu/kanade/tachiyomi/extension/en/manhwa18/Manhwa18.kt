package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

class Manhwa18 : HttpSource() {

    override val baseUrl = "https://manhwa18.com"
    private val apiUrl = "https://cdn3.manhwa18.com/api/v1"
    override val lang = "en"
    override val name = "Manhwa18"
    override val supportsLatest = true

    override val versionId = 2

    private val json: Json by injectLazy()

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/get-data-products?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangaListBrowse>(response.body.string()).browseList
        return MangasPage(
            result.mangaList.map { manga ->
                manga.toSManga()
            },
            hasNextPage = result.current_page < result.last_page,
        )
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/get-data-products-in-filter?arange=new-updated?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    private var searchMangaCache: MangasPage? = null

    // search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.isBlank()) {
            client.newCall(filterMangaRequest(page, filters))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
        } else {
            if (page == 1 || searchMangaCache == null) {
                searchMangaCache = super.fetchSearchManga(page, query, filters)
                    .toBlocking()
                    .last()
            }

            // Handling a large manga list
            Observable.just(searchMangaCache!!)
                .map { mangaPage ->
                    val mangas = mangaPage.mangas

                    val fromIndex = (page - 1) * MAX_MANGA_PER_PAGE
                    val toIndex = page * MAX_MANGA_PER_PAGE

                    MangasPage(
                        mangas.subList(
                            min(fromIndex, mangas.size - 1),
                            min(toIndex, mangas.size),
                        ),
                        hasNextPage = toIndex < mangas.size,
                    )
                }
        }
    }

    private fun filterMangaRequest(page: Int, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("get-data-products-in-filter")
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is CategoryFilter -> {
                        if (filter.checked.isNotBlank()) {
                            addQueryParameter("category", filter.checked)
                        }
                    }
                    is GenreFilter -> {
                        if (filter.checked.isNotBlank()) {
                            addQueryParameter("type", filter.checked)
                        }
                    }
                    is NationFilter -> {
                        if (filter.checked.isNotBlank()) {
                            addQueryParameter("nation", filter.checked)
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("arrange", filter.getValue())
                    }
                    is StatusFilter -> {
                        addQueryParameter("is_complete", filter.getValue())
                    }
                    else -> {}
                }
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("get-search-suggest")
            addPathSegments(query)
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<List<Manga>>(response.body.string())
        return MangasPage(
            result
                .map { manga ->
                    manga.toSManga()
                },
            hasNextPage = false,
        )
    }

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast('/')
        return GET("$apiUrl/get-detail-product/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetail = json.decodeFromString<MangaDetail>(response.body.string())
        return mangaDetail.manga.toSManga().apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "${baseUrl}${manga.url}"
    }

    // chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDetail = json.decodeFromString<MangaDetail>(response.body.string())
        val mangaSlug = mangaDetail.manga.slug

        return mangaDetail.manga.episodes?.map { chapter ->
            SChapter.create().apply {
                // compatible with old theme
                setUrlWithoutDomain("/manga/$mangaSlug/${chapter.slug}")
                name = chapter.name
                date_upload = chapter.created_at?.parseDate() ?: 0L
            }
        } ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "${baseUrl}${chapter.url}"
    }

    // page list
    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url
            .removePrefix("/")
            .substringAfter('/')
        return GET("$apiUrl/get-episode/$slug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<ChapterDetail>(response.body.string())
        return result.episode.servers?.first()?.images?.mapIndexed { index, image ->
            Page(index = index, imageUrl = image)
        } ?: emptyList()
    }

    // unused
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private fun String.parseDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }

        private const val MAX_MANGA_PER_PAGE = 15
    }

    override fun getFilterList(): FilterList = getFilters()
}
