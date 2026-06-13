package eu.kanade.tachiyomi.extension.id.comicaso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

class Comicaso : HttpSource() {

    override val name = "Comicaso"

    override val baseUrl = "https://v3.comicaso.pro"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(4)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var cachedMangaList: List<Pair<String, MangaDto>>? = null

    private fun getMangaList(): Observable<List<Pair<String, MangaDto>>> {
        if (cachedMangaList != null) return Observable.just(cachedMangaList)

        val sources = listOf("comicazen", "medusa")
        val observables = sources.map { source ->
            client.newCall(GET("$STATIC_API_URL/$source/manga/index.json", headers))
                .asObservableSuccess()
                .map { response ->
                    response.parseAs<List<MangaDto>>().map { source to it }
                }
                .onErrorReturn { emptyList() }
        }

        return Observable.zip(observables) { results ->
            results.flatMap { it as List<Pair<String, MangaDto>> }
        }.doOnNext { cachedMangaList = it }
    }

    // ============================== Popular ===============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val start = (page - 1) * PAGE_SIZE
            if (start >= mangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + PAGE_SIZE, mangas.size)
            MangasPage(mangas.subList(start, end).map { it.second.toSManga(it.first) }, end < mangas.size)
        }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Latest ===============================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val sortedMangas = mangas.sortedByDescending { it.second.updatedAt ?: it.second.mangaDate ?: 0L }
            val start = (page - 1) * PAGE_SIZE
            if (start >= sortedMangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + PAGE_SIZE, sortedMangas.size)
            MangasPage(sortedMangas.subList(start, end).map { it.second.toSManga(it.first) }, end < sortedMangas.size)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotEmpty()) {
            val url = when {
                query.startsWith("https://") -> query.trim()
                query.startsWith(URL_SEARCH_PREFIX) -> query.removePrefix(URL_SEARCH_PREFIX).trim()
                else -> null
            }

            if (url != null) {
                val httpUrl = url.toHttpUrl()
                val pageParam = httpUrl.queryParameter("page")
                val sourceParam = httpUrl.queryParameter("source")
                val slugParam = httpUrl.queryParameter("slug")

                if (pageParam == "manga" && sourceParam != null && slugParam != null) {
                    return fetchMangaDetails(SManga.create().apply { this.url = "$sourceParam/$slugParam" })
                        .map { MangasPage(listOf(it), false) }
                }
            }
        }

        return getMangaList().map { mangas ->
            var filteredMangas = mangas

            if (query.isNotEmpty()) {
                filteredMangas = filteredMangas.filter { it.second.title.contains(query, ignoreCase = true) }
            }

            filters.forEach { filter ->
                when (filter) {
                    is SourceFilter -> {
                        if (filter.state > 0) {
                            val source = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.first == source }
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            val genre = filter.values[filter.state]
                            filteredMangas = filteredMangas.filter { it.second.genres?.contains(genre) == true }
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            val status = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.second.status == status }
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            val type = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.second.type == type }
                        }
                    }
                    else -> {}
                }
            }

            val start = (page - 1) * PAGE_SIZE
            if (start >= filteredMangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + PAGE_SIZE, filteredMangas.size)
            MangasPage(filteredMangas.subList(start, end).map { it.second.toSManga(it.first) }, end < filteredMangas.size)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String {
        val (source, slug) = manga.url.split("/")
        return "$baseUrl/?page=manga&source=$source&slug=$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val (source, slug) = manga.url.split("/")
        return GET("$STATIC_API_URL/$source/manga/$slug.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailDto>()
        val source = response.request.url.pathSegments[1]
        return SManga.create().apply {
            url = "$source/${result.slug}"
            title = result.title
            thumbnail_url = result.thumbnail
            description = buildString {
                result.synopsis?.let { append(Jsoup.parse(it).text()) }
                result.alternative?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative: $it")
                }
            }.trim()
            author = result.author
            artist = result.artist
            genre = result.genres?.joinToString()
            status = when (result.status) {
                "on-going" -> SManga.ONGOING
                "end" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailDto>()
        val source = response.request.url.pathSegments[1]
        return result.chapters?.map { it.toSChapter(source, result.slug) }?.reversed() ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = chapter.url.split("/")
        val source = segments[0]
        val manga = segments[1]
        val slug = segments[2]
        return "$baseUrl/?page=chapter&source=$source&manga=$manga&chapter=$slug"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.split("/")
        val source = segments[0]
        val manga = segments[1]
        val slug = segments[2]

        return GET("$STATIC_API_URL/$source/chapter/$manga/$slug.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterImagesDto>()
        return result.images.mapIndexed { index, imageUrl ->
            Page(index, "", imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Filter ini dapat dikombinasikan dengan pencarian teks."))
        filters.add(Filter.Separator())
        filters.add(SourceFilter())
        filters.add(StatusFilter())
        filters.add(TypeFilter())

        val genres = cachedMangaList?.flatMap { it.second.genres ?: emptyList() }
            ?.distinct()
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        filters.add(GenreFilter(if (genres.isNullOrEmpty()) arrayOf("All") else arrayOf("All") + genres.toTypedArray()))

        filters.add(Filter.Separator())
        filters.add(Filter.Header("Jika daftar genre tidak muncul, silakan tekan 'Reset' untuk memuat ulang filter."))

        return FilterList(filters)
    }

    private class SourceFilter : Filter.Select<String>("Source", arrayOf("All", "Comicazen", "Medusa"))
    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)
    private class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "On-going", "End"))
    private class TypeFilter : Filter.Select<String>("Type", arrayOf("All", "Manga", "Manhua", "Manhwa"))

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
        private const val STATIC_API_URL = "https://static.comicaso.pro/static"
        private const val PAGE_SIZE = 60
    }
}
