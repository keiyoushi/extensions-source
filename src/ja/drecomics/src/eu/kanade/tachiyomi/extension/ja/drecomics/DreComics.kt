package eu.kanade.tachiyomi.extension.ja.drecomics

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
import keiyoushi.utils.boolean
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

@Source
abstract class DreComics :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/api/v1/app"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()

    private var loginFailed = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if ((response.code == 401 || response.code == 403) && request.url.pathSegments[3] == "viewer") {
                if (loginFailed) {
                    throw IOException("Invalid E-Mail or Password")
                }
                throw IOException("Enter your credentials in Settings and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/series/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "10")
            .build()

        val result = client.get(url).parseAs<RankingResponse>()
        val mangas = result.items.map { it.series.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest_published_at")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "18")
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "18")
            .build()

        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<SeriesResponse>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/series/${manga.url}").parseAs<DetailsResponse>().toSManga()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val authHeaders = apiHeaders()

            val episodes = async {
                buildList {
                    var page = 1
                    do {
                        val url = "$apiUrl/episodes".toHttpUrl().newBuilder()
                            .addQueryParameter("series_code", manga.url)
                            .addQueryParameter("page", page.toString())
                            .addQueryParameter("limit", "200")
                            .addQueryParameter("sort", "episode_number")
                            .addQueryParameter("order", "desc")
                            .build()

                        val result = client.get(url, authHeaders).parseAs<ChapterResponse>()
                        result.items
                            .filter { !hideLocked || !it.isLocked }
                            .mapTo(this) { it.toSChapter(manga.url, "episodes") }
                        page++
                    } while (result.pagination.hasNextPage())
                }
            }

            val volumes = async {
                val url = "$apiUrl/volumes".toHttpUrl().newBuilder()
                    .addQueryParameter("series_code", manga.url)
                    .addQueryParameter("page", "1")
                    .addQueryParameter("limit", "200")
                    .addQueryParameter("sort", "volume_number")
                    .addQueryParameter("order", "desc")
                    .build()

                client.get(url, authHeaders).parseAs<ChapterResponse>().items
                    .filter { !hideLocked || (!it.isLocked && !it.isPreview) }
                    .map { it.toSChapter(manga.url, "volumes") }
            }

            episodes.await() + volumes.await()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/series/${chapter.memo["seriesCode"]!!.string}/${chapter.memo["type"]!!.string}/${chapter.url}"
        return if (chapter.memo["isPreview"]!!.boolean) "$url/trial" else url
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val session = if (chapter.memo["isPreview"]!!.boolean) "trial-session" else "session"
        val result = client.post("$apiUrl/viewer/${chapter.memo["type"]!!.string}/${chapter.url}/$session", apiHeaders(), EMPTY_BODY).parseAs<ViewerResponse>()
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

    private suspend fun apiHeaders(): Headers {
        val token = getToken() ?: return headers
        return headersBuilder()
            .set("Authorization", "Bearer $token")
            .build()
    }

    private suspend fun getToken(): String? = tokenMutex.withLock {
        if (loginFailed) return null
        if (System.currentTimeMillis() < preferences.getLong(EXPIRES_PREF_KEY, 0L)) return preferences.getString(TOKEN_PREF_KEY, null)

        val refreshToken = preferences.getString(REFRESH_PREF_KEY, "")!!
        val tokens = refreshToken.takeIf { it.isNotEmpty() }?.let { refresh(it) }
            ?: login()
            ?: return null

        preferences.edit().apply {
            putString(TOKEN_PREF_KEY, tokens.accessToken)
            putString(REFRESH_PREF_KEY, tokens.refreshToken)
            putLong(EXPIRES_PREF_KEY, System.currentTimeMillis() + tokens.expiresIn * 1000)
            apply()
        }
        tokens.accessToken
    }

    private suspend fun login(): LoginResponse? {
        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!
        if (email.isBlank() || password.isBlank()) return null

        return try {
            client.post("$apiUrl/auth/login", LoginRequest(email, password).toJsonRequestBody()).parseAs<LoginResponse>()
        } catch (_: HttpException) {
            loginFailed = true
            null
        }
    }

    private suspend fun refresh(refreshToken: String): LoginResponse? = try {
        client.post("$apiUrl/auth/refresh", RefreshRequest(refreshToken).toJsonRequestBody()).parseAs<LoginResponse>()
    } catch (_: HttpException) {
        null
    }

    private fun clearTokens() {
        loginFailed = false
        preferences.edit().apply {
            remove(TOKEN_PREF_KEY)
            remove(REFRESH_PREF_KEY)
            remove(EXPIRES_PREF_KEY)
            apply()
        }
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN_PREF_KEY = "token"
        private const val REFRESH_PREF_KEY = "refresh"
        private const val EXPIRES_PREF_KEY = "expires"
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
    }
}
