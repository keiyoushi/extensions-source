package eu.kanade.tachiyomi.extension.en.emaqi

import android.text.InputType
import android.util.Base64
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
import java.security.KeyPairGenerator
import java.security.PrivateKey

class Emaqi :
    HttpSource(),
    ConfigurableSource {
    override val name = "emaqi"
    override val lang = "en"
    private val domain = "emaqi.com"
    override val baseUrl = "https://$domain"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain/graphql"
    private val authDomain = "googleapis.com"
    private val loginUrl = "https://identitytoolkit.$authDomain/v1"
    private val refreshUrl = "https://securetoken.$authDomain/v1"
    private val preferences by getPreferencesLazy()

    private var cursor: String? = null
    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed: Boolean = false

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            if (request.url.host.endsWith(authDomain)) {
                return@addInterceptor it.proceed(request)
            }

            val newRequest = request.newBuilder().apply {
                val token = getToken()
                if (token.isNotEmpty() && request.url.host != "pr.$domain" && request.url.host != "r.$domain") {
                    header("Authorization", "Bearer $token")
                }
            }.build()
            it.proceed(newRequest)
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) cursor = null
        return graphQLPost(
            apiUrl,
            headers,
            SERIES_QUERY,
            "FetchHomeSection",
            SeriesVariables("this-week-s-bestsellers", cursor),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<SeriesResponse>()
        val mangas = result.homeSection.mangaConn.edges.map { it.node.comic.toSManga() }
        cursor = result.homeSection.mangaConn.pageInfo.endCursor
        return MangasPage(mangas, result.homeSection.mangaConn.pageInfo.hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) cursor = null
        return graphQLPost(
            apiUrl,
            headers,
            SERIES_QUERY,
            "FetchHomeSection",
            SeriesVariables("hot-release", cursor),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return graphQLPost(
                apiUrl,
                headers,
                SEARCH_QUERY,
                "Search",
                SearchVariables(Input(query)),
            )
        }

        if (page == 1) cursor = null
        val genre = filters.firstInstance<GenreFilter>()
        return graphQLPost(
            "$apiUrl#genre",
            headers,
            GENRE_QUERY,
            "FetchGenre",
            SeriesVariables(genre.value, cursor),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.fragment == "genre") return popularMangaParse(response)
        val result = response.parseGraphQLAs<SearchResponse>()
        val mangas = result.search.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getFilterList() = FilterList(
        GenreFilter(),
    )

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().fragment
        return "$baseUrl/manga/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val comicId = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()
        return graphQLPost(
            apiUrl,
            headers,
            DETAILS_QUERY,
            "FetchMangaStatus",
            DetailsVariables(comicId),
        )
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<DetailsResponse>().manga.comic.toSManga()

    override fun chapterListRequest(manga: SManga): Request {
        val parts = "$baseUrl/${manga.url}".toHttpUrl()
        val comicId = parts.pathSegments.first()
        val slug = parts.fragment
        return graphQLPost(
            "$apiUrl#$slug",
            headers,
            CHAPTER_LIST_QUERY,
            "FetchComicData",
            DetailsVariables(comicId),
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val slug = response.request.url.fragment!!
        val result = response.parseGraphQLAs<ChapterVolumeResponse>()
        val chapters = result.chapters
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
            .reversed()
        val volumes = result.comicVolumes.volumes
            .filter { !hideLocked || (!it.isLocked && !it.isPreview) }
            .map { it.toSChapter(slug) }
            .reversed()
        return chapters + volumes
    }

    // Volume: https://emaqi.com/reader/isekai-territory-reform-starting-public-works-with-earth-magic-vol-1
    // OneShot: https://emaqi.com/reader/face
    // Chapter: https://emaqi.com/reader/dealing-with-mikadono-sisters-is-a-breeze?type=chapter&chapter=1
    override fun getChapterUrl(chapter: SChapter): String {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments
        val type = parts[1]
        val number = parts[2]
        val slug = parts[3]

        val url = if (type == "volume") {
            val volSlug = parts[4]
            "$baseUrl/reader/$slug${if (volSlug.isNotEmpty()) "-$volSlug" else ""}"
        } else {
            "$baseUrl/reader/$slug?type=chapter&chapter=$number"
        }

        return url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments
        val comicId = parts.first()
        val type = parts[1]
        val number = parts[2].toInt()
        val (xHash, privateKey) = generateXHashAndKey()
        val newHeaders = headersBuilder()
            .add("X-Hash", xHash)
            .build()

        val privateKeyStr = Base64.encodeToString(privateKey.encoded, Base64.URL_SAFE or Base64.NO_WRAP)

        if (type == "chapter") {
            return graphQLPost(
                "$apiUrl#$privateKeyStr",
                newHeaders,
                CHAPTER_QUERY,
                "FetchChapterContents",
                ChapterViewerVariables(comicId, number),
            )
        }

        return graphQLPost(
            "$apiUrl#$privateKeyStr",
            newHeaders,
            VOLUME_QUERY,
            "FetchMangaContents",
            VolumeViewerVariables(comicId, number),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseGraphQLAs<ViewerResponse>()
        val content = result.chapter.contents
        if (content == null || content.pages.isEmpty()) {
            if (loginFailed) {
                throw IOException("Invalid E-Mail or Password")
            }
            throw Exception("Enter your credentials in Settings and purchase this chapter to read.")
        }
        val privateKey = response.request.url.fragment
        val hash = content.hash
        return content.pages.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.url}#$privateKey:$hash")
        }
    }

    private fun generateXHashAndKey(): Pair<String, PrivateKey> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()
        val spkiPublicKeyBytes = keyPair.public.encoded
        val xHash = Base64.encodeToString(spkiPublicKeyBytes, Base64.NO_WRAP)
        return Pair(xHash, keyPair.private)
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
                return ""
            }
        }

        return ""
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

    private fun saveTokens(response: LoginResponse) {
        val expiration = System.currentTimeMillis() + 3_600_000L
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val EMAIL_PREF_KEY = "email_pref"
        private const val PASSWORD_PREF_KEY = "password_pref"
        private const val TOKEN = "token"
        private const val REFRESH = "refresh"
        private const val EXPIRES = "expires"
        private const val LOGIN_KEY = "AIzaSyC6NaQ5vOOartIGTPJHGgSP1OBjpSNKrZo"
    }
}
