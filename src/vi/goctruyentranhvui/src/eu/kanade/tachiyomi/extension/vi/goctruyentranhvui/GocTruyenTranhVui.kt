package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.runBlocking
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
abstract class GocTruyenTranhVui :
    KeiSource(),
    ConfigurableSource {

    override val name: String = "Goc Truyen Tranh Vui"

    private val apiUrl get() = "$baseUrl/api/v2"

    private val preferences by lazy { getPreferences() }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
        addInterceptor(::authInterceptor)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        build()["user-agent"]?.let {
            set("user-agent", removeWebViewToken(it))
        }
    }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

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

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(request)
        }

        val token = tokenCache ?: runBlocking { getToken() }

        return chain.proceed(
            request.newBuilder().apply {
                header("X-Requested-With", "XMLHttpRequest")

                token?.let {
                    header("Authorization", it)
                }

                if (request.method == "POST") {
                    header("Origin", baseUrl)
                }
            }.build(),
        )
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

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortByList(getSortByList()).apply {
                state[3].state = true
            },
        ),
    )

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        val res = response.parseAs<ResultDto<ListingDto>>()
        val hasNextPage = res.result.next
        return MangasPage(res.result.data.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) {
            throw Exception("Tên miền không được hỗ trợ")
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.getOrNull(0) == "truyen") {
            client.get(url).use { response ->
                return parseMangaDetails(response.asJsoup(), response.request.url)
            }
        }
        return null
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val mangaId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')

        suspend fun requestChapters(): List<SChapter>? {
            val chapterUrl = "$baseUrl/api/comic/$mangaId/chapter?limit=-1"
            return client.get(chapterUrl, xhrHeaders).use { response ->
                parseChapterList(response, slug)
            }
        }

        var details = manga
        if (fetchDetails) {
            details = client.get(mangaUrl).use { response ->
                parseMangaDetails(response.asJsoup(), response.request.url)
            }
        }

        var chaptersList = chapters
        if (fetchChapters) {
            // Thử lần 1
            chaptersList = requestChapters() ?: run {
                // Nếu chưa fetch details ở trên thì gọi mồi để làm mới cookie
                if (!fetchDetails) {
                    client.get(mangaUrl).use { }
                }
                // Thử lần 2 sau khi đã làm mới cookie
                requestChapters() ?: throw Exception("Phiên làm việc hết hạn. Không thể tải danh sách chương!")
            }
        }

        return SMangaUpdate(details, chaptersList)
    }

    private fun parseMangaDetails(document: Document, requestUrl: HttpUrl): SManga = SManga.create().apply {
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.image")?.absUrl("src")
        status = parseStatus(document.selectFirst(".mb-1:contains(Trạng thái:) span")?.text())
        author = document.selectFirst(".mb-1:contains(Tác giả:) span")?.text()
        description = document.select(".v-card-text").joinToString { it.wholeText().trim() }

        val script = document.select("script").firstOrNull { it.data().contains("const comic = {") }?.data()
        val id = script?.let { COMIC_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("#comic-id-comment")?.attr("value")
        val nameEn = script?.let { COMIC_NAME_EN_REGEX.find(it)?.groupValues?.get(1) }
            ?: requestUrl.pathSegments.getOrNull(1)

        if (id != null && nameEn != null) {
            setUrlWithoutDomain("$id:$nameEn")
        }
    }

    private fun parseChapterList(response: Response, slug: String): List<SChapter>? {
        val chapterJson = runCatching { response.parseAs<ResultDto<ChapterListDto>>() }.getOrNull()
        val chapters = chapterJson?.result?.chapters ?: return null
        if (chapters.isEmpty()) return null
        return chapters.map { it.toSChapter(slug) }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang thực hiện", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/truyen/${manga.url.substringAfter(':')}"

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = chapter.url
        val slug = url.substringAfter("/truyen/").substringBefore("/chuong-")
        val numberChapter = url.substringAfter("/chuong-").substringBefore("#")
        val comicId = url.substringAfter("#")

        val body = FormBody.Builder()
            .add("comicId", comicId)
            .add("chapterNumber", numberChapter)
            .add("nameEn", slug)
            .build()

        suspend fun requestImages(): List<Page>? {
            return client.post("$baseUrl/api/chapter/loadAll", body).use { response ->
                val result = runCatching { response.parseAs<ResultDto<ImageListDto>>() }.getOrNull()
                val imageList = result?.result?.data ?: return@use null

                imageList.mapIndexed { i, imgUrl ->
                    val finalUrl = if (imgUrl.startsWith("/image/")) {
                        baseUrl + imgUrl
                    } else {
                        imgUrl
                    }
                    Page(i, imageUrl = finalUrl)
                }
            }
        }
        // Thử lần 1
        var pages = requestImages()
        // Nếu thất bại (do hết hạn cookie/phiên làm việc)
        if (pages == null) {
            // Gọi "mồi" đến trang chi tiết để làm mới cookie
            val mangaUrl = "$baseUrl/truyen/$slug"
            client.get(mangaUrl).use { /* Chỉ gọi để lấy cookie */ }
            // Thử lần 2 sau khi đã có cookie mới
            pages = requestImages()
        }

        return pages ?: throw Exception("Chưa đăng nhập trong WebView. Hoặc không có ảnh!")
    }

    private var tokenCache: String? = null

    private suspend fun getToken(): String? {
        tokenCache?.let { return it }

        preferences.getString(CUSTOM_TOKEN, null)
            ?.takeIf(String::isNotBlank)
            ?.also {
                tokenCache = it
                return it
            }

        return getLocalStorage(baseUrl, "Authorization")
            ?.takeIf(String::isNotBlank)
            ?.also {
                tokenCache = it
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

        val filters = mutableListOf(
            StatusList(getStatusList()),
            SortByList(getSortByList()),
        )

        if (genres.isNotEmpty()) {
            filters += GenreList(genres)
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = CUSTOM_TOKEN
            title = "Authorization Token"
            summary = "Enter token manually"
            dialogTitle = "Authorization Token"
            val currentToken = preferences.getString(CUSTOM_TOKEN, null)
            currentToken?.let { dialogMessage = if (it.isNotEmpty()) "Token: $it" else "Only show manually entered token, do not show token from WebView" }
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val CUSTOM_TOKEN = "custom_token"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng token mới nhập."
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val COMIC_ID_REGEX = Regex("""id:\s*"([^"]+)"""")
        private val COMIC_NAME_EN_REGEX = Regex("""nameEn:\s*`([^`]+)`""")
    }
}
