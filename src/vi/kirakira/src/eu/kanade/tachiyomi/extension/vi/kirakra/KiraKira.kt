package eu.kanade.tachiyomi.extension.vi.kirakira

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class KiraKira :
    HttpSource(),
    ConfigurableSource {
    override val name = "KiraKira"
    override val lang = "vi"
    override val baseUrl = "https://truyenkira.com"
    override val supportsLatest = true

    private val apiUrl = "https://api.${baseUrl.toHttpUrl().host}"

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .set("Accept", "application/json")
            .set("Origin", baseUrl)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // Strip "wv" from User-Agent so Google login works in this source.
    // Google deny login when User-Agent contains the WebView token.
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/top".toHttpUrl().newBuilder()
            .addQueryParameter("status", "all")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<ComicListDto>()
        return payload.toMangasPage()
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/recent-update-comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val payload = response.parseAs<ComicListDto>()
        return payload.toMangasPage()
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, apiHeaders)
        }

        val genreId = filters.firstInstanceOrNull<GenreFilter>()?.selected?.id
        if (genreId != null) {
            val url = "$apiUrl/genres/$genreId".toHttpUrl().newBuilder()
                .addQueryParameter("type", genreId)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, apiHeaders)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<ComicListDto>()
        return payload.toMangasPage()
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = extractComicSlug(manga.url) ?: throw Exception("Không tìm thấy mã truyện")
        val url = "$apiUrl/comics/$slug".toHttpUrl().newBuilder().build()
        return GET(url, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val payload = response.parseAs<ComicDetailsDto>()

        return SManga.create().apply {
            title = payload.title ?: throw Exception("Không tìm thấy tên truyện")
            thumbnail_url = payload.thumbnail?.ifBlank { null } ?: payload.banner_image_url?.ifBlank { null }
            author = "Unknown"
            status = parseStatus(payload.status)
            genre = payload.genres.mapNotNull { it.name }.joinToString().ifEmpty { null }
            description = payload.description
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.equals("updating", true) -> SManga.ONGOING
        statusText.equals("ongoing", true) -> SManga.ONGOING
        statusText.equals("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun extractComicSlug(url: String): String? = COMIC_SLUG_REGEX.find(url)?.groupValues?.getOrNull(1)

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<ComicDetailsDto>()
        val slug = extractComicSlug(response.request.url.toString()) ?: payload.id
            ?: throw Exception("Không tìm thấy mã truyện")
        val autoUnlock = isAutoUnlockEnabled

        return payload.chapters.mapNotNull { chapter ->
            val chapterId = chapter.id ?: return@mapNotNull null
            val chapterTitle = chapter.name ?: return@mapNotNull null
            val isLocked = (chapter.coinPrice ?: 0) > 0
            val unlockDate = chapter.unlockAt?.let(::formatUnlockDate)
            val chapterDate = chapter.unlockAt?.let(ISO_DATE_FORMAT::tryParse) ?: 0L

            SChapter.create().apply {
                name = if (autoUnlock) chapterTitle else buildChapterName(chapterTitle, isLocked, unlockDate)
                val chapterUrl = buildString {
                    append("/chapters/$slug/$chapterId")
                    if (isLocked && !autoUnlock) {
                        append("?is_locked=1")
                    }
                }
                setUrlWithoutDomain(chapterUrl)
                date_upload = chapterDate
            }
        }
    }

    private fun buildChapterName(chapterName: String, isLocked: Boolean, unlockDate: String?): String {
        if (!isLocked) return chapterName

        return buildString {
            append("\uD83D\uDD12")
            append(" ")
            append(chapterName)
            if (unlockDate != null) {
                append(" [Mở khóa: ")
                append(unlockDate)
                append("]")
            }
        }
    }

    private fun formatUnlockDate(dateText: String): String? {
        val unlockMillis = ISO_DATE_FORMAT.tryParse(dateText)
        if (unlockMillis == 0L) return null
        return UNLOCK_LABEL_DATE_FORMAT.format(Date(unlockMillis))
    }

    // ============================== Pages =================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservable().map(::pageListParse)

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        if (chapterUrl.queryParameter("is_locked") == "1") {
            throw Exception(LOCKED_CHAPTER_MESSAGE)
        }

        val chapterInfo = extractChapterInfo(chapter.url)
            ?: throw Exception("Không tìm thấy thông tin chương")

        val url = "$apiUrl/comics/${chapterInfo.first}/chapters/${chapterInfo.second}"
            .toHttpUrl()
            .newBuilder()
            .build()

        return GET(url, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterInfo = extractChapterInfo(response.request.url.encodedPath)
            ?: throw Exception("Không tìm thấy thông tin chương")

        if (!response.isSuccessful) {
            if (response.code == 401 && isAutoUnlockEnabled) {
                return buildPageListFromPattern(chapterInfo.first, chapterInfo.second)
            }
            val error = runCatching { response.parseAs<ApiErrorDto>() }.getOrNull()
            throw Exception(error?.message ?: "Không thể tải dữ liệu chương")
        }

        val payload = response.parseAs<ChapterPagesDto>()
        val imageUrls = payload.images.mapNotNull { it.src?.ifBlank { null } }

        if (imageUrls.isEmpty()) {
            if (isAutoUnlockEnabled) {
                return buildPageListFromPattern(chapterInfo.first, chapterInfo.second)
            }
            if ((payload.coinPrice ?: 0) > 0 && payload.isPurchased == false) {
                throw Exception(LOCKED_CHAPTER_MESSAGE)
            }
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    /**
     * Build page list by constructing predictable image URLs.
     * Images are hosted at: {baseUrl}/manga/{imageSlug}/chapter-{id}/page-{i}.jpg
     * Probes pages with HEAD requests until the response Content-Type is not an image.
     */
    private fun buildPageListFromPattern(comicSlug: String, chapterId: String): List<Page> {
        val imageSlug = fetchImageSlug(comicSlug) ?: comicSlug
        val pages = mutableListOf<Page>()
        var index = 1

        while (index <= MAX_PAGE_PROBE) {
            val imageUrl = "$baseUrl/manga/$imageSlug/chapter-$chapterId/page-$index.jpg"
            val headRequest = Request.Builder().url(imageUrl).headers(headers).head().build()
            val isImage = client.newCall(headRequest).execute().use {
                it.isSuccessful && it.header("Content-Type")?.startsWith("image/") == true
            }

            if (!isImage) break

            pages.add(Page(index - 1, imageUrl = imageUrl))
            index++
        }

        if (pages.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return pages
    }

    private fun fetchImageSlug(comicSlug: String): String? {
        val url = "$apiUrl/comics/$comicSlug".toHttpUrl().newBuilder().build()
        val response = client.newCall(GET(url, apiHeaders)).execute()
        val details = runCatching { response.parseAs<ComicDetailsDto>() }.getOrNull() ?: return null
        return details.thumbnail?.let { IMAGE_SLUG_REGEX.find(it)?.groupValues?.getOrNull(1) }
    }

    private fun extractChapterInfo(url: String): Pair<String, String>? {
        val match = (CHAPTER_INFO_REGEX.find(url) ?: API_CHAPTER_REGEX.find(url)) ?: return null
        val comicSlug = match.groupValues.getOrNull(1)
        val chapterId = match.groupValues.getOrNull(2)
        if (comicSlug.isNullOrBlank() || chapterId.isNullOrBlank()) return null
        return comicSlug to chapterId
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // =========================== Preferences ==============================

    private val preferences: SharedPreferences = getPreferences()

    private val isAutoUnlockEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_UNLOCK_CHAPTERS, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_AUTO_UNLOCK_CHAPTERS
            title = "Tự động mở khóa chương"
            summary = "Có thể gây chậm hoặc crash cân nhắc khi sử dụng."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun ComicListDto.toMangasPage(): MangasPage {
        val mangas = comics.mapNotNull { comic ->
            val slug = comic.id ?: return@mapNotNull null
            val mangaTitle = comic.title ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                setUrlWithoutDomain("/comics/$slug")
                thumbnail_url = comic.thumbnail?.ifBlank { null } ?: comic.banner_image_url?.ifBlank { null }
            }
        }

        return MangasPage(mangas, current_page < total_pages)
    }

    companion object {
        private const val MAX_PAGE_PROBE = 200
        private const val LOCKED_CHAPTER_MESSAGE = "Vui lòng đăng nhập bằng tài khoản phù hợp qua webview để xem chương này"
        private const val KEY_AUTO_UNLOCK_CHAPTERS = "autoUnlockChapters"

        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val COMIC_SLUG_REGEX = Regex("/comics/([^/?#]+)")
        private val CHAPTER_INFO_REGEX = Regex("/chapters/([^/?#]+)/([^/?#]+)")
        private val API_CHAPTER_REGEX = Regex("/comics/([^/?#]+)/chapters/([^/?#]+)")
        private val IMAGE_SLUG_REGEX = Regex("/manga/([^/]+)/thumbnail")

        private val ISO_DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private val UNLOCK_LABEL_DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
