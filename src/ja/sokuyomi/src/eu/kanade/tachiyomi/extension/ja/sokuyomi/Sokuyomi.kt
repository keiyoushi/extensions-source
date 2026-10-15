package eu.kanade.tachiyomi.extension.ja.sokuyomi

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Sokuyomi :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/graphql"
    private val cdnUrl get() = "https://cdn.$domain"
    private val preferences by getPreferencesLazy()
    private val jst = ZoneId.of("Asia/Tokyo")
    private val clientVersionFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val tokenMutex = Mutex()

    private var loginFailed = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(GraphQLErrorInterceptor())
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 405) {
                throw IOException("This service is only available in Japan.")
            }
            response
        }
    }

    override fun Headers.Builder.configureHeaders() = set("Client-Version", LocalDate.now(jst).format(clientVersionFormat))

    override suspend fun getPopularManga(page: Int): MangasPage = client.post(
        apiUrl,
        graphQLBody(
            LIST_QUERY,
            "ListTitle",
            ListVariables(50, page - 1, "LIKE_COUNT", "DESC"),
        ),
    ).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.post(
        apiUrl,
        graphQLBody(
            LIST_QUERY,
            "ListTitle",
            ListVariables(50, page - 1, "LATEST_BOOK_OPEND_AT", "DESC"),
        ),
    ).toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = filters.firstInstance<TagFilter>().value
        val variables = if (query.isNotBlank()) {
            ListVariables(50, page - 1, "LIKE_COUNT", "DESC", query)
        } else {
            ListVariables(20, page - 1, "LIKE_COUNT", "ASC", tagSlug = tags)
        }

        return client.post(
            apiUrl,
            graphQLBody(
                LIST_QUERY,
                "ListTitle",
                variables,
            ),
        ).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseGraphQLAs<SeriesResponse>().listTitle
        val mangas = result.edges.map { it.node.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pageInfo.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.url}/detail/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.post(
            apiUrl,
            apiHeaders(),
            graphQLBody(
                DETAILS_QUERY,
                "GetTitle",
                DetailsVariables(manga.url),
            ),
        ).parseGraphQLAs<DetailsResponse>()

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.listChapter.edges
            .filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter("chapter") }

        val volumeList = result.listVolume.edges
            .filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter("volume") }

        return SMangaUpdate(
            result.getTitle.toSManga(cdnUrl),
            chapterList + volumeList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val type = chapter.memo["type"]?.stringOrNull ?: "volume"
        return "$baseUrl/viewer/$type/${chapter.url}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = if (chapter.memo["type"]?.stringOrNull == "chapter") {
            graphQLBody(CHAPTER_VIEWER_QUERY, "GetChapterViewer", ViewerVariables(chapter.url))
        } else {
            graphQLBody(VOLUME_VIEWER_QUERY, "GetVolumeViewer", ViewerVariables(chapter.url))
        }

        val result = try {
            client.post(apiUrl, apiHeaders(), body).parseGraphQLAs<ViewerResponse>()
        } catch (e: GraphQLException) {
            if (loginFailed) throw IOException("Invalid E-Mail or Password")
            throw IOException(e.message?.substringAfter("Viewer ") + " (Log in via Settings.)")
        }

        return result.viewer.pages.map {
            Page(it.pageNumber, imageUrl = "$cdnUrl/${it.key}#scramble")
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TagFilter(),
    )

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
            putString(TOKEN, tokens.accessToken)
            putString(REFRESH, tokens.refreshToken)
            putLong(EXPIRES, tokens.expiresAt * 1000)
            apply()
        }
        tokens.accessToken
    }

    private suspend fun login(): Signin? {
        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!
        if (email.isBlank() || password.isBlank()) return null

        return try {
            client.post(
                apiUrl,
                graphQLBody(
                    LOGIN_QUERY,
                    "Signin",
                    LoginVariables(email, password),
                ),
            ).parseGraphQLAs<LoginResponse>().signin
        } catch (_: GraphQLException) {
            loginFailed = true
            null
        }
    }

    private suspend fun refresh(refreshToken: String): Signin? = try {
        client.post(
            apiUrl,
            graphQLBody(
                REFRESH_QUERY,
                "Token",
                RefreshVariables(refreshToken),
            ),
        ).parseGraphQLAs<RefreshResponse>().token
    } catch (_: GraphQLException) {
        null
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
    }
}
