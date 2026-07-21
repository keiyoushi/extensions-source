package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class GocTruyenTranhVui : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v2"

    // ============================== Client ================================

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(authInterceptor())
        .rateLimit(3)

    private val xhrHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Cache-Control", "max-age=0")
            .add("Sec-Ch-Ua-Mobile", "?1")
            .add("Sec-Ch-Ua-Platform", "\"Android\"")
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Sec-Fetch-User", "?1")
            .add("Upgrade-Insecure-Requests", "1")
            .build()

    // ============================== Auth ==================================

    private var cachedAuthToken: String? = null
    private var authChecked = false

    private suspend fun loadAuthToken() {
        if (authChecked) return
        authChecked = true
        cachedAuthToken = getLocalStorage(baseUrl, "Authorization")
            ?.takeIf { it.isNotBlank() }
    }

    private fun authInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder().apply {
            if (original.url.encodedPath.startsWith("/api/")) {
                cachedAuthToken?.let { header("Authorization", it) }
            }
        }.build()
        chain.proceed(request)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortByList(getSortByList()).apply {
                state[0].state = true
            },
        ),
    )

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortByList(getSortByList()).apply {
                state[3].state = true
            },
        ),
    )

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search")
            addQueryParameter("p", (page - 1).toString())
            if (query.isNotEmpty()) addQueryParameter("searchValue", query)
            for (filter in filters) {
                if (filter is FilterGroup) {
                    for (checkbox in filter.state) {
                        if (checkbox.state) addQueryParameter(filter.query, checkbox.id)
                    }
                }
            }
        }.build()

        return parseMangaPage(client.get(url, xhrHeaders))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val result = response.parseAs<ResultDto<ListingDto>>().result
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, result.next)
    }

    // ============================== Details + Chapters ====================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.size < 2 || url.pathSegments[0] != "truyen") return null

        return parseMangaDetails(client.get(url).asJsoup(), url)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen/${manga.url.substringAfter(':')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailUrl = getMangaUrl(manga).toHttpUrl()
        val mangaDeferred = if (fetchDetails) {
            async {
                val document = client.get(detailUrl).asJsoup()
                parseMangaDetails(document, detailUrl)
            }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapters(manga) }
        } else {
            null
        }

        SMangaUpdate(
            mangaDeferred?.await() ?: manga,
            chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun parseMangaDetails(document: Document, url: HttpUrl): SManga = SManga.create().apply {
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.image")?.absUrl("src")
        status = parseStatus(document.selectFirst(".mb-1:contains(Trạng thái:) span")?.text())
        author = document.selectFirst(".mb-1:contains(Tác giả:) span")?.text()
        description = document.select(".v-card-text").joinToString { it.wholeText().trim() }

        val script = document.select("script").firstOrNull { it.data().contains("const comic = {") }?.data()
        val id = script?.let { comicIdRegex.find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("#comic-id-comment")?.attr("value")
        val nameEn = script?.let { comicNameEnRegex.find(it)?.groupValues?.get(1) }
            ?: url.pathSegments.getOrNull(1)

        if (id != null && nameEn != null) {
            setUrlWithoutDomain("$id:$nameEn")
        }
    }

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        val mangaId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')
        val result = client.get("$baseUrl/api/comic/$mangaId/chapter?limit=-1", xhrHeaders)
            .parseAs<ResultDto<ChapterListDto>>()

        if (result.result.chapters.isEmpty()) {
            throw Exception("Có thể: Phiên làm việc đã hết hạn, vui lòng tải lại.")
        }

        return result.result.chapters.map { it.toSChapter(slug) }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.lowercase().contains("đang thực hiện") -> SManga.ONGOING
        status.lowercase().contains("hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.substringAfter("/truyen/").substringBefore("/chuong-")
        val numberChapter = chapter.url.substringAfter("/chuong-").substringBefore("#")
        return "$baseUrl/truyen/$slug/chuong-$numberChapter"
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        loadAuthToken()

        val slug = chapter.url.substringAfter("/truyen/").substringBefore("/chuong-")
        val numberChapter = chapter.url.substringAfter("/chuong-").substringBefore("#")
        val comicId = chapter.url.substringAfter("#")
        val body = FormBody.Builder()
            .add("comicId", comicId)
            .add("chapterNumber", numberChapter)
            .add("nameEn", slug)
            .build()

        val imageList = client.post("$baseUrl/api/chapter/loadAll", xhrHeaders, body)
            .parseAs<ResultDto<ImageListDto>>()
            .result
            .data

        if (imageList.isNullOrEmpty()) {
            throw Exception("Chưa đăng nhập trong WebView. Hoặc không có ảnh!")
        }

        return imageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = if (imageUrl.startsWith("/image/")) baseUrl + imageUrl else imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/category", xhrHeaders).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data
            ?.parseAs<ResultDto<List<CategoryDto>>>()
            ?.result
            .orEmpty()
            .map(CategoryDto::toOption)

        val filters = mutableListOf<FilterGroup>(
            StatusList(getStatusList()),
            SortByList(getSortByList()),
        )
        if (genres.isNotEmpty()) filters += GenreList(genres)

        return FilterList(filters)
    }

    // ============================== Constants =============================

    private val comicIdRegex = Regex("""id:\s*"([^"]+)"""")
    private val comicNameEnRegex = Regex("""nameEn:\s*`([^`]+)`""")
}
