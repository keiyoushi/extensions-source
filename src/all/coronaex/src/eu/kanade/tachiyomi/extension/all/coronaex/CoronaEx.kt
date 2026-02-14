package eu.kanade.tachiyomi.extension.all.coronaex

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class CoronaEx(
    override val lang: String,
    domain: String,
) : HttpSource(),
    ConfigurableSource {
    override val name = "Corona EX"
    override val baseUrl = "https://$domain"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain"
    private val authDomain = "googleapis.com"
    private val loginUrl = "https://identitytoolkit.$authDomain/v1"
    private val refeshUrl = "https://securetoken.$authDomain/v1"
    private val preferences: SharedPreferences by getPreferencesLazy()

    private var cursor: String? = null
    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 402 && request.url.pathSegments.contains("begin_reading")) {
                val loginInvalidException = request.tag(IOException::class.java)
                if (loginInvalidException != null) {
                    throw loginInvalidException
                }

                throw IOException("Enter your credentials in Settings and subscribe to the website's service.")
            }

            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder().apply {
        val apiKey = if (lang == "ja") API_KEY_JA else API_KEY_EN
        add("X-Api-Environment-Key", apiKey)
    }

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) cursor = null
        val sort = if (lang == "ja") "title_yomigana" else "title_alphanumeric"
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("order", "asc")
            .addQueryParameter("sort", sort)
            .apply {
                cursor?.let { addQueryParameter("after_than", it) }
            }
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<TitleResponse>()
        val mangas = result.resources.map { it.toSManga() }
        val hasNextPage = result.nextCursor != null
        cursor = result.nextCursor
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) cursor = null
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "12")
            .addQueryParameter("order", "desc")
            .addQueryParameter("sort", "latest_episode_published_at")
            .apply {
                cursor?.let { addQueryParameter("after_than", it) }
            }
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) cursor = null
        if (query.isNotEmpty()) {
            val url = "$apiUrl/search/comics".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "24")
                .apply {
                    cursor?.let { addQueryParameter("after_than", it) }
                }
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<GenreFilter>()
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("genre_id", filter.value)
            .addQueryParameter("limit", "24")
            .addQueryParameter("order", "asc")
            .addQueryParameter("sort", "title_yomigana")
            .apply {
                cursor?.let { addQueryParameter("after_than", it) }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comics/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<TitleDetails>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("comic_id", manga.url)
            .addQueryParameter("episode_status", "free_viewing,only_for_subscription")
            .addQueryParameter("limit", "9999")
            .addQueryParameter("order", "desc")
            .addQueryParameter("sort", "episode_order")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterDetails>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return result.resources
            .filter { !hideLocked || it.episodeStatus != "only_for_subscription" }
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val request = GET("$apiUrl/episodes/${chapter.url}/begin_reading", headers)
        try {
            val token = getToken()
            if (token.isNotEmpty()) {
                return request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        } catch (_: IOException) {
            return request.newBuilder()
                .tag(IOException::class.java, IOException("Invalid E-Mail or Password"))
                .build()
        }

        return request
    }

    override fun pageListParse(response: Response): List<Page> {
        val results = response.parseAs<ViewerResponse>()
        return results.pages.mapIndexed { index, page ->
            Page(index, imageUrl = "${page.pageImageUrl}#${page.drmHash}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    @Synchronized
    private fun getToken(): String {
        if (loginFailed) {
            throw IOException("Invalid E-Mail or Password")
        }

        if (bearerToken != null && System.currentTimeMillis() < tokenExpiration) {
            return bearerToken!!
        }

        val refreshToken = preferences.getString(REFRESH, "")!!
        if (refreshToken.isNotEmpty()) {
            try {
                val newTokens = refresh(refreshToken)
                saveTokens(newTokens)
                return newTokens.idToken
            } catch (_: Exception) {
            }
        }

        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!

        if (email.isNotEmpty() && password.isNotEmpty()) {
            try {
                val newTokens = login(email, password)
                saveTokens(newTokens)
                return newTokens.idToken
            } catch (_: Exception) {
                loginFailed = true
                throw IOException("Invalid E-Mail or Password")
            }
        }

        return ""
    }

    private val loginKey: String
        get() = if (lang == "ja") LOGIN_KEY_JA else LOGIN_KEY_EN

    private fun login(email: String, password: String): LoginResponse {
        val body = LoginRequestBody(email, password, true).toJsonString().toRequestBody("application/json".toMediaType())
        val url = "$loginUrl/accounts:signInWithPassword".toHttpUrl().newBuilder()
            .addQueryParameter("key", loginKey)
            .build()
        val request = POST(url.toString(), headers, body)

        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun refresh(refreshToken: String): LoginResponse {
        val body = RefreshRequestBody("refresh_token", refreshToken).toJsonString().toRequestBody("application/json".toMediaType())
        val url = "$refeshUrl/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", loginKey)
            .build()
        val request = POST(url.toString(), headers, body)

        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun saveTokens(response: LoginResponse) {
        val expiration = System.currentTimeMillis() + (3600 * 1000)
        bearerToken = response.idToken
        tokenExpiration = expiration
        preferences.edit().apply {
            putString(TOKEN, response.idToken)
            putString(REFRESH, response.refreshToken)
            putLong(EXPIRES, expiration)
            apply()
        }
    }

    @Synchronized
    private fun clearTokens() {
        loginFailed = false
        bearerToken = null
        tokenExpiration = 0L
        preferences.edit().apply {
            remove(TOKEN)
            remove(REFRESH)
            remove(EXPIRES)
            apply()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = EMAIL_PREF_KEY
                title = "E-Mail"
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
    }

    override fun getFilterList(): FilterList {
        if (lang != "ja") return FilterList()
        return FilterList(GenreFilter())
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN = "token"
        private const val REFRESH = "refresh"
        private const val EXPIRES = "expires"
        private const val API_KEY_JA = "K4FWy7Iqott9mrw37hDKfZ2gcLOwO-kiLHTwXT8ad1E="
        private const val API_KEY_EN = "YMiCe3ofO07MjQSroVEYDEUzyDm2sUHwDeDgqAhsTC8"
        private const val LOGIN_KEY_JA = "AIzaSyCeiy1JMHVkFuI8zbiAxMjNO3zoXECENhE"
        private const val LOGIN_KEY_EN = "AIzaSyByfbwJ2lzGAH7mT2PNfXt7VuwsZZhfSe8"
    }
}
