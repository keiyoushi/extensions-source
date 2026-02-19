package eu.kanade.tachiyomi.extension.vi.otruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.collections.flatMap
import kotlin.collections.map

class OTruyen : HttpSource() {

    override val name: String = "OTruyen"

    override val lang: String = "vi"

    override val supportsLatest: Boolean = true

    private val domainName = "otruyen"

    override val baseUrl: String = "https://$domainName.cc"

    private val domainApi = "${domainName}api.com"

    private val apiUrl = "https://$domainApi/v1/api"

    private val cdnUrl = "https://sv1.${domainName}cdn.com"

    private val imgUrl = "https://img.$domainApi/uploads/comics"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/danh-sach/truyen-moi?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.parseAs<DataDto<ListingData>>()
        val pagination = res.data.params.pagination
        val totalPages = (pagination.totalItems + pagination.totalItemsPerPage - 1) / pagination.totalItemsPerPage
        val manga = res.data.items.map { it.toSManga(imgUrl) }
        val hasNextPage = pagination.currentPage < totalPages
        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/danh-sach/hoan-thanh?page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/truyen-tranh/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<DataDto<EntryData>>()
        return res.data.item.toSManga(imgUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen-tranh/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<DataDto<EntryData>>()
        val mangaUrl = res.data.item.slug
        val date = res.data.item.updatedAt
        return res.data.item.chapters
            .flatMap { server -> server.serverData.map { it.toSChapter(date, mangaUrl) } }
            .sortedByDescending { it.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaUrl = chapter.url.substringAfter(":")
        return "$baseUrl/truyen-tranh/$mangaUrl"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringBefore(":")
        return GET("$cdnUrl/v1/api/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<DataDto<PageDto>>()
        return res.data.toPage()
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val (segments, params) = when {
            query.isNotBlank() -> {
                listOf("tim-kiem") to mapOf("keyword" to query)
            }

            filters.filterIsInstance<GenreList>().isNotEmpty() -> {
                val genre = filters.filterIsInstance<GenreList>().first()
                listOf("the-loai", genre.values[genre.state].slug) to emptyMap()
            }

            filters.filterIsInstance<GenreList>().isEmpty() -> {
                val status = filters.filterIsInstance<StatusList>().first()
                listOf("danh-sach", status.values[status.state].slug) to emptyMap()
            }

            else -> {
                listOf("danh-sach", "dang-phat-hanh") to emptyMap()
            }
        }

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            addQueryParameter("page", "$page")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        return GET(url, headers)
    }

    private fun genresRequest(): Request = GET("$apiUrl/the-loai", headers)

    private fun parseGenres(response: Response): List<Pair<String, String>> = response.parseAs<DataDto<GenresData>>().data.items.map { Pair(it.slug, it.name) }

    private var genreList: List<Pair<String, String>> = emptyList()

    private var fetchGenresAttempts: Int = 0
    private fun fetchGenres() {
        launchIO {
            try {
                client.newCall(genresRequest()).await()
                    .use { parseGenres(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.also { genreList = it }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    private class GenreList(name: String, pairs: List<Pair<String, String>>) : GenresFilter(name, pairs)

    private class StatusList :
        Filter.Select<Genre>(
            "Trạng thái",
            arrayOf(
                Genre("Mới nhất", "truyen-moi"),
                Genre("Đang phát hành", "dang-phat-hanh"),
                Genre("Hoàn thành", "hoan-thanh"),
                Genre("Sắp ra mắt", "sap-ra-mat"),
            ),
        )

    private open class GenresFilter(title: String, pairs: List<Pair<String, String>>) :
        Filter.Select<Genre>(
            title,
            pairs.map { Genre(it.second, it.first) }.toTypedArray(),
        )

    private class Genre(val name: String, val slug: String) {
        override fun toString() = name
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return if (genreList.isEmpty()) {
            FilterList(
                Filter.Header("Nhấn 'Làm mới' để hiển thị thể loại"),
                Filter.Header("Hiển thị thể loại sẽ ẩn danh sách trạng thái vì không dùng chung được"),
                Filter.Header("Không dùng chung được với tìm kiếm bằng tên"),
                StatusList(),
            )
        } else {
            FilterList(
                Filter.Header("Không dùng chung được với tìm kiếm bằng tên"),
                GenreList("Thể loại", genreList),
            )
        }
    }
}
