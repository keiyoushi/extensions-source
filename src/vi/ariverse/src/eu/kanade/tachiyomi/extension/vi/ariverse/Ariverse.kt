package eu.kanade.tachiyomi.extension.vi.ariverse

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class Ariverse :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = baseUrl.replace("https://", "https://be.") + "/api/v1"

    private val imageUrl get() = baseUrl.replace("https://", "https://img.")

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    private var cachedAuthToken: String? = null

    private val allowR18 get() = preferences.getBoolean("pref_r18", false)

    // ============================== Client ================================

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(authInterceptor())
        rateLimit(3)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Referer", "$baseUrl/")
        set("Accept", "application/json")
    }

    // ============================== Auth ====================================

    private val authToken: String?
        @Synchronized
        get() = cachedAuthToken
            ?: runBlocking {
                runCatching {
                    getLocalStorage(baseUrl, "token")
                        ?.takeIf { it.isNotBlank() }
                        ?.also { cachedAuthToken = it }
                }.getOrNull()
            }

    private fun authInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val token = authToken

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        chain.proceed(request)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/stories/hot".toHttpUrl().newBuilder()
            .addQueryParameter("type", "comic")
            .addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            .addQueryParameter("limit", "50")
            .addQueryParameter("period", "week")
            .build()
        val result = client.get(url).parseAs<HotStoryListResponse>()

        val mangas = result.data.map { createManga(it) }

        return MangasPage(mangas, false)
    }

    private fun createManga(story: Story): SManga = SManga.create().apply {
        setUrlWithoutDomain("/comic/story/${story.slug}")
        title = story.title
        thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
    }

    private fun resolveCoverUrl(coverPath: String): String {
        if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
            return coverPath
        }
        return "$imageUrl/${coverPath.replace("\\", "/")}"
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/stories".toHttpUrl().newBuilder()
            .addQueryParameter("type", "comic")
            .addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", "50")
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url).parseAs<StoryListResponse>()
        return parseMangaPage(result)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$apiUrl/stories".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "comic")
            addQueryParameter("is_18_plus", if (allowR18) "1" else "0")
            addQueryParameter("per_page", "50")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val filterList = filters.ifEmpty { getFilterList() }

            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val selected = filter.selectedValues()
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected.joinToString(","))
                        }
                    }
                    is StatusFilter -> {
                        filter.toUriPart()?.let { addQueryParameter("status", it) }
                    }
                    is SortFilter -> {
                        filter.toSortValue()?.let { addQueryParameter("sort", it) }
                        filter.toOrderValue()?.let { addQueryParameter("order", it) }
                    }
                    else -> {}
                }
            }
        }.build()

        val result = client.get(url).parseAs<StoryListResponse>()
        return parseMangaPage(result)
    }

    private fun parseMangaPage(result: StoryListResponse): MangasPage {
        val mangas = result.data.map { createManga(it) }

        val hasNextPage = result.currentPage < result.lastPage

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.lastOrNull() ?: return null

        val story = runCatching {
            client.get("$apiUrl/stories/$slug")
                .parseAs<StoryDetailResponse>().data
        }.getOrNull()

        return SManga.create().apply {
            setUrlWithoutDomain("/comic/story/$slug")
            if (story != null) {
                title = story.title
                thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
                author = story.author
                artist = story.artist
                description = story.description?.let { parseDescription(it) }
                genre = story.genres?.joinToString { it.name }
                status = parseStatus(story.status)
            }
            initialized = true
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonObject?.get("data") as? JsonArray
            ?: return buildGenreFilter(null)
        return buildGenreFilter(genres)
    }

    // ============================== Details + Chapters ====================

    private fun parseDescription(html: String): String {
        val normalized = html
            .replace(brTagRegex, "\n")
            .replace("&nbsp;", " ")

        return Jsoup.parse(normalized).wholeText()
            .replace(horizontalSpaceRegex, " ")
            .replace(multiNewlineRegex, "\n")
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")

        val updatedManga = if (fetchDetails) {
            val story = runCatching {
                client.get("$apiUrl/stories/$slug")
                    .parseAs<StoryDetailResponse>().data
            }.getOrNull()

            if (story != null) {
                SManga.create().apply {
                    setUrlWithoutDomain("/comic/story/${story.slug}")
                    title = story.title
                    thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
                    author = story.author
                    artist = story.artist
                    description = story.description?.let { parseDescription(it) }
                    genre = story.genres?.joinToString { it.name }
                    status = parseStatus(story.status)
                }
            } else {
                manga
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val chapterData = client.get("$apiUrl/stories/$slug/chapters")
                .parseAs<ChapterListResponse>().data

            chapterData.chapters
                .sortedByDescending { it.number }
                .map { chapter ->
                    SChapter.create().apply {
                        setUrlWithoutDomain("/comic/story/${chapterData.story.slug}/${chapter.slug}")
                        name = chapter.title ?: "${chapter.number.toInt()}"
                        chapter_number = chapter.number.toFloat()
                        date_upload = chapter.publishedAt?.let { dateFormat.tryParse(it) } ?: 0L
                    }
                }
        } else {
            chapters
        }

        return SMangaUpdate(
            manga = updatedManga,
            chapters = updatedChapters,
        )
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapter.url.trim('/').split("/")
        val storySlug = parts.getOrElse(2) { "" }
        val chapterSlug = parts.getOrElse(3) { "" }
        val url = "$apiUrl/stories/$storySlug/chapters/$chapterSlug".toHttpUrl()
        val chapterDetail = client.get(url).parseAs<ChapterDetailResponse>().data

        if (chapterDetail.contentLocked) {
            throw Exception(loginWebviewMessage)
        }

        val content = chapterDetail.content.orEmpty()

        if (content.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return content.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Related ===============================

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val slug = manga.url.substringAfterLast("/")
        val story = runCatching {
            client.get("$apiUrl/stories/$slug")
                .parseAs<StoryDetailResponse>().data
        }.getOrNull() ?: return emptyList()

        val firstGenreSlug = story.genres?.firstOrNull()?.slug ?: return emptyList()

        val result = client.get(
            "$apiUrl/stories".toHttpUrl().newBuilder()
                .addQueryParameter("type", "comic")
                .addQueryParameter("genre", firstGenreSlug)
                .addQueryParameter("per_page", "12")
                .addQueryParameter("page", "1")
                .build(),
        ).parseAs<StoryListResponse>()

        return result.data
            .filter { it.slug != slug }
            .map { createManga(it) }
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = "pref_r18"
            title = "Hiển thị nội dung 18+"
            summary = "Cần đăng nhập bằng webiew để sử dụng tính năng này."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val horizontalSpaceRegex = Regex("[\\t\\x0B\\f\\r ]+")
    private val multiNewlineRegex = Regex("\\n{2,}")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    private val loginWebviewMessage = "Vui lòng đăng nhập vào tài khoản phù hợp qua Webview để đọc chương này"
}
