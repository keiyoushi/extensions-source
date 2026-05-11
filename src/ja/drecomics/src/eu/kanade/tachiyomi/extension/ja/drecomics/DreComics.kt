package eu.kanade.tachiyomi.extension.ja.drecomics

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

class DreComics :
    HttpSource(),
    ConfigurableSource {
    override val name = "DreComi+"
    private val domain = "drecomi-plus.jp"
    override val baseUrl = "https://drecomi-plus.jp"
    override val lang = "ja"
    override val supportsLatest = true
    override val versionId = 2

    private val apiUrl = "https://api.$domain/api/v1/app"
    private val authUrl = "$baseUrl/api/auth"
    private val preferences by getPreferencesLazy()

    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == baseUrl.toHttpUrl().host &&
                request.url.encodedPath.startsWith("/api/auth/")
            ) {
                return@addInterceptor chain.proceed(request)
            }

            val newRequest = request.newBuilder().apply {
                val token = getToken()
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }.build()

            val response = chain.proceed(newRequest)

            if ((response.code == 401 || response.code == 403) && request.url.pathSegments[3] == "viewer") {
                if (loginFailed) {
                    throw IOException("Invalid E-Mail or Password")
                }
                throw IOException("Enter your credentials in Settings and purchase this product to read.")
            }

            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "10")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingResponse>()
        val mangas = result.items.map { it.series.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest_published_at")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "18")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesResponse>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "18")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.url.contains("/")) {
            throw Exception("Migrate from $name to $name")
        }
        val chapters = mutableListOf<SChapter>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        var page = 1
        var result: ChapterResponse

        do {
            val url = "$apiUrl/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("series_code", manga.url)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", "200")
                .addQueryParameter("sort", "episode_number")
                .addQueryParameter("order", "desc")
                .build()

            result = client.newCall(GET(url, headers)).execute().parseAs<ChapterResponse>()
            chapters += result.items.filter { !hideLocked || !it.isLocked }.map { it.toSChapter() }
            page++
        } while (result.pagination.hasNextPage())

        val volumeUrl = "$apiUrl/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("series_code", manga.url)
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "200")
            .addQueryParameter("sort", "volume_number")
            .addQueryParameter("order", "desc")
            .build()
        client.newCall(GET(volumeUrl, headers)).execute().parseAs<ChapterResponse>().items
            .filter { !hideLocked || !it.isLocked }
            .mapTo(chapters) { it.toSChapter() }

        return Observable.just(chapters)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaCode = chapter.url.split("-").first()
        return "$baseUrl/series/$mangaCode/episodes/${chapter.url}"
    }

    override fun pageListRequest(chapter: SChapter): Request = if (chapter.url.split("-").size == 3) {
        POST("$apiUrl/viewer/episodes/${chapter.url}/session", headers)
    } else {
        POST("$apiUrl/viewer/volumes/${chapter.url}/session", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        return result.pages.map {
            Page(it.pageNumber, imageUrl = "${it.imageUrl}#${result.sessionKey}:${it.iv}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF_KEY
            title = "E-Mail"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            setOnPreferenceChangeListener { _, _ ->
                clearTokens()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF_KEY
            title = "Password"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, _ ->
                clearTokens()
                true
            }
        }.also(screen::addPreference)
    }

    @Synchronized
    private fun getToken(): String {
        if (loginFailed) return ""

        if (bearerToken != null && System.currentTimeMillis() < tokenExpiration) {
            return bearerToken!!
        }

        val savedToken = preferences.getString(TOKEN_PREF_KEY, "")!!
        val savedExpiry = preferences.getLong(EXPIRES_PREF_KEY, 0L)
        if (savedToken.isNotBlank() && System.currentTimeMillis() < savedExpiry) {
            bearerToken = savedToken
            tokenExpiration = savedExpiry
            return savedToken
        }

        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!
        if (email.isNotBlank() && password.isNotBlank()) {
            return try {
                login(email, password)
            } catch (_: Exception) {
                loginFailed = true
                ""
            }
        }

        return try {
            fetchSession().accessToken ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun login(email: String, password: String): String {
        val csrfToken = fetchCsrfToken()

        val body = FormBody.Builder()
            .add("csrfToken", csrfToken)
            .add("callbackUrl", baseUrl)
            .add("email", email)
            .add("password", password)
            .add("json", "true")
            .add("redirect", "false")
            .build()

        val loginRequest = POST("$authUrl/callback/credentials", headers, body)
        val loginResponse = client.newCall(loginRequest).execute()

        val result = loginResponse.parseAs<NextAuthSignInResponse>()
        val errorParam = result.url?.toHttpUrl()?.queryParameter("error")
        if (!errorParam.isNullOrBlank() || result.ok == false) {
            throw IOException("Login failed: $errorParam")
        }

        val token = fetchSession().accessToken
            ?: throw IOException("Login succeeded but no accessToken found in session")

        saveToken(token)
        return token
    }

    private fun fetchCsrfToken(): String {
        val request = GET("$authUrl/csrf", headers)
        return client.newCall(request).execute().parseAs<CsrfResponse>().csrfToken
    }

    private fun fetchSession(): SessionResponse {
        val request = GET("$authUrl/session", headers)
        return client.newCall(request).execute().parseAs<SessionResponse>()
    }

    private fun saveToken(token: String) {
        val expiration = System.currentTimeMillis() + 86_400_000L
        bearerToken = token
        tokenExpiration = expiration
        preferences.edit().apply {
            putString(TOKEN_PREF_KEY, token)
            putLong(EXPIRES_PREF_KEY, expiration)
            apply()
        }
    }

    @Synchronized
    private fun clearTokens() {
        loginFailed = false
        bearerToken = null
        tokenExpiration = 0L
        preferences.edit().apply {
            remove(TOKEN_PREF_KEY)
            remove(EXPIRES_PREF_KEY)
            apply()
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN_PREF_KEY = "token"
        private const val EXPIRES_PREF_KEY = "expires"
    }
}
