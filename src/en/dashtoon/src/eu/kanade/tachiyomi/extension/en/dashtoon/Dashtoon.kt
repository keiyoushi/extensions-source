package eu.kanade.tachiyomi.extension.en.dashtoon

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Source
abstract class Dashtoon :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences = getPreferences()

    private val sessionLock = Mutex()

    private var cachedToken: String? = null

    private var cachedTokenExpiry = 0L

    override suspend fun getPopularManga(page: Int) = MangasPage(fetchCatalogue(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The site has no search, so its catalogue is filtered by title
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(fetchCatalogue().filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    // The comics page lists every series
    private suspend fun fetchCatalogue(): List<SManga> = client.get("$baseUrl/comics", Fetch.DOCUMENT).asJsoup()
        .select("a[href^=\"/comics/read/\"]:has(img)")
        .map { link ->
            val image = link.selectFirst("img")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = image.attr("alt")
                thumbnail_url = image.absUrl("src").withoutResizing()
            }
        }

    // Covers go through Cloudflare's image resizing: keep the original
    private fun String.withoutResizing() = if ("/cdn-cgi/image/" in this) substringAfter("/cdn-cgi/image/").substringAfter('/') else this

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        // Series are /comics/read/<slug>_<id> and episodes /comics/read/<id>/episodes/<id>
        if (segments.size < 3 || segments[0] != "comics" || segments[1] != "read" || !segments[2].substringAfterLast('_').startsWith("SHO")) return null
        return fetchDetails("/comics/read/${segments[2]}")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchDetails(manga.url) } else null
        val episodes = if (fetchChapters) async { fetchEpisodes(manga.url.substringAfterLast('_').substringAfterLast('/')) } else null
        SMangaUpdate(
            details?.await()?.apply {
                url = manga.url
                thumbnail_url = manga.thumbnail_url ?: thumbnail_url
            } ?: manga,
            episodes?.await() ?: chapters,
        )
    }

    private suspend fun fetchDetails(path: String): SManga {
        val document = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()
        val show = document.extractNextJs<ShowDto> { it is JsonObject && "creator" in it && "genre" in it }!!
        return SManga.create().apply {
            setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")?.absUrl("href") ?: (baseUrl + path))
            title = show.name
            author = show.creator?.takeIf { it.isNotBlank() }
            description = MarkdownDescription.Builder().description(show.description).build()
            genre = listOfNotNull(show.genre, show.trope)
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()
                .ifEmpty { null }
            thumbnail_url = show.metadata.firstOrNull { it.type == "BANNER_THUMBNAIL_V2" }?.value
            initialized = true
        }
    }

    // Only the first episodes of a series are free on the web: the others are unlocked in the app
    private suspend fun fetchEpisodes(showId: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED, false)
        return api("episode/show/$showId").parseAs<List<EpisodeEntryDto>>()
            .filter { it.episode.state == null || it.episode.state == "PUBLISHED" }
            .filter { !hideLocked || it.lockData?.isLocked != true }
            .map { entry ->
                val episode = entry.episode
                val locked = entry.lockData?.isLocked == true
                SChapter.create().apply {
                    url = "/comics/read/$showId/episodes/${episode.id}"
                    name = if (locked) "🔒 ${episode.name}" else episode.name
                    chapter_number = episode.sequence
                    date_upload = episode.releaseDate ?: 0L
                    if (locked) memo = buildJsonObject { put(LOCKED, true) }
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // A locked episode only gives its first two panels and a prompt to download the app
        if (chapter.memo[LOCKED]?.booleanOrNull == true) return emptyList()
        return api("panel/episode/${chapter.url.substringAfterLast('/')}").parseAs<List<PanelDto>>()
            .sortedBy { it.sequence }
            .mapIndexed { index, panel -> Page(index, imageUrl = panel.imageUrl) }
    }

    // The reader API needs the anonymous Firebase session the site opens for every visitor
    private suspend fun api(path: String): Response {
        val response = client.get("$API_URL/$path", apiHeaders(renew = false), Fetch.XHR, ensureSuccess = false)
        if (response.isSuccessful) return response
        response.close()
        if (response.code != 401) throw HttpException(response.code)
        return client.get("$API_URL/$path", apiHeaders(renew = true), Fetch.XHR)
    }

    private suspend fun apiHeaders(renew: Boolean) = headersBuilder().set("Authorization", "Bearer ${token(renew)}").build()

    // Like the site, the session is kept and renewed rather than opened again on every launch
    private suspend fun token(renew: Boolean): String = sessionLock.withLock {
        cachedToken?.takeIf { !renew && System.currentTimeMillis() < cachedTokenExpiry }?.let { return it }
        val session = preferences.getString(PREF_REFRESH_TOKEN, null)?.let { renewSession(it) } ?: openSession()
        preferences.edit().putString(PREF_REFRESH_TOKEN, session.refreshToken).apply()
        cachedToken = session.idToken
        cachedTokenExpiry = System.currentTimeMillis() + (session.expiresIn.toLong() - 300) * 1000
        session.idToken
    }

    private suspend fun openSession() = client.post(
        "$FIREBASE_AUTH_URL/accounts:signUp?key=$FIREBASE_API_KEY",
        headers,
        Fetch.XHR,
        """{"returnSecureToken":true}""".toRequestBody(JSON),
    ).parseAs<SignUpDto>().let { RefreshDto(it.idToken, it.refreshToken, it.expiresIn) }

    // Null when the saved session can't be renewed anymore
    private suspend fun renewSession(refreshToken: String): RefreshDto? {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        val response = client.post("$FIREBASE_TOKEN_URL?key=$FIREBASE_API_KEY", headers, Fetch.XHR, body, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response.parseAs<RefreshDto>()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED
            title = "Hide locked episodes"
            summary = "Locked episodes (🔒) can only be unlocked in the Dashtoon app."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_URL = "https://api.dashtoon.ai/api/v2/reader"
        private const val FIREBASE_AUTH_URL = "https://identitytoolkit.googleapis.com/v1"
        private const val FIREBASE_TOKEN_URL = "https://securetoken.googleapis.com/v1/token"

        // The site's public Firebase web key
        private const val FIREBASE_API_KEY = "AIzaSyB_2aDH3U9GS2J9CDahwqGPSZkTF59mzNc"
        private val JSON = "application/json".toMediaType()
        private const val LOCKED = "locked"
        private const val PREF_HIDE_LOCKED = "pref_hide_locked"
        private const val PREF_REFRESH_TOKEN = "firebase_refresh_token"
    }
}
