package eu.kanade.tachiyomi.extension.ja.mangano

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.hours

@Source
abstract class MangaNo :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/query"
    private val authDomain get() = "googleapis.com"
    private val loginUrl get() = "https://identitytoolkit.$authDomain/v1"
    private val refreshUrl get() = "https://securetoken.$authDomain/v1"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()

    private var cursor: String? = null
    private var loginFailed: Boolean = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(GraphQLErrorInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = POPULAR_QUERY,
                operationName = "RankingsMonthly",
            ),
        ).parseGraphQLAs<PopularResponse>()
        val mangas = result.ranking.monthly2.edges.map { it.node.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) cursor = null
        return client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = LATEST_QUERY,
                operationName = "NewWorks",
                variables = LatestVariables(cursor),
            ),
        ).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseGraphQLAs<SeriesResponse>().newWorks2
        cursor = result.pageInfo.endCursor
        val mangas = result.edges.orEmpty().map { it.node.toSManga() }
        return MangasPage(mangas, result.pageInfo.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page == 1) cursor = null
        if (query.isNotBlank()) {
            return client.post(
                url = apiUrl,
                headers = apiHeaders(),
                body = graphQLBody(
                    query = SEARCH_QUERY,
                    operationName = "Search",
                    variables = SearchVariables(query, cursor),
                ),
            ).toMangasPage()
        }

        val filter = filters.firstInstance<TagFilter>()
        val result = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = TAG_QUERY,
                operationName = "Tag",
                variables = TagFilterVariables(filter.value, 100, cursor),
            ),
        ).parseGraphQLAs<TagResponse>().tag.works
        cursor = result.pageInfo.endCursor
        val mangas = result.edges.orEmpty().map { it.node.toSManga() }
        return MangasPage(mangas, result.pageInfo.hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TagFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val work = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = DETAILS_QUERY,
                operationName = "MangaDetails",
                variables = IdVariables(manga.url),
            ),
        ).parseGraphQLAs<Edge>().node

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = work.episodes!!.edges
            .filter { !hideLocked || (!it.node.isLocked && !it.node.isPreview) }
            .map { it.node.toSChapter() }
            .reversed()

        return SMangaUpdate(
            work.toSManga(),
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val edges = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = VIEWER_QUERY,
                operationName = "GetEpisode",
                variables = IdVariables(chapter.url),
            ),
        ).parseGraphQLAs<ViewerResponse>().node.allPagesConnection.edges

        if (edges.isEmpty()) {
            if (loginFailed) {
                throw IOException("Invalid E-Mail or Password")
            }

            throw Exception("Enter your credentials in Settings and purchase this chapter to read.")
        }

        return edges.mapIndexed { index, page ->
            Page(index, imageUrl = page.node.image.url.toHttpUrl().pathSegments.last())
        }
    }

    private suspend fun apiHeaders(): Headers = headersBuilder()
        .set("Authorization", "Bearer ${getToken()}")
        .build()

    private suspend fun getToken(): String = tokenMutex.withLock {
        if (System.currentTimeMillis() < preferences.getLong(EXPIRES, 0L)) return preferences.getString(TOKEN, null)!!

        val tokens = preferences.getString(REFRESH, null)?.let { refresh(it) }
            ?: login()
            ?: secureToken()

        preferences.edit().apply {
            putString(TOKEN, tokens.idToken)
            putString(REFRESH, tokens.refreshToken)
            putLong(EXPIRES, System.currentTimeMillis() + 1.hours.inWholeMilliseconds)
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
        val url = "$refreshUrl/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()

        return try {
            client.post(url, RefreshRequestBody("refresh_token", refreshToken).toJsonRequestBody()).parseAs<LoginResponse>()
        } catch (_: HttpException) {
            null
        }
    }

    private suspend fun secureToken(): LoginResponse {
        val url = "$loginUrl/accounts:signUp".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()

        return client.post(url, SecureTokenRequestBody(true).toJsonRequestBody()).parseAs<LoginResponse>()
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
        private const val LOGIN_KEY = "AIzaSyASnOvvLWrECQKNRI0R_82droxO1QMd4O8"
    }
}
