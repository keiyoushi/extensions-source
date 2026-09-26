package eu.kanade.tachiyomi.extension.ja.coronaex

import android.text.InputType
import androidx.preference.EditTextPreference
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
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class CoronaEx :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain"
    private val authDomain get() = "googleapis.com"
    private val loginUrl get() = "https://identitytoolkit.$authDomain/v1"
    private val refeshUrl get() = "https://securetoken.$authDomain/v1"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()

    private var cursor: String? = null
    private var loginFailed = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 402 && request.url.pathSegments.contains("begin_reading")) {
                if (loginFailed) {
                    throw IOException("Invalid E-Mail or Password")
                }
                throw IOException("Enter your credentials in Settings and subscribe to the website's service.")
            }
            response
        }
    }

    override fun Headers.Builder.configureHeaders() = set("X-Api-Environment-Key", API_KEY)

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) cursor = null
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("order", "asc")
            .addQueryParameter("sort", "title_yomigana")
            .apply {
                cursor?.let { addQueryParameter("after_than", it) }
            }
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) cursor = null
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "12")
            .addQueryParameter("order", "desc")
            .addQueryParameter("sort", "latest_episode_published_at")
            .apply {
                cursor?.let { addQueryParameter("after_than", it) }
            }
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page == 1) cursor = null
        if (query.isNotEmpty()) {
            val url = "$apiUrl/search/comics".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "24")
                .apply {
                    cursor?.let { addQueryParameter("after_than", it) }
                }
                .build()

            return client.get(url).toMangasPage()
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

        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<TitleResponse>()
        cursor = result.nextCursor
        val mangas = result.resources.map { it.toSManga() }
        val hasNextPage = result.nextCursor != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/comics/${manga.url}").parseAs<TitleDetails>().toSManga()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            buildList {
                var nextCursor: String? = null
                do {
                    val url = "$apiUrl/episodes".toHttpUrl().newBuilder()
                        .addQueryParameter("comic_id", manga.url)
                        .addQueryParameter("episode_status", "free_viewing,only_for_subscription")
                        .addQueryParameter("limit", "100")
                        .addQueryParameter("order", "desc")
                        .addQueryParameter("sort", "episode_order")
                        .apply {
                            nextCursor?.let { addQueryParameter("after_than", it) }
                        }
                        .build()

                    val result = client.get(url).parseAs<ChapterDetails>()
                    result.resources
                        .filter { !hideLocked || !it.isLocked }
                        .mapTo(this) { it.toSChapter() }
                    nextCursor = result.nextCursor
                } while (nextCursor != null)
            }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$apiUrl/episodes/${chapter.url}/begin_reading", apiHeaders()).parseAs<ViewerResponse>()
        return result.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.pageImageUrl)
        }
    }

    private suspend fun apiHeaders(): Headers {
        val token = getToken() ?: return headers
        return headersBuilder()
            .set("Authorization", "Bearer $token")
            .build()
    }

    private suspend fun getToken(): String? = tokenMutex.withLock {
        if (loginFailed) return null
        if (System.currentTimeMillis() < preferences.getLong(EXPIRES, 0L)) return preferences.getString(TOKEN, null)

        val refreshToken = preferences.getString(REFRESH, "")!!
        val tokens = refreshToken.takeIf { it.isNotEmpty() }?.let { refresh(it) }
            ?: login()
            ?: return null

        preferences.edit().apply {
            putString(TOKEN, tokens.idToken)
            putString(REFRESH, tokens.refreshToken)
            putLong(EXPIRES, System.currentTimeMillis() + 3_600_000L)
            apply()
        }
        tokens.idToken
    }

    private suspend fun login(): LoginResponse? {
        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!
        if (email.isEmpty() || password.isEmpty()) return null

        val url = "$loginUrl/accounts:signInWithPassword".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()

        return try {
            client.post(url, LoginRequestBody(email, password, true).toJsonRequestBody()).parseAs<LoginResponse>()
        } catch (_: HttpException) {
            loginFailed = true
            null
        }
    }

    private suspend fun refresh(refreshToken: String): LoginResponse? {
        val url = "$refeshUrl/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()

        return try {
            client.post(url, RefreshRequestBody("refresh_token", refreshToken).toJsonRequestBody()).parseAs<LoginResponse>()
        } catch (_: HttpException) {
            null
        }
    }

    private fun clearTokens() {
        loginFailed = false
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

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN = "token"
        private const val REFRESH = "refresh"
        private const val EXPIRES = "expires"
        private const val API_KEY = "K4FWy7Iqott9mrw37hDKfZ2gcLOwO-kiLHTwXT8ad1E="
        private const val LOGIN_KEY = "AIzaSyCeiy1JMHVkFuI8zbiAxMjNO3zoXECENhE"
    }
}
