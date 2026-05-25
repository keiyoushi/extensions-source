package eu.kanade.tachiyomi.extension.pt.brscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BRScans : HttpSource() {

    override val name = "BRScans"

    override val baseUrl = "https://e5oer7ngt8.execute-api.sa-east-1.amazonaws.com/dev"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // Genre caching system
    private var genreMap: Map<Int, String> = emptyMap()
    private var genreMapFetched = false

    private fun fetchGenreMap() {
        if (genreMapFetched) return
        try {
            val req = GET("$baseUrl/manhwas/genres/", headers)
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val genres = res.parseAs<List<GenreDto>>()
                genreMap = genres.associate { it.id to it.name }
                genreMapFetched = true
            }
            res.close()
        } catch (e: Exception) {
            // Silently ignore or log to avoid blocking the main flow
        }
    }

    // ============================== Popular ===============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.fromCallable {
        fetchGenreMap()
        val req = popularMangaRequest(page)
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("Erro HTTP ${res.code} ao buscar mangás populares")
        }
        popularMangaParse(res)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhwas/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val paginated = response.parseAs<PaginatedManhwaDto>()
        val mangas = paginated.results.map { it.toSManga(genreMap) }
        val hasNext = paginated.next != null
        return MangasPage(mangas, hasNext)
    }

    // =============================== Latest ===============================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.fromCallable {
        fetchGenreMap()
        val req = latestUpdatesRequest(page)
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("Erro HTTP ${res.code} ao buscar últimos lançamentos")
        }
        latestUpdatesParse(res)
    }

    // The backend default ordering is by recent chapter activity, which matches latest updates perfectly!
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        fetchGenreMap()
        val req = searchMangaRequest(page, query, filters)
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("Erro HTTP ${res.code} ao realizar busca")
        }
        searchMangaParse(res)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // The search action on the backend is located at /manhwas/search/?query={query}
        return GET("$baseUrl/manhwas/search/?query=${query.trim()}", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // The search endpoint returns a raw list (array) of ManhwaDto
        val results = response.parseAs<List<ManhwaDto>>()
        val mangas = results.map { it.toSManga(genreMap) }
        return MangasPage(mangas, false) // Search is currently unpaginated on the backend
    }

    // =========================== Manga Details ============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.fromCallable {
        fetchGenreMap()
        val req = mangaDetailsRequest(manga)
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("Erro HTTP ${res.code} ao buscar detalhes do mangá")
        }
        mangaDetailsParse(res).apply {
            initialized = true
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // manga.url stores the manhwa ID
        return GET("$baseUrl/manhwas/${manga.url}/", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<ManhwaDto>()
        return dto.toSManga(genreMap)
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        // The detailed manhwa response contains all its chapters
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ManhwaDto>()
        // Chapters are ordered ascending (1, 2, 3...) in the backend,
        // so we reverse the list to show the latest chapters first.
        return dto.chapters.reversed().map { it.toSChapter(dateFormat) }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        // chapter.url stores the chapter ID
        return GET("$baseUrl/chapters/${chapter.url}/", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterDetailDto>()
        return dto.pages.mapIndexedNotNull { index, pageDto ->
            pageDto.toPage(index)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")
}
