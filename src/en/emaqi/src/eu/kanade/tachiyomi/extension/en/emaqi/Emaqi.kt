package eu.kanade.tachiyomi.extension.en.emaqi

import android.text.InputType
import android.util.Base64
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@Source
abstract class Emaqi :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/graphql"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()
    private val keyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private var cursor: String? = null
    private var loginFailed: Boolean = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) cursor = null
        return client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = SERIES_QUERY,
                operationName = "FetchHomeSection",
                variables = SeriesVariables("this-week-s-bestsellers", cursor),
            ),
        ).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) cursor = null
        return client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = SERIES_QUERY,
                operationName = "FetchHomeSection",
                variables = SeriesVariables("hot-release", cursor),
            ),
        ).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseGraphQLAs<SeriesResponse>().homeSection.mangaConn
        cursor = result.pageInfo.endCursor
        val mangas = result.edges.map { it.node.comic.toSManga() }
        val hasNextPage = result.pageInfo.hasNextPage
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.checked.orEmpty()
        val mode = filters.firstInstanceOrNull<GenreModeFilter>()?.isOr
        val tagSlugGroups = if (mode == true) listOf(TagGroup(genres)) else genres.map { TagGroup(listOf(it)) }
        val result = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = SEARCH_QUERY,
                operationName = "Search",
                variables = SearchVariables(
                    SearchInput(
                        keyword = query,
                        tagSlugGroups = tagSlugGroups,
                        page = page,
                        limit = SEARCH_LIMIT,
                    ),
                ),
            ),
        ).parseGraphQLAs<SearchResponse>()
        val mangas = result.search.map { it.toSManga() }
        val hasNextPage = mangas.size == SEARCH_LIMIT
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        GenreModeFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.memo["slug"]!!.string}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicId = manga.url.substringBefore("#") // for old url compatibility
        val result = client.post(
            url = apiUrl,
            headers = apiHeaders(),
            body = graphQLBody(
                query = COMIC_QUERY,
                operationName = "FetchComicData",
                variables = DetailsVariables(comicId),
            ),
        ).parseGraphQLAs<ComicDataResponse>()
        val comic = result.comicVolumes.comic

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.chapters
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(comic.slug) }
            .reversed()

        val volumeList = result.comicVolumes.volumes
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(comic.slug) }
            .reversed()

        return SMangaUpdate(
            comic.toSManga(),
            chapterList + volumeList,
        )
    }

    // Volume: https://emaqi.com/reader/isekai-territory-reform-starting-public-works-with-earth-magic-vol-1
    // OneShot: https://emaqi.com/reader/the-tracks-we-left
    // Chapter: https://emaqi.com/reader/dealing-with-mikadono-sisters-is-a-breeze?type=chapter&chapter=1
    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        if (chapter.memo["type"]!!.string == "volume") return "$baseUrl/reader/$slug"
        return "$baseUrl/reader/$slug?type=chapter&chapter=${chapter.url}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val comicId = chapter.memo["comicId"]!!.string
        val body = if (chapter.memo["type"]!!.string == "volume") {
            graphQLBody(
                query = VOLUME_QUERY,
                operationName = "FetchMangaContents",
                variables = VolumeViewerVariables(comicId, chapter.memo["volumeNumber"]!!.int),
            )
        } else {
            graphQLBody(
                query = CHAPTER_QUERY,
                operationName = "FetchChapterContents",
                variables = ChapterViewerVariables(comicId, chapter.url.toInt()),
            )
        }

        val contentHeaders = apiHeaders().newBuilder()
            .set("X-Hash", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
            .build()

        val contents = client.post(apiUrl, contentHeaders, body).parseGraphQLAs<ViewerResponse>().chapter.contents
        if (contents == null || contents.pages.isEmpty()) {
            if (loginFailed) {
                throw IOException("Invalid E-Mail or Password")
            }
            throw Exception("Enter your credentials in Settings and purchase this chapter to read.")
        }

        val key = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
            init(Cipher.DECRYPT_MODE, keyPair.private, OAEP_PARAMS)
            doFinal(Base64.decode(contents.hash, Base64.DEFAULT))
        }.toHexString()

        return contents.pages.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.url}#$key")
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

        preferences.edit()
            .putString(TOKEN, tokens.idToken)
            .putString(REFRESH, tokens.refreshToken)
            .putLong(EXPIRES, System.currentTimeMillis() + 3_600_000L)
            .apply()
        tokens.idToken
    }

    private suspend fun login(): LoginResponse? {
        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!
        if (email.isEmpty() || password.isEmpty()) return null
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword".toHttpUrl().newBuilder()
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
        val url = "https://securetoken.googleapis.com/v1/token".toHttpUrl().newBuilder()
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
        preferences.edit()
            .remove(TOKEN)
            .remove(REFRESH)
            .remove(EXPIRES)
            .apply()
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
        private const val LOGIN_KEY = "AIzaSyC6NaQ5vOOartIGTPJHGgSP1OBjpSNKrZo"
        private const val SEARCH_LIMIT = 50
        private val OAEP_PARAMS = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )
    }
}
