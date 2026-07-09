package eu.kanade.tachiyomi.extension.ja.mangano

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class MangaNo :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/query"
    private val authDomain = "googleapis.com"
    private val loginUrl = "https://identitytoolkit.$authDomain/v1"
    private val refreshUrl = "https://securetoken.$authDomain/v1"
    private val preferences by getPreferencesLazy()

    private var cursor: String? = null
    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed: Boolean = false

    override val client = network.client.newBuilder()
        .addInterceptor(GraphQLErrorInterceptor())
        .addInterceptor {
            val request = it.request()
            if (request.url.host.endsWith(authDomain)) {
                return@addInterceptor it.proceed(request)
            }

            val newRequest = request.newBuilder().apply {
                val token = getToken()
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }.build()

            it.proceed(newRequest)
        }
        .build()

    override fun popularMangaRequest(page: Int) = graphQLPost(
        apiUrl,
        headers,
        POPULAR_QUERY,
        "RankingsMonthly",
        EmptyVariables,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<PopularResponse>()
        val mangas = result.ranking.monthly2.edges.map { it.node.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) cursor = null
        return graphQLPost(
            apiUrl,
            headers,
            LATEST_QUERY,
            "NewWorks",
            LatestVariables(cursor),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<SeriesResponse>()
        val mangas = result.newWorks2.edges?.map { it.node.toSManga() } ?: emptyList()
        cursor = result.newWorks2.pageInfo.endCursor
        return MangasPage(mangas, result.newWorks2.pageInfo.hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) cursor = null
        if (query.isNotBlank()) {
            return graphQLPost(
                apiUrl,
                headers,
                SEARCH_QUERY,
                "Search",
                SearchVariables(query, cursor),
            )
        }

        val filter = filters.firstInstance<TagFilter>()
        return graphQLPost(
            "$apiUrl#tag",
            headers,
            TAG_QUERY,
            "Tag",
            TagFilterVariables(filter.value, 100, cursor),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.fragment == "tag") {
            val result = response.parseGraphQLAs<TagResponse>()
            val mangas = result.tag.works.edges?.map { it.node.toSManga() } ?: emptyList()
            cursor = result.tag.works.pageInfo.endCursor
            return MangasPage(mangas, result.tag.works.pageInfo.hasNextPage)
        }
        return latestUpdatesParse(response)
    }

    override fun mangaDetailsRequest(manga: SManga) = graphQLPost(
        apiUrl,
        headers,
        DETAILS_QUERY,
        "MangaDetails",
        IdVariables(manga.url),
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<Edge>().node.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override fun chapterListRequest(manga: SManga) = graphQLPost(
        apiUrl,
        headers,
        CHAPTER_LIST_QUERY,
        "ChapterList",
        IdVariables(manga.url),
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return response.parseGraphQLAs<ChapterResponse>().node.episodes.edges
            .filter { !hideLocked || (!it.node.isLocked && !it.node.isPreview) }
            .map { it.node.toSChapter() }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override fun pageListRequest(chapter: SChapter) = graphQLPost(
        apiUrl,
        headers,
        VIEWER_QUERY,
        "GetEpisode",
        IdVariables(chapter.url),
    )

    override fun pageListParse(response: Response): List<Page> {
        val results = response.parseGraphQLAs<ViewerResponse>()
        val edge = results.node.allPagesConnection.edges
        if (edge.isEmpty()) {
            if (loginFailed) {
                throw IOException("Invalid E-Mail or Password")
            }

            throw Exception("Enter your credentials in Settings and purchase this chapter to read.")
        }

        return edge.mapIndexed { index, page ->
            Page(index, imageUrl = page.node.image.url.toHttpUrl().pathSegments.last())
        }
    }

    override fun getFilterList() = FilterList(
        TagFilter(),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    @Synchronized
    private fun getToken(): String {
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
            }
        }

        val newTokens = secureToken()
        saveTokens(newTokens)
        return newTokens.idToken
    }

    private fun login(email: String, password: String): LoginResponse {
        val body = LoginRequestBody(email, password, true).toJsonRequestBody()
        val url = "$loginUrl/accounts:signInWithPassword".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()
            .toString()
        val request = POST(url, headers, body)

        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun refresh(refreshToken: String): LoginResponse {
        val body = RefreshRequestBody("refresh_token", refreshToken).toJsonRequestBody()
        val url = "$refreshUrl/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()
            .toString()
        val request = POST(url, headers, body)

        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun secureToken(): LoginResponse {
        val body = SecureTokenRequestBody(true).toJsonRequestBody()
        val url = "$loginUrl/accounts:signUp".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()
            .toString()
        val request = POST(url, headers, body)

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
        private const val LOGIN_KEY = "AIzaSyASnOvvLWrECQKNRI0R_82droxO1QMd4O8"
    }
}
