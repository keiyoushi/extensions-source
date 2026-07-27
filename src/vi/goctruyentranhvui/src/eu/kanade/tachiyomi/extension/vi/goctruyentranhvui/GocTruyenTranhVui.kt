package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import android.text.InputType
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
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.text.isNotBlank

@Source
abstract class GocTruyenTranhVui :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api/v2"

    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
        addInterceptor(::authInterceptor)
    }

    private val xhrHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(request)
        }

        val token = tokenCache

        return chain.proceed(
            request.newBuilder().apply {
                header("X-Requested-With", "XMLHttpRequest")

                token?.let {
                    header("Authorization", it)
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
        getToken()
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
        val result = res.result ?: throw Exception(res.errorMessage ?: "Lỗi tải danh sách")
        val hasNextPage = result.next
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrlOrNull()?.host) return null
        if (url.pathSegments.size < 2 || url.pathSegments[0] != "truyen") return null
        return parseMangaDetails(client.get(url).asJsoup(), url)
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = client.get(getMangaUrl(manga).toHttpUrl()).use { response ->
        val document = response.asJsoup()
        val newManga = parseMangaDetails(document, response.request.url)

        val newChapters = if (fetchChapters) {
            parseChapterList(newManga)
        } else {
            chapters
        }

        SMangaUpdate(
            manga = newManga,
            chapters = newChapters,
        )
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

    private suspend fun parseChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')
        val result = client.get("$baseUrl/api/comic/$mangaId/chapter?limit=-1", xhrHeaders)
            .parseAs<ResultDto<ChapterListDto>>()

        val chapters = result.result?.chapters ?: throw Exception(result.errorMessage ?: "Phiên làm việc đã hết hạn, vui lòng tải lại.")

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
        getToken()
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
            client.get(mangaUrl).close()
            // Thử lần 2 sau khi đã có cookie mới
            pages = requestImages()
        }

        return pages ?: throw Exception("Chưa đăng nhập trong WebView. Hoặc không có ảnh!")
    }

    private var tokenCache: String? = null
    private var tokenChecked = false

    private suspend fun getToken(): String? {
        if (tokenChecked) return tokenCache

        preferences.getString(CUSTOM_TOKEN, null)
            ?.takeIf(String::isNotBlank)
            ?.also {
                tokenCache = it
                tokenChecked = true
                return it
            }

        tokenCache = runCatching { getLocalStorage(baseUrl, "Authorization") }.getOrNull()
            ?.takeIf(String::isNotBlank)
        tokenChecked = true
        return tokenCache
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        getToken()
        return client.get("$baseUrl/api/category", xhrHeaders).parseAs()
    }

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
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, newValue ->
                val token = newValue as String
                tokenCache = token.takeIf(String::isNotBlank)
                tokenChecked = true
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val CUSTOM_TOKEN = "custom_token"
        private val COMIC_ID_REGEX = Regex("""id:\s*"([^"]+)"""")
        private val COMIC_NAME_EN_REGEX = Regex("""nameEn:\s*`([^`]+)`""")
    }
}
