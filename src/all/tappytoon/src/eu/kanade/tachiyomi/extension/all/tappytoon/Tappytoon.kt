package eu.kanade.tachiyomi.extension.all.tappytoon

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Tappytoon :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "fr", "de"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        val mime = res.headers["Content-Type"]
        if (res.isSuccessful || res.code == 401) {
            if (mime != "application/octet-stream") {
                return@addInterceptor res
            }
            val type = IMG_CONTENT_TYPE.toMediaType()
            val body = res.body.source().asResponseBody(type)
            return@addInterceptor res.newBuilder().body(body).build()
        }
        if (mime == "application/json") {
            throw IOException(res.parseAs<ErrorResponse>().message)
        }
        res.close()
        throw HttpException(res.code)
    }

    private var apiHeaders: Headers? = null

    private var apiHeadersTokenState: String? = null

    private val apiHeadersLock = Mutex()

    private suspend fun apiHeaders(): Headers = apiHeadersLock.withLock {
        apiHeaders?.takeIf { tokenState() == apiHeadersTokenState }?.let { return it }

        val axiosHeaders = client.get("$baseUrl/comics/discover", Fetch.DOCUMENT).asJsoup().extractNextJs<AxiosHeaders>()!!
        headers.newBuilder()
            .set("Authorization", axiosHeaders.authorization)
            .set("X-Device-Uuid", axiosHeaders.deviceUuid)
            .build()
            .also {
                apiHeaders = it
                apiHeadersTokenState = tokenState()
            }
    }

    // The site's token state cookie, replaced by a login in WebView
    private fun tokenState() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == "__TST__" }?.value

    private suspend fun apiGet(url: HttpUrl, isRetry: Boolean = false): Response {
        val res = client.get(url, apiHeaders(), Fetch.XHR, ensureSuccess = isRetry)
        if (res.code != 401 || isRetry) return res

        res.close()
        apiHeaders = null
        return apiGet(url, true)
    }

    private var nextUrl: String? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegment("comics")
            .addEncodedQueryParameter("sort_by", "trending")
            // Sort is only available for completed series
            .addEncodedQueryParameter("filter", "completed")
            .addEncodedQueryParameter("locale", lang)
            .build()

        return parseComics(apiGet(url))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegment("comics")
            .addEncodedQueryParameter("day_of_week", day)
            .addEncodedQueryParameter("locale", lang)
            .build()

        return parseComics(apiGet(url))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = nextUrl?.takeIf { page > 1 }?.toHttpUrl() ?: apiUrl.newBuilder().run {
            addEncodedPathSegments("comics")
            addEncodedQueryParameter("locale", lang)
            val genre = filters.firstInstanceOrNull<Genre>()
            if (genre != null && genre.state != 0) {
                addEncodedQueryParameter("genre", genre.alias)
                addEncodedQueryParameter("limit", "50")
            } else if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            build()
        }

        val response = apiGet(url)
        val link = response.headers["Link"]
        nextUrl = link?.substringAfter('<')?.substringBefore('>')
        return parseComics(response, link != null)
    }

    private fun parseComics(response: Response, hasNext: Boolean = false) = MangasPage(
        response.parseAs<List<Comic>>().accessible.map { it.toSManga(intl["mature_login_note"]) },
        hasNext,
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.slug}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicUrl = apiUrl.newBuilder()
            .addPathSegment("comics")
            .addPathSegment(manga.id)
            .addEncodedQueryParameter("locale", lang)
            .build()
        val comic = apiGet(comicUrl).parseAs<Comic>()

        return SMangaUpdate(comic.toSManga(intl["mature_login_note"])) {
            if (fetchChapters) chapterList(comicUrl, comic.ageRating.canUserRead) else chapters
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val base = baseUrl.toHttpUrl()
        if (url.host != base.host || url.pathSegments.getOrNull(0) != lang || url.pathSegments.getOrNull(1) !in setOf("comics", "book")) return null
        val slug = url.pathSegments.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null

        // An unknown slug redirects to the site's search, so match it exactly
        val bookUrl = base.newBuilder().addPathSegment("book").addPathSegment(slug).build()
        val comic = client.get(bookUrl, Fetch.DOCUMENT).asJsoup().extractNextJs<BookState>()!!
            .entities.comics.values.firstOrNull { it.slug == slug } ?: return null

        val manga = SManga.create().apply { this.url = comic.toString() }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private suspend fun chapterList(comicUrl: HttpUrl, canUserRead: Boolean): List<SChapter> {
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)
        if (!canUserRead && !showLocked) return emptyList()

        val url = comicUrl.newBuilder()
            .addEncodedPathSegment("chapters")
            .build()

        return apiGet(url).parseAs<List<Chapter>>().accessible.filter { showLocked || !it.isLocked }.map {
            SChapter.create().apply {
                name = if (!canUserRead || it.isLocked) "$it \uD83D\uDD12" else it.toString()
                this.url = it.id.toString()
                chapter_number = it.order + 1f
                date_upload = Instant.tryParse(it.willAccessibleAt)
            }
        }.asReversed()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapters/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val media = if (preferences.getBoolean(PREF_HD_MODE, false)) {
            // Only comics marked for HD have super_high images (others answer 404), and the API can refuse it, so fall back.
            try {
                contents(chapter, "super_high")
            } catch (_: IOException) {
                contents(chapter, "high")
            }
        } else {
            contents(chapter, "high")
        }
        return media.mapIndexed { idx, img ->
            Page(idx, imageUrl = img.toString())
        }
    }

    private suspend fun contents(chapter: SChapter, variant: String): Media {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegments("content-delivery/contents")
            .addEncodedQueryParameter("chapterId", chapter.url)
            .addEncodedQueryParameter("variant", variant)
            .addEncodedQueryParameter("locale", lang)
            .build()
        return apiGet(url).parseAs<Media>()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = intl["pref_show_locked_title"]
            summary = intl["pref_show_locked_summary"]
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HD_MODE
            title = intl["pref_hd_mode_title"]
            summary = intl["pref_hd_mode_summary"]
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTE: can't be used with text search!"),
        Genre(genres.keys.toTypedArray()),
    )

    class Genre(values: Array<String>) : Filter.Select<String>("Genre", values)

    private inline val Genre.alias: String
        get() = genres[values[state]]!!

    private inline val SManga.slug: String
        get() = url.substringBefore('|')

    private inline val SManga.id: String
        get() = url.substringAfter('|')

    private val day: String
        get() = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ENGLISH)

    companion object {
        private const val IMG_CONTENT_TYPE = "image/jpeg"

        private const val PREF_HD_MODE = "pref_hd_mode"

        private const val PREF_SHOW_LOCKED = "pref_show_locked"

        private val apiUrl: HttpUrl = "https://api-global.tappytoon.com".toHttpUrl()

        private val genres = mapOf(
            "<select>" to "",
            "Action" to "action",
            "Romance" to "romance",
            "Fantasy" to "fantasy",
            "School" to "school",
            "Slice of Life" to "slice",
            "BL" to "bl",
            "Comedy" to "comedy",
            "GL" to "gl",
        )
    }
}
