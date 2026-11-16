package eu.kanade.tachiyomi.extension.pt.baozinho

import android.text.InputType
import androidx.preference.EditTextPreference
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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class Baozinho :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://api.baozinho.com"

    private val preferences by getPreferencesLazy()

    private val hidePaid get() = preferences.getBoolean(HIDE_PAID_KEY, false)

    private val email get() = preferences.getString(EMAIL_KEY, null).orEmpty().trim()
    private val password get() = preferences.getString(PASSWORD_KEY, null).orEmpty()
    private val hasCredentials get() = email.isNotEmpty() && password.isNotEmpty()
    private val hasSession get() = preferences.getString(REFRESH_KEY, null) != null

    // The refresh token rotates on every renewal; the access token is short-lived and kept in memory only.
    private val authLock = Mutex()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessExpiresAt = Instant.DISTANT_PAST

    // Time and message of the last failed login, so a failure is not retried (reopening the captcha) on every request.
    @Volatile
    private var loginFailure: Pair<Instant, Exception>? = null

    override fun getMangaUrl(manga: SManga) = "$baseUrl/obras/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/obras/${chapter.url}"

    override suspend fun getPopularManga(page: Int) = fetchWorks(worksUrl(apiUrl, page, POPULAR))

    override suspend fun getLatestUpdates(page: Int) = fetchWorks(worksUrl(apiUrl, page, "atualizadas"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = worksUrl(
            apiUrl,
            page,
            orderBy = filters.firstInstanceOrNull<SortFilter>()?.value ?: POPULAR,
            query = query,
            status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
            format = filters.firstInstanceOrNull<FormatFilter>()?.value.orEmpty(),
            tagIds = filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty().filter { it.state }.map { it.id },
        )
        return fetchWorks(url)
    }

    private suspend fun fetchWorks(url: HttpUrl) = client.get(url, Fetch.XHR).parseAs<WorksDto>().toMangasPage()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val tags = async { client.get("$apiUrl/tags", Fetch.XHR).parseAs<List<TagDto>>() }
        val formats = async { client.get("$apiUrl/formatos", Fetch.XHR).parseAs<List<FormatDto>>() }
        FiltersDto(tags.await(), formats.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(SortFilter(), StatusFilter())
        data?.parseAs<FiltersDto>()?.let {
            filters += FormatFilter(it.formats)
            filters += TagFilter(it.tagsWithWorks())
        }
        return FilterList(filters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.takeIf { it.firstOrNull() == "obras" }?.getOrNull(1)
        return slug?.takeIf { it.isNotEmpty() }?.let { fetchWork(it).toSManga() }
    }

    private suspend fun fetchWork(slug: String) = client.get("$apiUrl/obras/slug/$slug", Fetch.XHR).parseAs<WorkDto>()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val knownId = manga.memo["id"]?.stringOrNull?.toIntOrNull()
        if (knownId == null) {
            // Entries saved by older versions have no id in memo yet.
            val work = fetchWork(manga.url)
            return SMangaUpdate(work.toSManga(), if (fetchChapters) fetchChapterList(work.id, manga.url) else chapters)
        }
        return coroutineScope {
            val details = if (needsDetails(fetchDetails, knownId)) async { fetchWork(manga.url).toSManga() } else null
            val newChapters = if (fetchChapters) fetchChapterList(knownId, manga.url) else chapters
            SMangaUpdate(details?.await() ?: manga, newChapters)
        }
    }

    private suspend fun fetchChapterList(workId: Int, slug: String): List<SChapter> = coroutineScope {
        val list = async { client.get("$apiUrl/capitulos/obra/$workId", Fetch.XHR).parseAs<List<ChapterDto>>() }
        val unlocked = fetchUnlocked(workId)
        list.await().visible(hidePaid, unlocked).map { it.toSChapter(slug, unlocked.orEmpty()) }
    }

    // Logging in is never needed for the chapter list: without a session the unlocked set is unknown or empty.
    private suspend fun fetchUnlocked(workId: Int): Set<Int>? {
        if (!hasSession) return unlockedWithoutSession(hasCredentials)
        return try {
            getJson<List<UnlockedDto>>("$apiUrl/capitulos/obra/$workId/desbloqueados", Fetch.XHR).map { it.chapterId }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // A paid chapter the account has not unlocked comes without "paginas": an empty list makes the app show its own error.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.memo["id"]?.stringOrNull ?: return emptyList()
        val url = "$apiUrl/capitulos/$id"
        val fetch = Fetch.XHR.from(getChapterUrl(chapter))
        val pages = getJson<ChapterPagesDto>(url, fetch).toPages()
        // The first request uses the session if there is one; only a locked chapter with saved credentials logs in.
        if (shouldLoginForPages(pages.isEmpty(), hasSession, hasCredentials)) {
            return getJson<ChapterPagesDto>(url, fetch, login = true).toPages()
        }
        return pages
    }

    // The user's state must not come from the client's 10-minute response cache.
    private suspend inline fun <reified T> getJson(url: String, fetch: Fetch, login: Boolean = false): T {
        var token = accessToken(login)
        for (attempt in 0..1) {
            val response = client.get(url, authHeaders(token), fetch, CacheControl.FORCE_NETWORK, ensureSuccess = false)
            if (response.code == 401 && token != null && attempt == 0) {
                response.close()
                token = accessToken(login, force = true)
                continue
            }
            if (!response.isSuccessful) {
                response.close()
                throw HttpException(response.code)
            }
            return response.parseAs<T>()
        }
        error("unreachable")
    }

    private fun authHeaders(token: String?): Headers = token?.let { headersBuilder().set("Authorization", "Bearer $it").build() } ?: headers

    // Uses the stored session; only logs in with the saved credentials when asked to.
    private suspend fun accessToken(login: Boolean = false, force: Boolean = false): String? = authLock.withLock {
        if (!force && Clock.System.now() < accessExpiresAt) return@withLock accessToken
        val renewed = preferences.getString(REFRESH_KEY, null)?.let { renewSession(it) }
        renewed ?: if (login) login() else null
    }

    // Returns null when the site clearly rejects the stored token; other failures keep the session.
    private suspend fun renewSession(refreshToken: String): String? {
        val response = client.post("$apiUrl/auth/refresh", Fetch.XHR, RefreshRequest(refreshToken).toJsonRequestBody(), ensureSuccess = false)
        if (response.isSuccessful) return saveSession(response.parseAs<TokensDto>()).accessToken
        val code = response.code
        val body = response.use { it.body.string() }
        if (isSessionRejected(code, body)) {
            clearSession()
            return null
        }
        throw refreshError(code)
    }

    // The login needs a Cloudflare Turnstile token, which core solves in a WebView. Failures are reported to the user by the request that needed the login.
    private suspend fun login(): String? {
        if (!hasCredentials) return null
        loginFailure?.takeUnless { shouldRetryLogin(it.first, Clock.System.now(), LOGIN_RETRY) }?.let { throw it.second }
        try {
            val turnstile = try {
                getTurnstileToken(baseUrl, TURNSTILE_SITE_KEY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Não foi possível passar a verificação do Baozinho (${e.message}).", e)
            }
            val response = client.post("$apiUrl/auth/login", Fetch.XHR, LoginRequest(email, password, turnstile).toJsonRequestBody(), ensureSuccess = false)
            if (response.isSuccessful) return saveSession(response.parseAs<TokensDto>()).accessToken
            val code = response.code
            val body = response.use { it.body.string() }
            throw loginError(code, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginFailure = Clock.System.now() to e
            throw e
        }
    }

    // commit(), not apply(): the rotated token must be on disk before any other renewal.
    private fun saveSession(tokens: TokensDto): TokensDto {
        preferences.edit().putString(REFRESH_KEY, tokens.refreshToken).commit()
        accessToken = tokens.accessToken
        accessExpiresAt = Clock.System.now() + ACCESS_TTL
        loginFailure = null
        return tokens
    }

    private fun clearSession() {
        preferences.edit().remove(REFRESH_KEY).apply()
        accessToken = null
        accessExpiresAt = Instant.DISTANT_PAST
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        val hidePaidPref = SwitchPreferenceCompat(context).apply {
            key = HIDE_PAID_KEY
            title = "Ocultar capítulos pagos"
            summary = "Esconde da lista os capítulos com 🔒 que a sua conta não liberou. Vale a partir da próxima atualização da obra."
            setDefaultValue(false)
        }

        val emailPref = EditTextPreference(context).apply {
            key = EMAIL_KEY
            title = "E-mail"
            summary = email.ifEmpty { "Não definido" }
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
            setOnPreferenceChangeListener { pref, value ->
                pref.summary = (value as String).trim().ifEmpty { "Não definido" }
                onCredentialsChanged()
                true
            }
        }

        // The login itself happens later, when a chapter needs it; clearing the password signs out.
        val passwordPref = EditTextPreference(context).apply {
            key = PASSWORD_KEY
            title = "Senha"
            summary = passwordSummary(password, hasSession)
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            setOnPreferenceChangeListener { pref, value ->
                pref.summary = passwordSummary(value as String, connected = false)
                onCredentialsChanged()
                true
            }
        }

        listOf(hidePaidPref, emailPref, passwordPref).forEach(screen::addPreference)
    }

    // New credentials mean a possibly different account: drop the old session and allow a fresh login (local work only).
    private fun onCredentialsChanged() {
        clearSession()
        loginFailure = null
    }

    private companion object {
        const val POPULAR = "populares_total"

        // Preference keys are persisted on users' devices: keep the values when renaming.
        const val HIDE_PAID_KEY = "hide_paid_chapters"
        const val EMAIL_KEY = "login_email"
        const val PASSWORD_KEY = "login_password"
        const val REFRESH_KEY = "session_refresh_token"
        val ACCESS_TTL = 10.minutes
        val LOGIN_RETRY = 5.minutes

        // Public key of the site's Turnstile widget (it appears in the login page's JS).
        const val TURNSTILE_SITE_KEY = "0x4AAAAAADtmuUfMFzDWFmF8"
    }
}
