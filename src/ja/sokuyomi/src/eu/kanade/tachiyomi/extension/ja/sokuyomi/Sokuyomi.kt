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
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class Sokuyomi :
    HttpSource(),
    ConfigurableSource {
    private val domain = "sokuyomi.jp"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain/graphql"
    private val cdnUrl = "https://cdn.$domain"
    private val preferences by getPreferencesLazy()

    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed = false

    override val client = network.client.newBuilder()
        .addInterceptor(GraphQLErrorInterceptor())
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            if (request.url.fragment == "auth") {
                return@addInterceptor it.proceed(request)
            }

            val newRequest = request.newBuilder().apply {
                val token = getToken()
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }.build()
            val response = it.proceed(newRequest)
            if (response.code == 403) {
                throw IOException("This service is only available in Japan.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int) = graphQLPost(
        apiUrl,
        headers,
        SERIES_QUERY,
        "ListTitle",
        PopularVariables(50, page - 1, "LIKE_COUNT", true),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<SeriesResponse>()
        val mangas = result.listTitle.edges.map { it.node.toSManga(cdnUrl) }
        return MangasPage(mangas, result.listTitle.pageInfo.hasNextPage())
    }

    override fun latestUpdatesRequest(page: Int) = graphQLPost(
        apiUrl,
        headers,
        SERIES_QUERY,
        "ListTitle",
        PopularVariables(50, page - 1, "LATEST_BOOK_OPEND_AT", true),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return graphQLPost(
                apiUrl,
                headers,
                SEARCH_QUERY,
                "ListTitle",
                SearchVariables(
                    query,
                    query,
                    query,
                    50,
                    page - 1,
                    "LIKE_COUNT",
                    true,
                ),
            )
        }

        val filter = filters.firstInstance<TagFilter>()
        return graphQLPost(
            apiUrl,
            headers,
            TAG_FILTER_QUERY,
            "ListTitleByTag",
            TagFilterVariables(
                filter.value,
                20,
                page - 1,
            ),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.url}/detail/"

    override fun mangaDetailsRequest(manga: SManga) = graphQLPost(
        apiUrl,
        headers,
        DETAILS_QUERY,
        "GetTitle",
        DetailsVariables(manga.url),
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<DetailsResponse>().getTitle.toSManga(cdnUrl)

    override fun chapterListRequest(manga: SManga): Request = graphQLPost(
        apiUrl,
        headers,
        CHAPTER_LIST_QUERY,
        "ListVolume",
        ChapterListVariables(manga.url, 1000, 0, "DESC"),
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = response.parseGraphQLAs<ChapterResponse>()
        return result.listVolume.edges
            .filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/volume/${chapter.url}/"

    override fun pageListRequest(chapter: SChapter): Request = graphQLPost(
        apiUrl,
        headers,
        VIEWER_QUERY,
        "GetVolumeViewer",
        ViewerVariables(chapter.url),
    )

    override fun pageListParse(response: Response): List<Page> {
        try {
            val result = response.parseGraphQLAs<ViewerResponse>()
            return result.getVolumeViewer.volumePages.map {
                Page(it.pageNumber, imageUrl = "$cdnUrl/${it.key}#scramble")
            }
        } catch (e: GraphQLException) {
            throw IOException(e.message?.substringAfter("input: getVolumeViewer ") + " (Log in via Settings.)")
        }
    }

    @Synchronized
    private fun getToken(): String {
        if (loginFailed) {
            return ""
        }

        if (bearerToken != null && System.currentTimeMillis() < tokenExpiration) {
            return bearerToken!!
        }

        val refreshToken = preferences.getString(REFRESH, "")!!
        if (refreshToken.isNotBlank()) {
            try {
                val newTokens = refresh(refreshToken)
                saveTokens(newTokens.token)
                return newTokens.token.accessToken
            } catch (_: Exception) {
            }
        }

        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!

        if (email.isNotBlank() && password.isNotBlank()) {
            try {
                val newTokens = login(email, password)
                saveTokens(newTokens.signin)
                return newTokens.signin.accessToken
            } catch (_: Exception) {
                loginFailed = true
                return ""
            }
        }

        return ""
    }

    private fun login(email: String, password: String): LoginResponse {
        val request = graphQLPost(
            "$apiUrl#auth",
            headers,
            LOGIN_QUERY,
            "Signin",
            LoginVariables(
                email,
                password,
            ),
        )
        return client.newCall(request).execute().parseGraphQLAs<LoginResponse>()
    }

    private fun refresh(refreshToken: String): RefreshResponse {
        val request = graphQLPost(
            "$apiUrl#auth",
            headers,
            REFRESH_QUERY,
            "Signin",
            RefreshVariables(
                refreshToken,
            ),
        )
        return client.newCall(request).execute().parseGraphQLAs<RefreshResponse>()
    }

    private fun saveTokens(signin: Signin) {
        val expiration = System.currentTimeMillis() + (86400 * 1000L)
        bearerToken = signin.accessToken
        tokenExpiration = expiration
        preferences.edit().apply {
            putString(TOKEN, signin.accessToken)
            putString(REFRESH, signin.refreshToken)
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

    override fun getFilterList() = FilterList(
        TagFilter(),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN = "token"
        private const val REFRESH = "refresh"
        private const val EXPIRES = "expires"
    }
}
