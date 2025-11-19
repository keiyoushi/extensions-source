package eu.kanade.tachiyomi.extension.ar.mangaswat

import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaSwat :
    MangaThemesia(
        "MangaSwat",
        "https://appswat.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by getPreferencesLazy()

    private val apiBaseUrl = "https://appswat.com/v2/api/v2"

    override val versionId = 2

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
    }

    override val client = super.client.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .rateLimit(1)
        .build()

    // From Akuma - CSRF token
    private var storedToken: String? = null

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val newRequest = request.newBuilder()
            val token = getToken()
            val response = chain.proceed(
                newRequest
                    .addHeader("X-CSRF-TOKEN", token)
                    .build(),
            )

            if (response.code == 419) {
                response.close()
                storedToken = null // reset the token
                val newToken = getToken()
                return chain.proceed(
                    newRequest
                        .addHeader("X-CSRF-TOKEN", newToken)
                        .build(),
                )
            }

            return response
        }

        val response = chain.proceed(request)

        if (response.header("Content-Type")?.contains("text/html") != true) {
            return response
        }

        storedToken = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
            .selectFirst("head meta[name*=csrf-token]")
            ?.attr("content")

        return response
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            client.newCall(request).execute().close() // updates token in interceptor
        }
        return storedToken!!
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiBaseUrl/series/?status=79&page=$page", apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<LatestUpdatesResponse>()
        val mangas = data.results.map { it.toSManga() }
        return MangasPage(mangas, data.hasNext())
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiBaseUrl/series/?is_hot=true&page=$page", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<LatestUpdatesResponse>()
        val mangas = data.results.map { it.toSManga() }
        return MangasPage(mangas, data.hasNext())
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url
        return GET("$apiBaseUrl/series/$id/", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaDetailsDto>().toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url
        return GET("$apiBaseUrl/chapters/?serie=$id&order_by=-order&page_size=200", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var data = response.parseAs<ChapterListResponse>()
        val chapters = mutableListOf<SChapter>()
        chapters.addAll(data.results.map { it.toSChapter() })

        var nextPage = data.next
        while (nextPage != null) {
            val nextResponse = client.newCall(GET(nextPage, response.request.headers)).execute()
            data = nextResponse.parseAs<ChapterListResponse>()
            chapters.addAll(data.results.map { it.toSChapter() })
            nextPage = data.next
        }

        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.removePrefix("/chapters/").substringBefore("/")
        return GET("$apiBaseUrl/chapters/$id/", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<PageListResponse>()
        return chapter.images.map {
            Page(it.order, imageUrl = it.image)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$apiBaseUrl/series/?search=$query&page=$page", apiHeaders)
        }

        return super.searchMangaRequest(page, query, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if ("appswat.com" !in response.request.url.host) {
            return super.searchMangaParse(response)
        }

        val data = response.parseAs<LatestUpdatesResponse>()
        val mangas = data.results.map { it.toSManga() }
        return MangasPage(mangas, data.hasNext())
    }

    companion object {
        internal val apiDateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
        private const val RESTART_APP = "Restart the app to apply the new URL"
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "For temporary uses. Updating the extension will erase this setting."
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${'$'}{super.baseUrl}"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }
}
