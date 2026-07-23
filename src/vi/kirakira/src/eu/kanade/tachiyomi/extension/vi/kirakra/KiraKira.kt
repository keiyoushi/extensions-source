package eu.kanade.tachiyomi.extension.vi.kirakira

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import java.time.Instant as JavaInstant

@Source
abstract class KiraKira :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "https://api.${baseUrl.toHttpUrl().host}"
    private val imageUrl get() = "https://images.${baseUrl.toHttpUrl().host}"

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("Accept", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getGenreMangaList(page, sort = "views")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getGenreMangaList(page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return client.get(url, apiHeaders).parseAs<ComicListDto>().toMangasPage()
        }

        return getGenreMangaList(
            page = page,
            genreId = filters.firstInstanceOrNull<GenreFilter>()?.selected?.id ?: "all",
            sort = filters.firstInstanceOrNull<SortFilter>()?.selected,
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
        )
    }

    private suspend fun getGenreMangaList(
        page: Int,
        genreId: String = "all",
        sort: String? = null,
        status: String? = null,
    ): MangasPage {
        val url = "$apiUrl/genres/$genreId".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", genreId)
            addQueryParameter("page", page.toString())
            sort?.let { addQueryParameter("sort", it) }
            status?.let { addQueryParameter("status", it) }
        }.build()

        return client.get(url, apiHeaders).parseAs<ComicListDto>().toMangasPage()
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments.firstOrNull()) {
            "comics", "chapters" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null
        val manga = SManga.create().apply {
            title = slug
            setUrlWithoutDomain("/comics/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = extractComicSlug(manga.url) ?: throw Exception("Không tìm thấy mã truyện")
        val payload = client.get("$apiUrl/comics/$slug", apiHeaders).parseAs<ComicDetailsDto>()

        val updatedManga = SManga.create().apply {
            setUrlWithoutDomain("/comics/$slug")
            title = payload.title
            thumbnail_url = payload.thumbnail?.ifBlank { null } ?: payload.bannerImageUrl?.ifBlank { null }
            author = payload.authors
            status = parseStatus(payload.status)
            genre = payload.genres.mapNotNull { it.name }.joinToString().ifEmpty { null }
            description = payload.description
        }

        return SMangaUpdate(
            manga = updatedManga,
            chapters = parseChapters(payload, slug),
        )
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.equals("updating", true) -> SManga.ONGOING
        statusText.equals("ongoing", true) -> SManga.ONGOING
        statusText.equals("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun extractComicSlug(url: String): String? = comicSlugRegex.find(url)?.groupValues?.getOrNull(1) ?: url.substringBefore('/').takeIf { it.isNotBlank() }

    private fun parseChapters(payload: ComicDetailsDto, slug: String): List<SChapter> {
        val autoUnlock = isAutoUnlockEnabled

        return payload.chapters.mapNotNull { chapter ->
            val chapterId = chapter.id ?: return@mapNotNull null
            val chapterTitle = chapter.name ?: return@mapNotNull null
            val isLocked = (chapter.coinPrice ?: 0) > 0
            val unlockDate = chapter.unlockAt?.let(::formatUnlockDate)
            val chapterDate = chapter.unlockAt?.let(::parseDate) ?: 0L

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

    private fun formatUnlockDate(dateText: String): String? = Instant.parseOrNull(dateText)?.let {
        unlockLabelDateFormat.format(JavaInstant.ofEpochMilli(it.toEpochMilliseconds()).atZone(dateZone))
    }

    private fun parseDate(dateText: String): Long = Instant.parseOrNull(dateText)?.toEpochMilliseconds() ?: 0L

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        if (chapterUrl.queryParameter("is_locked") == "1") {
            throw Exception(lockedChapterMessage)
        }

        val chapterInfo = extractChapterInfo(chapter.url)
            ?: throw Exception("Không tìm thấy thông tin chương")

        val url = "$apiUrl/comics/${chapterInfo.first}/chapters/${chapterInfo.second}"
            .toHttpUrl()
            .newBuilder()
            .build()

        val response = client.get(url, apiHeaders, ensureSuccess = false)

        if (!response.isSuccessful) {
            if (response.code == 401 && isAutoUnlockEnabled) {
                response.close()
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
                throw Exception(lockedChapterMessage)
            }
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // Probe image URLs concurrently and stop at the first missing page.
    private suspend fun buildPageListFromPattern(comicSlug: String, chapterId: String): List<Page> = coroutineScope {
        val imageSlug = fetchImageSlug(comicSlug) ?: comicSlug
        val pages = mutableListOf<Page>()
        var index = 1

        while (index <= maxPageProbe) {
            val candidates = (index until minOf(index + pageProbeBatchSize, maxPageProbe + 1))
                .map { pageNumber ->
                    async {
                        pageNumber to findPageImageUrl(imageSlug, chapterId, pageNumber)
                    }
                }
                .awaitAll()

            for ((pageNumber, pageUrl) in candidates) {
                if (pageUrl == null) {
                    if (pages.isEmpty()) throw Exception("Không tìm thấy hình ảnh")
                    return@coroutineScope pages
                }

                pages.add(Page(pageNumber - 1, imageUrl = pageUrl))
            }

            index += candidates.size
        }

        if (pages.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        pages
    }

    private suspend fun findPageImageUrl(imageSlug: String, chapterId: String, pageNumber: Int): String? = imageExtensions.firstNotNullOfOrNull { extension ->
        val pageUrl = "$imageUrl/manga/$imageSlug/chapter-$chapterId/page-$pageNumber.$extension"
        client.head(pageUrl, headers, ensureSuccess = false).use {
            pageUrl.takeIf { _ ->
                it.isSuccessful && it.header("Content-Type")?.startsWith("image/") == true
            }
        }
    }

    private suspend fun fetchImageSlug(comicSlug: String): String? {
        val details = client.get("$apiUrl/comics/$comicSlug", apiHeaders).parseAs<ComicDetailsDto>()
        return details.thumbnail?.let { imageSlugRegex.find(it)?.groupValues?.getOrNull(1) }
    }

    private fun extractChapterInfo(url: String): Pair<String, String>? {
        val match = (chapterInfoRegex.find(url) ?: apiChapterRegex.find(url)) ?: return null
        val comicSlug = match.groupValues.getOrNull(1)
        val chapterId = match.groupValues.getOrNull(2)
        if (comicSlug.isNullOrBlank() || chapterId.isNullOrBlank()) return null
        return comicSlug to chapterId
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres", apiHeaders).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenreListDto>()?.data?.genres.orEmpty().mapNotNull { genre ->
            val id = genre.id ?: return@mapNotNull null
            val name = genre.name ?: return@mapNotNull null
            GenreOption(name, id)
        }
        return getFilters(genres)
    }

    // ============================= Preferences =============================

    private val preferences: SharedPreferences = getPreferences()

    private val isAutoUnlockEnabled: Boolean
        get() = preferences.getBoolean(keyAutoUnlockChapters, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = keyAutoUnlockChapters
            title = "Tự động mở khóa chương"
            summary = "Có thể gây chậm hoặc crash cân nhắc khi sử dụng."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private val maxPageProbe = 200
    private val pageProbeBatchSize = 10
    private val imageExtensions = listOf("webp", "jpg", "jpeg", "png")
    private val lockedChapterMessage = "Vui lòng đăng nhập bằng tài khoản phù hợp qua webview để xem chương này"
    private val keyAutoUnlockChapters = "autoUnlockChapters"
    private val comicSlugRegex = Regex("/comics/([^/?#]+)")
    private val chapterInfoRegex = Regex("(?:/chapters/)?([^/?#]+)/([^/?#]+)")
    private val apiChapterRegex = Regex("/comics/([^/?#]+)/chapters/([^/?#]+)")
    private val imageSlugRegex = Regex("/manga/([^/]+)/thumbnail")
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val unlockLabelDateFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)
}
