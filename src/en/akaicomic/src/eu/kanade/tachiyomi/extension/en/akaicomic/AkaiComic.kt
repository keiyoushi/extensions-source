package eu.kanade.tachiyomi.extension.en.akaicomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AkaiComic : HttpSource() {

    override val name = "Akai Comic"

    override val lang = "en"

    override val baseUrl = "https://akaicomic.org"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val apiUrl = "$baseUrl/api"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/manga/list?limit=$PAGE_SIZE&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponse>()
        val manga = data.manga.map { it.toSManga() }
        val hasNextPage = data.page * data.pageSize < data.total
        return MangasPage(manga, hasNextPage)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/manga/list?limit=$PAGE_SIZE&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponse>()
        val manga = data.manga
            .sortedByDescending { it.updatedAt }
            .map { it.toSManga() }
        val hasNextPage = data.page * data.pageSize < data.total
        return MangasPage(manga, hasNextPage)
    }

    // ============================== Search ================================

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = fetchAllManga().map { allManga ->
        val filtered = if (query.isNotBlank()) {
            allManga.filter { it.title.contains(query, ignoreCase = true) }
        } else {
            allManga
        }
        val chunked = filtered.chunked(PAGE_SIZE)
        if (chunked.isEmpty()) {
            MangasPage(emptyList(), false)
        } else {
            val entries = chunked.getOrElse(page - 1) { emptyList() }
            MangasPage(entries, page < chunked.size)
        }
    }

    private fun fetchAllManga(): Observable<List<SManga>> = Observable.fromCallable {
        val allManga = mutableListOf<MangaDto>()
        var page = 1
        var hasMore = true
        while (hasMore) {
            val response = client.newCall(
                GET("$apiUrl/manga/list?limit=$FETCH_ALL_SIZE&page=$page", headers),
            ).execute()
            val data = response.parseAs<MangaListResponse>()
            allManga.addAll(data.manga)
            hasMore = page * data.pageSize < data.total
            page++
        }
        allManga.map { it.toSManga() }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/$mangaId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/$mangaId/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ChapterListResponse>()
        return data.chapters
            .filter { it.lockedByCoins == 0 }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/manga/${chapter.mangaId}/chapter/${chapter.chapterNumber}"
                    name = "Chapter ${chapter.chapterNumber}"
                    chapter_number = chapter.chapterNumber.toFloat()
                    date_upload = dateFormat.tryParse(chapter.createdAt)
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    // ============================== Pages ==================================

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val mangaId = url.pathSegments[1]
        val chapterNum = url.pathSegments[3]
        return GET("$apiUrl/manga/$mangaId/chapter/$chapterNum/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListResponse>()
        return data.pages.mapIndexed { index, path ->
            Page(index, imageUrl = "$baseUrl$path")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    // ============================== Helpers ================================

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        url = "/manga/$id"
        title = seriesName
        thumbnail_url = coverUrl
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = buildString {
            this@toSManga.description?.let { append(it) }
            alternativeName?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative name: $it")
            }
        }.ifEmpty { null }
        genre = buildList {
            type?.let { add(it) }
            this@toSManga.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
        }.joinToString().ifEmpty { null }
        status = when (this@toSManga.status?.uppercase()) {
            "ONGOING", "RELEASING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED", "DROPPED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val FETCH_ALL_SIZE = 100
    }
}
