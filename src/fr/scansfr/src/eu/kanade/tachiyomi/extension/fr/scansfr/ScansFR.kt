package eu.kanade.tachiyomi.extension.fr.scansfr

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ScansFR :
    HttpSource(),
    ConfigurableSource {

    override val name = "ScansFR"
    override val baseUrl = "https://scansfr.com"
    override val lang = "fr"
    override val supportsLatest = true

    private val apiUrl = "https://api.scansfr.com"

    override val client = network.cloudflareClient.newBuilder()
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val imageHeaders = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
        .build()

    private val sessionId = java.util.UUID.randomUUID().toString()

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val showNsfw get() = preferences.getBoolean(PREF_SHOW_NSFW, false)

    private fun nsfwQueryParam() = if (showNsfw) "&nsfw=true" else "&isNsfw=false"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_NSFW
            title = "Afficher le contenu NSFW"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/api/v1/mangas?page=$page&sort=popular${nsfwQueryParam()}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListDto>()
        return MangasPage(
            data.mangas.map { it.toSManga() },
            data.page < data.totalPages,
        )
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/api/v1/mangas?page=$page&sort=updated${nsfwQueryParam()}", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================

    private var lastQuery = ""
    private var hasChaptersOnly = false

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        lastQuery = query
        hasChaptersOnly = false
        val url = "$apiUrl/api/v1/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("search", query)
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (query.isBlank()) addQueryParameter("sort", filter.selected)
                    is TypeFilter -> if (filter.selected.isNotEmpty()) addQueryParameter("type", filter.selected)
                    is StatusFilter -> if (filter.selected.isNotEmpty()) addQueryParameter("status", filter.selected)
                    is GenreFilter -> if (filter.selected.isNotEmpty()) addQueryParameter("genre", filter.selected)
                    is HasChaptersFilter -> hasChaptersOnly = filter.state
                    else -> {}
                }
            }
            if (showNsfw) addQueryParameter("nsfw", "true") else addQueryParameter("isNsfw", "false")
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListDto>()
        val filtered = if (hasChaptersOnly) data.mangas.filter { it.chapters > 0 } else data.mangas
        val mangas = filtered.map { it.toSManga() }
        val q = lastQuery.trim().lowercase()
        val sorted = if (q.isBlank()) {
            mangas
        } else {
            mangas.sortedBy { manga ->
                val t = manga.title.trim().lowercase()
                when {
                    t == q -> 0
                    t.startsWith(q) -> 1
                    t.contains(" $q") || t.contains("-$q") -> 2
                    t.contains(q) -> 3
                    else -> 4
                }
            }
        }
        return MangasPage(sorted, data.page < data.totalPages)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/")
        return GET("$apiUrl/api/v1/mangas/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDetailDto>()
        return SManga.create().apply {
            url = "/manga/${dto.slug}"
            title = dto.title
            thumbnail_url = "$apiUrl${dto.cover}"
            description = dto.description.takeIf { it.isNotBlank() }
            author = dto.author
            artist = dto.artist.takeIf { it != dto.author }
            genre = dto.tags.joinToString()
            status = dto.status.toSMangaStatus()
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val slug = manga.url.removePrefix("/manga/")
        return client.newCall(GET("$apiUrl/api/v1/mangas/$slug", headers))
            .asObservableSuccess()
            .map { response ->
                response.parseAs<MangaDetailDto>().chaptersList
                    .sortedByDescending { it.number }
                    .map { chapter ->
                        SChapter.create().apply {
                            url = "/$slug/${chapter.number}"
                            name = chapter.title
                            date_upload = chapter.date.parseDate()
                            chapter_number = chapter.number.toFloat()
                        }
                    }
            }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val parts = chapter.url.trim('/').split("/")
        val mangaSlug = parts[0]
        val chapterNumber = parts[1]
        val chapterId = "$mangaSlug-$chapterNumber"

        return client.newCall(GET("$apiUrl/api/v1/chapters/$chapterId", headers))
            .asObservableSuccess()
            .flatMap { chapterResponse ->
                val pageCount = chapterResponse.parseAs<ChapterDetailDto>().pageCount
                val body = buildJsonObject {
                    putJsonArray("pages") { (1..pageCount).forEach { add(it) } }
                    put("sessionId", sessionId)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)

                client.newCall(
                    POST("$apiUrl/api/v1/chapters/$chapterId/tokens", headers, body),
                ).asObservableSuccess()
            }
            .map { tokenResponse ->
                tokenResponse.parseAs<TokensResponseDto>().tokens
                    .sortedBy { it.pageNumber }
                    .map { token -> Page(token.pageNumber - 1, imageUrl = "$apiUrl/api/v1/images/${token.token}") }
            }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    // ============================= Filters ================================

    override fun getFilterList() = getFilters()

    // ============================= Utilities ==============================

    private fun String.toSMangaStatus() = when (this) {
        "ongoing", "En cours" -> SManga.ONGOING
        "completed", "Terminé" -> SManga.COMPLETED
        "hiatus", "En pause" -> SManga.ON_HIATUS
        "cancelled", "Abandonné" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String?.parseDate(): Long {
        if (this == null) return 0L
        return runCatching {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        private const val PREF_SHOW_NSFW = "pref_show_nsfw"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
