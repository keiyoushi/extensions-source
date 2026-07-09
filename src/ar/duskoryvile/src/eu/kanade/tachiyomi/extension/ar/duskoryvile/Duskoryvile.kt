package eu.kanade.tachiyomi.extension.ar.duskoryvile

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class Duskoryvile :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val baseHost = baseUrl.toHttpUrl().host

    override val client = network.client.newBuilder()
        .addInterceptor(LoginInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series-list/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".dap-series-grid a[href*='series_id=']").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div[style*=font-weight]")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http")) {
            val urlObj = query.toHttpUrlOrNull()
            if (urlObj != null && urlObj.host == baseHost) {
                val seriesId = urlObj.queryParameter("series_id")
                if (seriesId != null) {
                    val manga = SManga.create().apply {
                        url = "/series-page/?series_id=$seriesId"
                    }
                    return Observable.fromCallable {
                        val fetchResponse = client.newCall(GET(baseUrl + manga.url, headers)).execute()
                        val document = fetchResponse.asJsoup()
                        manga.title = document.selectFirst(".dk-series-hero h2")!!.text()
                        manga.thumbnail_url = document.selectFirst(".dk-series-hero-img img")?.absUrl("src")
                        MangasPage(listOf(manga), false)
                    }
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".dk-series-hero h2")!!.text()
            thumbnail_url = document.selectFirst(".dk-series-hero-img img")?.absUrl("src")

            val descElement = document.selectFirst(".dk-series-hero div[style*='line-height:1.5']")
            val descriptionText = descElement?.text() ?: ""
            description = descriptionText

            genre = if (descriptionText.contains("التصنيف:")) {
                descriptionText.substringAfter("التصنيف:")
                    .split("،", ",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString()
            } else {
                null
            }
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".dk-ch-grid a.dk-ch-card").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".dk-ch-title")!!.text()
                val dateText = element.selectFirst(".dk-ch-date")?.text() ?: ""
                date_upload = dateFormat.tryParse(dateText)
            }
        }.reversed()
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".dap-pages img.dap-page").mapIndexed { index, element ->
            val imageUrl = if (element.hasAttr("data-src")) {
                element.absUrl("data-src")
            } else {
                element.absUrl("src")
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList()

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME_KEY
            title = "Email / Username"
            summary = "Your account identifier"
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD_KEY
            title = "Password"
            summary = "Your account password"
        }.let(screen::addPreference)
    }

    // ============================= Utilities =============================

    private inner class LoginInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath

            if (path == "/wp-login.php" || path == "/login" || path == "/login/" || path.startsWith("/login/")) {
                return chain.proceed(request)
            }

            val username = preferences.getString(PREF_USERNAME_KEY, null)
            val password = preferences.getString(PREF_PASSWORD_KEY, null)

            val cookies = client.cookieJar.loadForRequest(request.url)
            val hasLoginCookie = cookies.any { it.name.startsWith("wordpress_logged_in_") }

            if (!hasLoginCookie && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val loginBody = FormBody.Builder()
                    .add("log", username)
                    .add("pwd", password)
                    .add("wp-submit", "Login")
                    .add("redirect_to", "$baseUrl/")
                    .build()

                val loginRequest = Request.Builder()
                    .url("$baseUrl/wp-login.php")
                    .post(loginBody)
                    .headers(headers)
                    .build()

                chain.proceed(loginRequest).close()
            }

            val response = chain.proceed(request)
            val finalPath = response.request.url.encodedPath
            if (finalPath == "/login" || finalPath == "/login/" || finalPath.startsWith("/login/")) {
                response.close()
                throw IOException("يرجى تسجيل الدخول من إعدادات الإضافة\n(Please log in via extension settings)")
            }

            return response
        }
    }

    companion object {
        private const val PREF_USERNAME_KEY = "pref_username"
        private const val PREF_PASSWORD_KEY = "pref_password"
    }
}
