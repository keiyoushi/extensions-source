package eu.kanade.tachiyomi.extension.es.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Dynasty : HttpSource() {

    override val name = "Dynasty"

    override val baseUrl = "https://manhuako.net"

    override val lang = "es"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/mangas?page=$page&limit=20&sort=popular", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/mangas?page=$page&limit=20&sort=newest", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "20")

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            } else {
                val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
                if (genreFilter != null && genreFilter.state != 0) {
                    addQueryParameter("genre", genreFilter.toUriPart())
                }
            }

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                addQueryParameter("sort", sortFilter.toUriPart())
            }
        }.build()

        return GET(url, headers)
    }

    private fun mangaPageParse(response: Response): MangasPage {
        val result = response.parseAs<MangaPaginatedResponse>()
        val sortType = response.request.url.queryParameter("sort")

        var mangasData = result.getMangas().filter {
            it.type?.contains("novel", ignoreCase = true) != true
        }

        mangasData = when (sortType) {
            "popular" -> mangasData.sortedByDescending { it.views ?: 0 }
            "newest" -> mangasData.sortedByDescending { parseDate(it.updatedAt ?: "") }
            "rating" -> mangasData.sortedByDescending { it.rating ?: 0f }
            "az" -> mangasData.sortedBy { it.title }
            else -> mangasData
        }

        val mangas = mangasData.map { it.toSManga() }
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(mangas, currentPage < result.getTotalPages())
    }

    override fun popularMangaParse(response: Response) = mangaPageParse(response)

    override fun latestUpdatesParse(response: Response) = mangaPageParse(response)

    override fun searchMangaParse(response: Response) = mangaPageParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringBefore("|")
        return GET("$baseUrl/api/mangas/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.parseAs<JsonElement>()
        val data = if (json is JsonObject && json.containsKey("data")) {
            json["data"]!!
        } else {
            json
        }
        return jsonInstance.decodeFromJsonElement<MangaDto>(data).toSManga()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        val mangaId = manga.url.substringBefore("|")
        var page = 1
        var totalPages = 1

        do {
            val response = client.newCall(
                GET("$baseUrl/api/chapters/paginated?manga_id=$mangaId&page=$page&limit=100&sort=desc", headers),
            ).execute()

            val res = response.parseAs<ChapterPaginatedResponse>()
            totalPages = res.getTotalPages()
            allChapters.addAll(res.getChapters().map { it.toSChapter() })
            page++
        } while (page <= totalPages)

        allChapters
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/chapter-pages?chapter_id=${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<List<PageDto>>()
        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.getUrl())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringAfter("|", "")
        return if (slug.isNotEmpty()) "$baseUrl/manga/$slug" else baseUrl
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun parseDate(dateStr: String): Long = dateFormat.tryParse(dateStr)
    }
}
