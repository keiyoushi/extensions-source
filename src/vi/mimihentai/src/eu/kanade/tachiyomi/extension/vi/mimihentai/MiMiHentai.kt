package eu.kanade.tachiyomi.extension.vi.mimihentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MiMiHentai : HttpSource() {
    override val name: String = "MiMiHentai"

    override val lang: String = "vi"

    override val baseUrl: String = "https://mimihentai.com"

    private val apiUrl: String = "$baseUrl/api/v1/manga"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun latestUpdatesRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tatcatruyen")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "updated_at")
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.parseAs<ListingDto>()
        val hasNextPage = res.currentPage != res.totalPage
        return MangasPage(res.data.map { it.toSManga() }, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/g/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("gallery")
            .addPathSegment(id)
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val segments = response.request.url.pathSegments
        val mangaId = segments.last()
        val res = response.parseAs<List<ChapterDto>>()
        return res.map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.url.substringBefore('/')
        val chapterId = chapter.url.substringAfter('/')
        return "$baseUrl/g/$mangaId/chapter/$chapterId"
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("info")
            .addPathSegment(id)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<MangaDto>()
        return res.toSManga()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiUrl/chapter?id=${chapter.url.substringAfterLast("/")}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<PageListDto>()
        return res.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tatcatruyen")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "views")
        }.build(),
        headers,
    )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }
    private fun searchMangaByIdRequest(id: String) = GET("$apiUrl/info/$id", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search")
            addQueryParameter("page", page.toString())
            addQueryParameter("name", query)

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filters ->
                when (filters) {
                    is SortByList ->
                        {
                            val sort = getSortByList()[filters.state]
                            addQueryParameter("sort", sort.id)
                        }

                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    private class SortByList(sort: Array<SortBy>) : Filter.Select<SortBy>("Sắp xếp", sort)
    private class SortBy(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    override fun getFilterList() = FilterList(
        SortByList(getSortByList()),
    )

    private fun getSortByList() = arrayOf(
        SortBy("Mới", "updated_at"),
        SortBy("Likes", "likes"),
        SortBy("Views", "views"),
        SortBy("Lưu", "follows"),
        SortBy("Tên", "title"),
    )
    companion object {
        private const val PREFIX_ID_SEARCH = "id:"
    }
}
