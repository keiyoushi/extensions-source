package eu.kanade.tachiyomi.extension.en.kodansha

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

class Kodansha :
    HttpSource(),
    ConfigurableSource {
    override val name = "Kodansha"
    private val domain = "kodansha.us"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain"
    private val tokenUrl = "$apiUrl/account/token"
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val pageLimit = 24

    private var bearerToken: String? = null
    private var tokenExpiration: Long = 0L
    private var loginFailed = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor {
            val request = it.request()
            if (request.url.encodedPath.endsWith("/account/token")) {
                return@addInterceptor it.proceed(request)
            }

            val newRequest = request.newBuilder().apply {
                val token = getToken()
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }.build()

            val response = it.proceed(newRequest)

            if (response.code == 403 && request.url.pathSegments[2].contains("pages") && request.url.host == apiUrl.toHttpUrl().host) {
                if (loginFailed) {
                    throw IOException("Invalid E-Mail or Password")
                }
                throw IOException("Enter your credentials in Settings and purchase this product to read.")
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val nextPage = (page - 1) * pageLimit
        val url = "$apiUrl/discover/v2".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "0")
            .addQueryParameter("subCategory", "0")
            .addQueryParameter("includeSeries", "true")
            .addQueryParameter("showSpotLightInfo", "true")
            .addQueryParameter("category", "0")
            .addQueryParameter("fromIndex", nextPage.toString())
            .addQueryParameter("count", pageLimit.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val nextPage = (page - 1) * pageLimit
        val url = "$apiUrl/discover/v2".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "5")
            .addQueryParameter("subCategory", "0")
            .addQueryParameter("includeSeries", "true")
            .addQueryParameter("showSpotLightInfo", "true")
            .addQueryParameter("category", "0")
            .addQueryParameter("fromIndex", nextPage.toString())
            .addQueryParameter("count", pageLimit.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search/V3".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("platform", "web")
                .addQueryParameter("showSpotLightInfo", "true")
                .build()
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstance<SortFilter>()
        val statusFilter = filters.firstInstance<StatusFilter>()
        val genreFilter = filters.firstInstance<GenreFilter>()
        val ageRatingFilter = filters.firstInstance<AgeRatingFilter>()
        val nextPage = (page - 1) * pageLimit

        val url = "$apiUrl/discover/v2".toHttpUrl().newBuilder().apply {
            addQueryParameter("fromIndex", nextPage.toString())
            addQueryParameter("count", pageLimit.toString())
            addQueryParameter("showSpotLightInfo", "true")
            addQueryParameter("includeSeries", "true")
            addQueryParameter("category", "0")
            addQueryParameter("subCategory", "0")
            addQueryParameter("sort", sortFilter.value)

            val genres = genreFilter.state.filter { it.state }.joinToString(",") { it.value }
            val ageRatings = ageRatingFilter.state.filter { it.state }.joinToString(",") { (it as CheckBox).value }

            if (genres.isNotEmpty()) {
                addQueryParameter("genreIds", genres)
            }

            if (ageRatings.isNotEmpty()) {
                addQueryParameter("ageRatings", ageRatings)
            }

            if (statusFilter.value.isNotEmpty()) {
                addQueryParameter("seriesStatus", statusFilter.value)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<EntryResponse>()
        val mangas = result.response
            .filter { it.type != "product" }
            .map { it.content.toSManga() }
        val fullCount = result.status?.fullCount
        val currentPage = response.request.url.queryParameter("fromIndex")?.toInt()
        val hasNextPage = fullCount != null && currentPage != null && (currentPage + pageLimit) < fullCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = "$baseUrl/${manga.url}".toHttpUrl().fragment
        val url = "$apiUrl/series/V2/$id"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().response.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val id = "$baseUrl/${manga.url}".toHttpUrl().fragment
        val url = "$apiUrl/product/forSeries/$id".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "web")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        var isLoggedIn = false
        val purchasedIds = try {
            val purchasedResponse = client.newCall(GET("$apiUrl/mycomics/?onlyPurchased=true", headers)).execute()
            isLoggedIn = purchasedResponse.isSuccessful
            if (purchasedResponse.isSuccessful) {
                purchasedResponse.parseAs<List<PurchasedComic>>().map { it.id }.toSet()
            } else {
                emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }

        val result = response.parseAs<List<ChapterResponse>>()
        return result.flatMap { volume ->
            buildList {
                val isVolumeLocked = volume.isLocked(purchasedIds)
                if (!hideLocked || !isVolumeLocked) {
                    add(volume.toSChapter(isVolumeLocked, volume.requiresLogin(isLoggedIn)))
                }

                volume.chapters?.forEach { chapter ->
                    val isChapterLocked = chapter.isLocked(purchasedIds)
                    if (!hideLocked || !isChapterLocked) {
                        add(chapter.toSChapter(isChapterLocked, chapter.requiresLogin(isLoggedIn)))
                    }
                }
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl().fragment!!.split(":")
        val slug = parts[0]
        val volumeNum = parts[1]
        val chapterNum = parts[2]
        val url = "$baseUrl/reader/$slug".toHttpUrl().newBuilder().apply {
            if (volumeNum != "null") addPathSegment("volume-$volumeNum")
            if (chapterNum != "null") addPathSegment("chapter-$chapterNum")
        }.build().toString()
        return url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val parts = url.fragment!!.split(":")
        val requiresLogin = parts[3] == "1"
        if (requiresLogin) {
            throw Exception("Enter your credentials in Settings to read this free chapter.")
        }

        val id = url.pathSegments.first()
        return GET("$apiUrl/comic/$id/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<List<ViewerResponse>>()
        return result.map {
            Page(it.pageNumber, it.comicId.toString())
        }
    }

    override fun imageUrlRequest(page: Page): Request = GET("$apiUrl/comic/${page.url}/pages/${page.index + 1}", headers)

    override fun imageUrlParse(response: Response): String = response.parseAs<PageResponse>().url

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        AgeRatingFilter(),
    )

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
                saveTokens(newTokens)
                return newTokens.accessToken
            } catch (_: Exception) {
            }
        }

        val email = preferences.getString(EMAIL_PREF_KEY, "")!!
        val password = preferences.getString(PASSWORD_PREF_KEY, "")!!

        if (email.isNotBlank() && password.isNotBlank()) {
            try {
                val newTokens = login(email, password)
                saveTokens(newTokens)
                return newTokens.accessToken
            } catch (_: Exception) {
                loginFailed = true
                return ""
            }
        }

        return ""
    }

    private fun login(email: String, password: String): LoginResponse {
        val body = LoginRequestBody(email, password).toJsonString().toRequestBody("application/json".toMediaType())
        val request = POST(tokenUrl, headers, body)
        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun refresh(refreshToken: String): LoginResponse {
        val body = RefreshRequestBody(refreshToken).toJsonString().toRequestBody("application/json".toMediaType())
        val request = POST(tokenUrl, headers, body)
        return client.newCall(request).execute().parseAs<LoginResponse>()
    }

    private fun saveTokens(response: LoginResponse) {
        val expiration = System.currentTimeMillis() + (86400 * 1000L)
        bearerToken = response.accessToken
        tokenExpiration = expiration
        preferences.edit().apply {
            putString(TOKEN, response.accessToken)
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
