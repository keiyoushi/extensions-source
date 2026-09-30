package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MoeTruyen : KeiSource() {
    private var currentActivity: WeakReference<Activity>? = null

    init {
        try {
            applicationContext.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(a: Activity) {
                        currentActivity = WeakReference(a)
                    }
                    override fun onActivityPaused(a: Activity) {
                        if (currentActivity?.get() === a) currentActivity = null
                    }
                    override fun onActivityDestroyed(a: Activity) {
                        if (currentActivity?.get() === a) currentActivity = null
                    }
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                },
            )
        } catch (_: Throwable) {
        }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Sec-Fetch-Dest", "document")
        set("Sec-Fetch-Mode", "navigate")
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(imgxInterceptor())
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views_desc")
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href^=/manga/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = getFullListTitle(element)
        thumbnail_url = element.selectFirst("img")?.let {
            it.absUrl("data-src").ifEmpty { it.absUrl("src") }
        }
    }

    private fun getFullListTitle(element: Element): String {
        val titleElement = element.selectFirst("h3")!!
        val titleAttr = titleElement.attr("title")
        if (titleAttr.isNotEmpty()) {
            return titleAttr
        }

        val titleText = titleElement.text()
        if (!titleText.endsWith("...")) {
            return titleText
        }

        val imageAlt = element.selectFirst("img")?.attr("alt")
            ?.removePrefix("Bìa ")
            ?.trim()
            ?.ifEmpty { null }

        return imageAlt ?: titleText
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.manga-card--list")
            .map(::mangaFromElement)

        val hasNextPage = document
            .selectFirst("nav[aria-label='Phân trang truyện'] a[aria-label='Trang sau']:not(.is-disabled)")
            ?.attr("href")
            ?.let { it != "#" }
            ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.ifEmpty { null }
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart()?.ifEmpty { null }
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        val includedGenres = genres.filter { it.isIncluded() }
        val excludedGenres = genres.filter { it.isExcluded() }
        val hasFilter = status != null || (sort != null && sort != "updated_desc") || includedGenres.isNotEmpty() || excludedGenres.isNotEmpty()

        if (query.isBlank() && !hasFilter) {
            return getLatestUpdates(page)
        }

        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }

                status?.let { addQueryParameter("status", it) }
                sort?.let { addQueryParameter("sort", it) }
                includedGenres.forEach { addQueryParameter("include", it.id) }
                excludedGenres.forEach { addQueryParameter("exclude", it.id) }
            }
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply { setUrlWithoutDomain("/manga/$slug") }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1.manga-detail-title")!!.text()
        author = document.select("p.manga-detail-meta-line")
            .firstOrNull { line ->
                line.selectFirst(".manga-detail-meta-label")
                    ?.text()
                    ?.contains("Tác giả")
                    ?: false
            }
            ?.select("a.inline-link")
            ?.joinToString { it.text() }
            ?.ifEmpty { null }
        genre = document.select(".manga-detail-genre-chips a.chip")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst("[data-description-content]")
            ?.text()
            ?.ifEmpty { null }
            ?: document.selectFirst(".manga-description__text")
                ?.text()
                ?.ifEmpty { null }
        status = parseStatus(document.selectFirst(".manga-status-pill")?.text())
        thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = if (fetchChapters) fetchChapterList(document) else chapters,
        )
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "Còn tiếp" -> SManga.ONGOING
        "Hoàn thành" -> SManga.COMPLETED
        "Tạm dừng" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(firstDocument: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedPages = mutableSetOf<String>()
        var currentPageUrl = firstDocument.location()
        var currentDocument = firstDocument

        while (visitedPages.add(currentPageUrl)) {
            chapters += parseChapterList(currentDocument)

            val nextChapterLinkElement: Element? = currentDocument.selectFirst(
                "nav[aria-label*='Phân trang chương'] a[aria-label='Trang chương sau']:not(.is-disabled)",
            )
            val nextChapterPageUrl: String? = nextChapterLinkElement?.let { link ->
                if (link.attr("href") == "#") {
                    null
                } else {
                    link.absUrl("href").ifEmpty { null }
                }
            }

            if (nextChapterPageUrl == null || visitedPages.contains(nextChapterPageUrl)) {
                break
            }

            currentPageUrl = nextChapterPageUrl
            currentDocument = client.get(currentPageUrl).asJsoup()
        }

        return chapters
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.chapter-list li.chapter a.chapter-link").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            val title = element.selectFirst(".chapter-num")!!.text()
            val locked = element.selectFirst(".chapter-lock-icon") != null
            name = if (locked) "🔒 $title" else title

            val chapterTime = element.selectFirst(".chapter-time")
            val relativeDate = chapterTime?.text()
            val absoluteDate = chapterTime?.attr("title")
                ?.substringAfter("Cập nhật", missingDelimiterValue = "")
                ?.trim()
                ?.ifEmpty { null }

            date_upload = parseRelativeDate(relativeDate).takeIf { it != 0L }
                ?: parseAbsoluteDate(absoluteDate)
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val number = numberRegex.find(dateStr)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            dateStr.contains("giây") -> number.seconds
            dateStr.contains("phút") -> number.minutes
            dateStr.contains("giờ") -> number.hours
            dateStr.contains("ngày") -> number.days
            dateStr.contains("tuần") -> (number * 7).days
            dateStr.contains("tháng") -> (number * 30).days
            dateStr.contains("năm") -> (number * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    private fun parseAbsoluteDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDate.parse(date, dateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}"
        var document = client.get(chapterUrl).asJsoup()

        if (isCommentLocked(document)) {
            unlockByComment(chapter, document, chapterUrl)
            document = client.get(chapterUrl).asJsoup()
            if (isCommentLocked(document)) {
                throw Exception("Không thể mở khóa chương này")
            }
        }

        val readerPages = document.selectFirst("[data-reader-lazy-pages]")
        val encryptedMedia = ImgxAccessClient.encryptedMedia(document)
        val totalPages = readerPages?.attr("data-reader-total-pages")?.toIntOrNull() ?: 0
        val accessUrl = readerPages?.attr("data-reader-imgx-access-url").orEmpty()
        val isImgx = accessUrl.isNotBlank()
        val plainUrls = plainPageUrls(document)
        val hasRealPlain = plainUrls.any { isRealPageUrl(it) }
        val shouldTryImgx = isImgx && (
            encryptedMedia.any { isRealPageUrl(it.storageKey) || isRealPageUrl(it.downloadUrl) } ||
                (!hasRealPlain && totalPages > 0)
            )

        if (shouldTryImgx) {
            try {
                val access = ImgxAccessClient(client, baseUrl, chapterUrl, document)
                val pages = access.fetchPages(encryptedMedia, totalPages)
                    .filter { isRealPageUrl(it.storageKey) && isRealPageUrl(it.downloadUrl) }
                if (pages.isNotEmpty()) {
                    pages.forEach { page ->
                        val grant = page.grant
                            ?: throw IllegalStateException("IMGX grant missing page=\${page.pageIndex + 1}")
                        imgxGrants[page.downloadUrl] = grant to page.storageKey
                    }
                    return pages
                        .sortedBy { it.pageIndex }
                        .mapIndexed { index, page -> Page(index, imageUrl = page.downloadUrl) }
                }
            } catch (e: Exception) {
                if (!hasRealPlain) throw e
            }
        }

        return plainUrls
            .filter { isRealPageUrl(it) }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun isCommentLocked(document: Document): Boolean {
        if (document.selectFirst("[data-reader-lazy-pages], img.page-media") != null) return false
        val text = document.body().text()
        return text.contains("Bạn phải bình luận") || text.contains("yêu cầu bình luận")
    }

    private suspend fun isLoggedIn(): Boolean = client.get("$baseUrl/auth/session", ensureSuccess = false).use { response ->
        response.isSuccessful && response.parseAs<AuthSession>().session != null
    }

    private suspend fun unlockByComment(chapter: SChapter, document: Document, chapterUrl: String) {
        if (!isLoggedIn()) {
            throw Exception(loginRequiredMessage)
        }

        val previousUrl = document.select("a[href*=/chapters/]")
            .firstOrNull { it.text().contains("chương trước", ignoreCase = true) }
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception(loginRequiredMessage)

        val comment = promptForComment(chapter.name)
        postChapterComment(previousUrl, chapterUrl, comment)
    }

    // Some chapters require comment in previous chapter to unlock
    private suspend fun promptForComment(chapterTitle: String): String {
        val activity = currentActivity?.get()
            ?: throw Exception(loginRequiredMessage)

        val deferred = CompletableDeferred<String>()
        var dialog: AlertDialog? = null

        try {
            withContext(Dispatchers.Main.immediate) {
                val input = EditText(activity).apply {
                    hint = "Bình luận"
                }
                val container = FrameLayout(activity).apply {
                    val pad = (16 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad / 2, pad, 0)
                    addView(input)
                }

                dialog = AlertDialog.Builder(activity)
                    .setTitle(chapterTitle)
                    .setMessage("Chương này yêu cầu bình luận ở chương trước\n\nBình luận vô nghĩa tài khoản sẽ bị khoá")
                    .setView(container)
                    .setPositiveButton("Mở khóa") { _, _ ->
                        val text = input.text.toString().trim()
                        if (text.isNotBlank()) {
                            deferred.complete(text)
                        } else {
                            deferred.completeExceptionally(Exception("Bình luận không được để trống"))
                        }
                    }
                    .setNegativeButton("Hủy") { _, _ ->
                        deferred.completeExceptionally(Exception("Đã hủy bình luận"))
                    }
                    .setOnCancelListener {
                        deferred.completeExceptionally(Exception("Đã đóng hộp thoại"))
                    }
                    .setOnDismissListener {
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(Exception("Đã đóng hộp thoại"))
                        }
                    }
                    .show()
            }

            return deferred.await()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                dialog?.takeIf { it.isShowing }?.dismiss()
            }
        }
    }

    private suspend fun postChapterComment(previousChapterUrl: String, referer: String, content: String) {
        val requestId = UUID.randomUUID().toString()
        val body = CommentRequest(content, requestId).toJsonRequestBody()
        val requestHeaders = headers.newBuilder()
            .set("Referer", referer)
            .set("Accept", "application/json")
            .set("Idempotency-Key", requestId)
            .build()
        val response = client.post("$previousChapterUrl/comments", requestHeaders, body, ensureSuccess = false)
        if (!response.isSuccessful) {
            val code = response.code
            val errorBody = response.body.string()
            val apiError = runCatching { errorBody.parseAs<ApiError>() }.getOrNull()
            throw Exception(
                apiError?.error?.takeIf { it.isNotBlank() }
                    ?: when (code) {
                        401, 403 -> loginRequiredMessage
                        else -> "Không thể mở khóa chương ($code)"
                    },
            )
        }
        response.close()
    }

    private fun isRealPageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.startsWith("data:")) return false
        val path = url.substringBefore('?')
        return !path.endsWith("/0.js") && !path.endsWith("/0.js/")
    }

    private fun plainPageUrls(document: Document): List<String> = readerImages(document)
        .map { element -> element.absUrl("data-src").ifEmpty { element.absUrl("src") } }
        .filter { it.isNotBlank() && !it.startsWith("data:") }

    private fun imgxInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val grantEntry = imgxGrants.remove(request.url.toString())
            ?: return@Interceptor chain.proceed(request)
        val (grant, storageKey) = grantEntry
        val response = chain.proceed(request)
        val encrypted = response.body.use { body ->
            val source = body.source()
            source.request(Long.MAX_VALUE)
            source.buffer.readByteArray()
        }
        if (
            encrypted.size <= 13 ||
            encrypted[0] != 0x49.toByte() ||
            encrypted[1] != 0x4D.toByte() ||
            encrypted[2] != 0x47.toByte() ||
            encrypted[3] != 0x58.toByte()
        ) {
            return@Interceptor response.newBuilder()
                .body(encrypted.toResponseBody(response.body.contentType()))
                .build()
        }
        val webp = try {
            ImgxCrypto.decodeProtectedPage(encrypted, grant, storageKey)
        } catch (e: Exception) {
            throw e
        }
        if (webp.size < 12 || webp[0] != 0x52.toByte() || webp[1] != 0x49.toByte()) {
        } else {
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", "image/webp")
            .header("Content-Length", webp.size.toString())
            .header("Cache-Control", "no-store")
            .body(webp.toResponseBody("image/webp".toMediaType()))
            .build()
    }

    private fun readerImages(document: Document): List<Element> {
        val all = document.select("img.page-media")
        val outsideNoscript = all.filterNot { element ->
            element.parents().any { parent -> parent.tagName().equals("noscript", ignoreCase = true) }
        }
        if (outsideNoscript.isNotEmpty()) {
            val first = outsideNoscript.first()
        }
        return outsideNoscript
    }

    private val imgxGrants = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<ImgxGrant, String>>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<ImgxGrant, String>>?): Boolean = size > 100
        },
    )

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/manga").asJsoup()
        .select(".filter-option[data-genre]")
        .mapNotNull { element ->
            val id = element.attr("data-genre").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = element.selectFirst(".filter-name")?.text()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val section = document.selectFirst("section[aria-labelledby=manga-related-similar-title]")
            ?: return emptyList()

        return section.select("article.manga-related-card").mapNotNull { card ->
            val link = card.selectFirst("a.manga-related-card__link[href^=/manga/]")
                ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }.distinctBy { it.url }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val numberRegex = Regex("""\d+""")
    private val loginRequiredMessage = "Chương này cần đăng nhập webview bằng tài khoản phù hợp để xem"
}
