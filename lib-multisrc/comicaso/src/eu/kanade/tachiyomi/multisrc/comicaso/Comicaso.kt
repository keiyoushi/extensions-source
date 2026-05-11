package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

abstract class Comicaso(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val pageSize: Int = 20,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var cachedMangaList: List<MangaDto>? = null

    private fun getMangaList(): Observable<List<MangaDto>> = cachedMangaList?.let { Observable.just(it) }
        ?: client.newCall(GET("$baseUrl/wp-content/static/manga/index.json", headers))
            .asObservableSuccess()
            .map { response ->
                response.parseAs<List<MangaDto>>().also {
                    cachedMangaList = it
                }
            }

    // ============================== Popular ===============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val start = (page - 1) * pageSize
            if (start >= mangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, mangas.size)
            MangasPage(mangas.subList(start, end).map { it.toSManga() }, end < mangas.size)
        }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Latest ===============================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val sortedMangas = mangas.sortedByDescending { it.updatedAt ?: it.mangaDate ?: 0L }
            val start = (page - 1) * pageSize
            if (start >= mangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, mangas.size)
            MangasPage(sortedMangas.subList(start, end).map { it.toSManga() }, end < sortedMangas.size)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotEmpty()) {
            val url = when {
                query.startsWith("https://") ->
                    query.trim()
                query.startsWith(URL_SEARCH_PREFIX) ->
                    query.removePrefix(URL_SEARCH_PREFIX).trim()
                query.contains("://") ->
                    query.trim()
                else -> null
            }

            if (url != null) {
                val httpUrl = url.toHttpUrl()
                if (httpUrl.host != baseUrl.toHttpUrl().host) {
                    throw Exception("Unsupported url")
                }
                val mangaSlug = url.substringAfter("/komik/").substringBefore("/")
                return fetchMangaDetails(SManga.create().apply { this.url = mangaSlug })
                    .map { MangasPage(listOf(it), false) }
            }
        }

        return getMangaList().map { mangas ->
            var filteredMangas = mangas

            if (query.isNotEmpty()) {
                filteredMangas = filteredMangas.filter { it.title.contains(query, ignoreCase = true) }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            val genre = filter.values[filter.state]
                            filteredMangas = filteredMangas.filter { it.genres?.contains(genre) == true }
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            val status = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.status == status }
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            val type = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.type == type }
                        }
                    }
                    else -> {}
                }
            }

            val start = (page - 1) * pageSize
            if (start >= filteredMangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, filteredMangas.size)
            MangasPage(filteredMangas.subList(start, end).map { it.toSManga() }, end < filteredMangas.size)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/komik/${manga.url}/"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/wp-content/static/manga/${manga.url}.json", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailDto>()
        return SManga.create().apply {
            url = result.slug
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
        return result.chapters?.map { it.toSChapter(result.slug) }?.reversed() ?: emptyList()
    }

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val cleanUrl = chapterUrl.newBuilder().query(null).build().toString()

        val body = TokenRequestDto(listOf(cleanUrl)).toJsonRequestBody()
        val tokenHeaders = headersBuilder()
            .set("Referer", "$baseUrl${chapter.url.substringBeforeLast("/", "").substringBeforeLast("/")}/")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val tokenRequest = Request.Builder()
            .url("$baseUrl/wp-json/mp/v1/chapter")
            .post(body)
            .headers(tokenHeaders)
            .build()

        return client.newCall(tokenRequest).asObservableSuccess().switchMap { response ->
            val result = response.parseAs<TokenDto>()
            val token = result.tokens[cleanUrl]
                ?: throw Exception("Failed to get token for $cleanUrl")

            val pageUrl = chapterUrl.newBuilder()
                .addQueryParameter("t", token)
                .addQueryParameter("e", result.expire.toString())
                .build()

            client.newCall(GET(pageUrl, headers)).asObservableSuccess().map(::pageListParse)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.mjv2-page-image")
            .map { it.attr("abs:src").ifEmpty { it.attr("abs:data-src") } }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, document.location(), imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Filter ini dapat dikombinasikan dengan pencarian teks."))
        filters.add(Filter.Separator())
        filters.add(StatusFilter())
        filters.add(TypeFilter())

        val genres = cachedMangaList?.flatMap { it.genres ?: emptyList() }
            ?.distinct()
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        filters.add(GenreFilter(if (genres.isNullOrEmpty()) arrayOf("All") else arrayOf("All") + genres.toTypedArray()))

        filters.add(Filter.Separator())
        filters.add(Filter.Header("Jika daftar genre tidak muncul, silakan tekan 'Reset' untuk memuat ulang filter."))

        return FilterList(filters)
    }

    protected class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)
    protected class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "On-going", "End"))
    protected class TypeFilter : Filter.Select<String>("Type", arrayOf("All", "Manga", "Manhua", "Manhwa"))

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
    }
}
