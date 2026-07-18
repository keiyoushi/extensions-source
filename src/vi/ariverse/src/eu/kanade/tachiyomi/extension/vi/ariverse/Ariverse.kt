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
import keiyoushi.utils.array
import keiyoushi.utils.get
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import kotlin.time.Instant

@Source
abstract class Ariverse :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = baseUrl.replace("https://", "https://be.") + "/api/v1"

    private val imageUrl get() = baseUrl.replace("https://", "https://img.")

    private val preferences: SharedPreferences = getPreferences()

    private val allowR18 get() = preferences.getBoolean("pref_r18", false)

    // ============================== Client ================================

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(authInterceptor())
        rateLimit(3)
    }

    // ============================== Auth ====================================

    private var cachedAuthToken: String? = null
    private var authChecked = false

    private suspend fun loadAuthToken() {
        if (authChecked) return
        authChecked = true
        cachedAuthToken = getLocalStorage(baseUrl, "token")
            ?.takeIf { it.isNotBlank() }
    }

    private fun authInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder().apply {
            cachedAuthToken?.let { header("Authorization", "Bearer $it") }
            if (original.url.encodedPath.startsWith("/api/")) {
                header("Accept", "application/json")
            }
        }.build()
        chain.proceed(request)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        loadAuthToken()
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
        loadAuthToken()
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
        loadAuthToken()
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
        loadAuthToken()
        val slug = url.pathSegments.lastOrNull() ?: return null

        val story = client.get("$apiUrl/stories/$slug")
            .parseAs<StoryDetailResponse>().data

        return SManga.create().apply {
            setUrlWithoutDomain("/comic/story/$slug")
            title = story.title
            thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
            author = story.author
            artist = story.artist
            description = story.description?.let { parseDescription(it) }
            genre = story.genres?.joinToString { it.name }
            status = parseStatus(story.status)
            initialized = true
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data["data"]?.array
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
        loadAuthToken()
        val slug = manga.url.substringAfterLast("/")

        val updatedManga: SManga
        val updatedChapters: List<SChapter>

        coroutineScope {
            val mangaDeferred = if (fetchDetails) {
                async {
                    val story = client.get("$apiUrl/stories/$slug")
                        .parseAs<StoryDetailResponse>().data

                    SManga.create().apply {
                        setUrlWithoutDomain("/comic/story/${story.slug}")
                        title = story.title
                        thumbnail_url = story.coverPath?.let { resolveCoverUrl(it) }
                        author = story.author
                        artist = story.artist
                        description = story.description?.let { parseDescription(it) }
                        genre = story.genres?.joinToString { it.name }
                        status = parseStatus(story.status)
                        memo = buildJsonObject {
                            story.genres?.firstOrNull()?.slug?.let { put("genreSlug", JsonPrimitive(it)) }
                        }
                    }
                }
            } else {
                null
            }

            val chaptersDeferred = if (fetchChapters) {
                async {
                    val chapterData = client.get("$apiUrl/stories/$slug/chapters")
                        .parseAs<ChapterListResponse>().data

                    chapterData.chapters
                        .sortedByDescending { it.number }
                        .map { chapter ->
                            SChapter.create().apply {
                                setUrlWithoutDomain("/comic/story/${chapterData.story.slug}/${chapter.slug}")
                                name = chapter.title ?: "${chapter.number.toInt()}"
                                chapter_number = chapter.number.toFloat()
                                date_upload = chapter.publishedAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
                            }
                        }
                }
            } else {
                null
            }

            updatedManga = mangaDeferred?.await() ?: manga
            updatedChapters = chaptersDeferred?.await() ?: chapters
        }

        return SMangaUpdate(
            manga = updatedManga,
            chapters = updatedChapters,
        )
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        loadAuthToken()
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
        val genreSlug = manga.memo["genreSlug"]?.string
            ?: client.get("$apiUrl/stories/$slug")
                .parseAs<StoryDetailResponse>().data.genres?.firstOrNull()?.slug
            ?: return emptyList()

        val result = client.get(
            "$apiUrl/stories".toHttpUrl().newBuilder()
                .addQueryParameter("type", "comic")
                .addQueryParameter("genre", genreSlug)
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

    private val loginWebviewMessage = "Vui lòng đăng nhập vào tài khoản phù hợp qua Webview để đọc chương này"
}
