package eu.kanade.tachiyomi.extension.ru.ninegrid

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class NineGrid :
    HttpSource(),
    ConfigurableSource {

    override val name = "NineGrid"
    override val lang = "ru"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        val app: Application by injectLazy()
        app.getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl: String
        get() = preferences.getString(PREF_BASE_URL, DEFAULT_BASE_URL)!!.trimEnd('/')

    private val apiKey: String
        get() = preferences.getString(PREF_API_KEY, "") ?: ""

    private val apiBase: String
        get() = "$baseUrl/api/external/v1"

    // ============================================
    // Headers
    // ============================================

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            add("Authorization", "Bearer $apiKey")
        }
    }

    // ============================================
    // Popular (sorted by translated issue count)
    // ============================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "popular")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.parseJson()
        val content = body["content"]!!.jsonArray
        val totalPages = body["totalPages"]!!.jsonPrimitive.int
        val page = body["page"]!!.jsonPrimitive.int

        val mangas = content.map { it.jsonObject.toSManga(baseUrl) }
        return MangasPage(mangas, page + 1 < totalPages)
    }

    // ============================================
    // Latest (sorted by recently updated)
    // ============================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "latest")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================================
    // Search
    // ============================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is PublisherFilter -> if (filter.state.isNotBlank()) {
                    url.addQueryParameter("publisher", filter.state)
                }
                is YearFilter -> if (filter.state.isNotBlank()) {
                    url.addQueryParameter("year", filter.state)
                }
                is GenreFilter -> filter.state.filter { it.state }.forEach { genre ->
                    url.addQueryParameter("genre", genre.name)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================================
    // Manga Details
    // ============================================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBase/series/${manga.url.substringAfterLast("/")}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val obj = response.parseJson()
        return SManga.create().apply {
            title = obj["name"]!!.jsonPrimitive.content
            thumbnail_url = "$baseUrl/api/external/v1/series/${obj["id"]!!.jsonPrimitive.int}/thumbnail"
            description = obj["description"]?.jsonPrimitive?.contentOrNull
            author = obj["publisherName"]?.jsonPrimitive?.contentOrNull
            genre = obj["genres"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content }
            status = when (obj["status"]?.jsonPrimitive?.contentOrNull) {
                "Continuing" -> SManga.ONGOING
                "Ended" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    // ============================================
    // Chapter List (issues with translations)
    // ============================================

    override fun chapterListRequest(manga: SManga): Request = GET("$apiBase/series/${manga.url.substringAfterLast("/")}/issues", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.parseJson()
        val issues = body["issues"]!!.jsonArray
        val chapters = mutableListOf<SChapter>()

        for (issue in issues) {
            val obj = issue.jsonObject
            val issueNumber = obj["number"]!!.jsonPrimitive.content
            val issueName = obj["name"]?.jsonPrimitive?.contentOrNull
            val translations = obj["translations"]!!.jsonArray

            for (translation in translations) {
                val t = translation.jsonObject
                val translationId = t["id"]!!.jsonPrimitive.content
                val teamNames = t["teamNames"]!!.jsonArray
                    .map { it.jsonPrimitive.content }
                val teamLabel = if (teamNames.isNotEmpty()) teamNames.joinToString(", ") else "Unknown"

                chapters.add(
                    SChapter.create().apply {
                        url = "/translations/$translationId/pages"
                        name = buildString {
                            append("#$issueNumber")
                            if (!issueName.isNullOrBlank()) append(" — $issueName")
                            if (translations.size > 1) append(" [$teamLabel]")
                        }
                        chapter_number = try {
                            issueNumber.replace(Regex("^annual\\s*", RegexOption.IGNORE_CASE), "1000.").toFloat()
                        } catch (_: Exception) {
                            -1f
                        }
                        date_upload = try {
                            t["createdAt"]!!.jsonPrimitive.content.let {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(it)?.time ?: 0L
                            }
                        } catch (_: Exception) {
                            0L
                        }
                        scanlator = teamLabel
                    },
                )
            }
        }

        // Mihon shows chapters in reverse order (newest first), but we sorted oldest first
        // So reverse to show newest first
        return chapters.reversed()
    }

    // ============================================
    // Page List
    // ============================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBase${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.parseJson()
        val pages = body["pages"]!!.jsonArray
        return pages.map { page ->
            val obj = page.jsonObject
            Page(
                index = obj["index"]!!.jsonPrimitive.int,
                imageUrl = obj["url"]!!.jsonPrimitive.content,
            )
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ============================================
    // Filters
    // ============================================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        PublisherFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )

    // ============================================
    // Preferences (ConfigurableSource)
    // ============================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL
            title = "URL сервера"
            summary = "По умолчанию: $DEFAULT_BASE_URL"
            setDefaultValue(DEFAULT_BASE_URL)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "API-ключ"
            summary = "Для трекинга прогресса (Настройки → API-ключи)"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    // ============================================
    // Helpers
    // ============================================

    private fun Response.parseJson(): JsonObject = json.parseToJsonElement(body.string()).jsonObject

    private fun JsonObject.toSManga(baseUrl: String): SManga = SManga.create().apply {
        val id = this@toSManga["id"]!!.jsonPrimitive.int
        url = "/series/$id"
        title = this@toSManga["name"]!!.jsonPrimitive.content
        thumbnail_url = "$baseUrl/api/external/v1/series/$id/thumbnail"
        description = this@toSManga["description"]?.jsonPrimitive?.contentOrNull
        author = this@toSManga["publisherName"]?.jsonPrimitive?.contentOrNull
        genre = this@toSManga["genres"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content }
        status = when (this@toSManga["status"]?.jsonPrimitive?.contentOrNull) {
            "Continuing" -> SManga.ONGOING
            "Ended" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://9grid.cc"
        private const val PREF_BASE_URL = "pref_base_url"
        private const val PREF_API_KEY = "pref_api_key"
        const val SEARCH_PREFIX = "id:"
    }
}
