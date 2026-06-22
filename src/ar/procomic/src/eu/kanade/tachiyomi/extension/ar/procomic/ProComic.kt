package ar.procomic

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class ProComic : HttpSource(), ConfigurableSource {

    override val name = "ProComic"
    override val baseUrl = "https://procomic.pro"
    private val apiUrl = "https://app.procomic.pro"
    
    override val lang = "ar"
    override val supportsLatest = true

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val authenticatedRequest = if (getAuthToken() != null) {
                request.newBuilder()
                    .header("Authorization", "Bearer ${getAuthToken()}")
                    .build()
            } else {
                request
            }
            chain.proceed(authenticatedRequest)
        }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
        .add("Referer", "$baseUrl/")

    // ========== Popular Manga ==========
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/api/content?page=$page&limit=20", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.decodeFromString<ContentResponse>(response.body!!.string())
        val mangas = data.data.map { it.toSManga() }
        return MangasPage(mangas, data.meta.page < data.meta.pages)
    }

    // ========== Latest Updates ==========
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/api/content?page=$page&limit=20&sortBy=updated_at&order=desc", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ========== Search ==========
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.filterIsInstance<ContentTypeFilter>()
        val type = filterList.firstOrNull()?.selected?.value ?: ""
        
        val url = "$apiUrl/api/content".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
        
        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }
        
        if (type.isNotBlank()) {
            url.addQueryParameter("type", type)
        }
        
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ========== Manga Details ==========
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/api/content?page=1&limit=1&q=$mangaId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = json.decodeFromString<ContentResponse>(response.body!!.string())
        return data.data.firstOrNull()?.toSManga() ?: SManga.create()
    }

    // ========== Chapter List ==========
    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/api/chapters?contentId=$mangaId&page=1&limit=100", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = json.decodeFromString<ChaptersResponse>(response.body!!.string())
        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/api/chapters/${chapter.id}"
                name = if (chapter.title != null) {
                    "الفصل ${chapter.chapter_number}: ${chapter.title}"
                } else {
                    "الفصل ${chapter.chapter_number}"
                }
                chapter_number = chapter.chapter_number.toFloatOrNull() ?: 0f
                date_upload = parseDate(chapter.created_at)
                scanlator = chapter.translator
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ========== Page List ==========
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/api/chapters?id=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = json.decodeFromString<ChaptersResponse>(response.body!!.string())
        val chapter = data.chapters.firstOrNull() ?: return emptyList()
        
        val baseImageUrl = "https://${chapter.cdn_path}.procomic.pro"
        
        return chapter.metadata.images.mapIndexed { index, imagePath ->
            Page(index, "", "$baseImageUrl$imagePath")
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ========== Filters ==========
    override fun getFilterList(): FilterList {
        return FilterList(
            ContentTypeFilter()
        )
    }

    private class ContentTypeFilter : UriPartFilter(
        "نوع المحتوى",
        arrayOf(
            Pair("الكل", ""),
            Pair("مانها (صينية)", "manhua"),
            Pair("مانهوا (كورية)", "manhwa"),
            Pair("مانجا (يابانية)", "manga"),
            Pair("رواية", "novel"),
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val selected: Pair<String, String>
            get() = vals[state]
    }

    // ========== Login / WebView ==========
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val tokenPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = "auth_token"
            title = "رمز التحقق (Token)"
            summary = "أدخل رمز التحقق من المتصفح بعد تسجيل الدخول"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("auth_token", newValue as String).apply()
                true
            }
        }
        screen.addPreference(tokenPref)
    }

    private fun getAuthToken(): String? {
        return preferences.getString("auth_token", null)
    }

    // ========== Helpers ==========
    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ========== Data Classes ==========
    @Serializable
    data class ContentResponse(
        val data: List<Series>,
        val meta: Meta
    )

    @Serializable
    data class Meta(
        val total: Int,
        val page: Int,
        val limit: Int,
        val pages: Int
    )

    @Serializable
    data class Series(
        val id: Int,
        val title: String,
        val slug: String,
        val description: String? = null,
        val type: String,
        val progress: String? = null,
        val thumbnail: String? = null,
        val cdn_path: String? = null,
        val metadata: SeriesMetadata? = null,
        val updated_at: String? = null,
        val series_views: Int = 0
    ) {
        fun toSManga(): SManga = SManga.create().apply {
            url = "/series/$type/$id/$slug"
            title = this@Series.title
            thumbnail_url = this@Series.thumbnail
            description = this@Series.description ?: this@Series.metadata?.descriptions?.ar
            author = this@Series.metadata?.author
            artist = this@Series.metadata?.artist
            genre = this@Series.metadata?.genres?.joinToString(", ")
            status = when (this@Series.progress) {
                "مستمر" -> SManga.ONGOING
                "مكتمل" -> SManga.COMPLETED
                "متوقف" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    @Serializable
    data class SeriesMetadata(
        val author: String? = null,
        val artist: String? = null,
        val year: String? = null,
        val origin: String? = null,
        val genres: List<String>? = null,
        val descriptions: Descriptions? = null,
        val altTitles: List<String>? = null
    )

    @Serializable
    data class Descriptions(
        val ar: String? = null,
        val en: String? = null
    )

    @Serializable
    data class ChaptersResponse(
        val chapters: List<Chapter>,
        val total: Int? = null,
        val hasMore: Boolean = false
    )

    @Serializable
    data class Chapter(
        val id: Int,
        val content_id: Int,
        val chapter_number: String,
        val title: String? = null,
        val language: String,
        val translator: String? = null,
        val status: String,
        val cdn_path: String,
        val metadata: ChapterMetadata,
        val created_at: String? = null
    )

    @Serializable
    data class ChapterMetadata(
        val images: List<String>,
        val closeHours: String? = null,
        val lockDurationHours: String? = null,
        val queueStage: String? = null
    )
}
