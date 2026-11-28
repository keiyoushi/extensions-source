package eu.kanade.tachiyomi.extension.en.toptoon

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xor
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.Clock

@Source
abstract class Toptoon :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    // The WebView's device id, for the session it logged in
    private var device: Pair<String, String>? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::logoutInterceptor)
        .rateLimit(3) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int) = getCatalog(SortOrder.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getCatalog(SortOrder.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.sort ?: SortOrder.POPULAR
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.genre
        val status = filters.firstInstanceOrNull<StatusFilter>()?.completed
        val day = filters.firstInstanceOrNull<DayFilter>()?.day
        return getCatalog(sort) { comic ->
            comic.matches(query.trim()) &&
                (genre == null || genre in comic.genres.orEmpty()) &&
                (status == null || comic.isCompleted == status) &&
                (day == null || day in comic.updateDays)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "content") return null
        val comicId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return getEpisodePage(comicId).comic.takeIf { it.isComic }?.toSMangaDetails()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = getEpisodePage(getMangaUrl(manga).toHttpUrl().pathSegments[1].toInt())
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)
        val episodes = data.episode
            .sortedByDescending { it.order }
            .mapNotNull { episode ->
                when {
                    episode.isFree -> episode.toSChapter(locked = false)
                    showLocked -> episode.toSChapter(locked = true)
                    else -> null
                }
            }
        return SMangaUpdate(data.comic.toSMangaDetails(), episodes)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (_, comicId, episodeId) = getChapterUrl(chapter).toHttpUrl().pathSegments
        return getImageUrls(comicId, episodeId).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override suspend fun fetchFilterData(): JsonElement = getComics()
        .flatMap { it.genres.orEmpty() }
        .distinct()
        .sorted()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            SortFilter(),
            StatusFilter(),
            DayFilter(),
            data?.parseAs<List<String>>()?.let(::GenreFilter),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "Show locked episodes"
            summary = "Episodes that need coins or a membership, marked 🔒"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private suspend fun getCatalog(sort: SortOrder, predicate: (ComicDto) -> Boolean = { true }): MangasPage {
        val comics = getComics().filter(predicate)
        val sorted = when (sort) {
            SortOrder.POPULAR -> comics.sortedWith(compareByDescending<ComicDto> { it.sales }.thenByDescending { it.viewCount })
            SortOrder.VIEWS -> comics.sortedByDescending { it.viewCount }
            SortOrder.LATEST -> comics.sortedByDescending { it.updatedAt }
        }
        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // The site's search reads this whole catalog; the account's 18+ setting decides which editions it holds
    private suspend fun getComics(): List<ComicDto> = client.get("$API_URL/api/v1/search/totalsearch".toHttpUrl(), apiHeaders(session()), Fetch.XHR)
        .parseAs<ResponseDto<CatalogDto>>().data.totalSearch
        .filter { it.isComic }

    private suspend fun getEpisodePage(comicId: Int): EpisodePageDto {
        val url = "$API_URL/api/v1/page/episode".toHttpUrl().newBuilder()
            .addQueryParameter("comicId", comicId.toString())
            .build()
        return client.get(url, apiHeaders(session()), Fetch.XHR).parseAs<ResponseDto<EpisodePageDto>>().data
    }

    private suspend fun getImageUrls(comicId: String, episodeId: String): List<String> {
        val session = session() ?: throw IOException(LOGIN_MESSAGE)
        val deviceId = deviceId(session)
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val headers = apiHeaders(session).newBuilder()
            .set("x-api-key", viewerApiKey(timestamp, deviceId, episodeId))
            .set("timestamp", timestamp.toString())
            .set("deviceId", deviceId)
            .build()
        val body = ViewerRequestDto("viewer", "view_contents", false, "").toJsonRequestBody()
        val response = client.post("$API_URL/api/v2/viewer/$comicId/$episodeId", headers, Fetch.XHR, body, ensureSuccess = false)
        if (response.code == 500 && response.body.contentType()?.subtype == "json") {
            val refusal = response.parseAs<ResponseDto<ViewerRefusalDto>>().data
            throw IOException(if (refusal.needLogin == true) LOGIN_MESSAGE else LOCKED_MESSAGE)
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.parseAs<ResponseDto<ViewerDto>>().data.episode
            .first { it.episodeId.toString() == episodeId }
            .contentImage?.webp.orEmpty()
            .map { it.path }
    }

    // The site's client logs out on 463
    private fun logoutInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 463) return response
        response.close()
        throw IOException(LOGIN_MESSAGE)
    }

    private fun apiHeaders(session: SessionDto?): Headers = headers.newBuilder()
        .set("x-api-key", API_KEY)
        .set("version", CLIENT_VERSION)
        .set("ua", "web")
        .set("package-name", "web")
        .set("language", "en")
        .set("x-origin", baseUrl.toHttpUrl().host)
        .set("user-id", session?.userId?.toString() ?: "0")
        .apply { if (session != null) set("token", session.token) }
        .build()

    // The WebView login's session cookie: its JSON, XOR 0x16, hex-encoded
    private fun session(): SessionDto? = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == "userSession" }
        ?.value?.decodeHex()
        ?.xor(0x16)
        ?.decodeToString()
        ?.parseAs<SessionDto>()

    private suspend fun deviceId(session: SessionDto): String {
        device?.takeIf { it.first == session.token }?.let { return it.second }
        val deviceId = getLocalStorage(baseUrl, "udid") ?: throw IOException(LOGIN_MESSAGE)
        device = session.token to deviceId
        return deviceId
    }

    // As the site's reader
    private fun viewerApiKey(timestamp: Long, deviceId: String, episodeId: String): String {
        val salt = deviceId.replace("-", timestamp.toString()).sha256().toHexString().toByteArray()
        val spec = PBEKeySpec("$deviceId|$timestamp|$episodeId".toCharArray(), salt, API_KEY_ITERATIONS, salt.size * 8)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }
}

private const val API_HOST = "api-global.toptoon.com"
private const val API_URL = "https://$API_HOST"
private const val API_KEY = "SUPERCOOLAPIKEY2021#@#("
private const val CLIENT_VERSION = "0.1.5a"
private const val API_KEY_ITERATIONS = 257
private const val SHOW_LOCKED_PREF = "show_locked_episodes"
private const val LOGIN_MESSAGE = "Log in via WebView to read"
private const val LOCKED_MESSAGE = "Unlock this episode in WebView"
