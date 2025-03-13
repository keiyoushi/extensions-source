package eu.kanade.tachiyomi.extension.ar.mangaswat

import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSwat :
    MangaThemesia(
        "MangaSwat",
        "https://swatscans.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by getPreferencesLazy()

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
        val xhrHeaders = headersBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val formBody = FormBody.Builder()
            .add("action", "more_manga_home")
            .add("paged", (page - 1).toString())
            .build()

        return POST("$baseUrl/ajax-request", xhrHeaders, formBody)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return json
            .decodeFromString<MoreMangaHomeDto>(response.body.string())
            .html.toResponseBody("text/html".toMediaType())
            .let { response.newBuilder().body(it).build() }
            .let { super.latestUpdatesParse(it) }
            .let { page ->
                MangasPage(
                    mangas = page.mangas,
                    hasNextPage = page.mangas.size >= 24, // info not present
                )
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        val urlBuilder = request.url.newBuilder()

        // remove trailing slash
        if (request.url.pathSegments.last().isBlank()) {
            urlBuilder.removePathSegment(
                request.url.pathSegments.lastIndex,
            )
        }

        if (query.isNotBlank()) {
            urlBuilder
                .removePathSegment(0)
                .removeAllQueryParameters("title")
                .addQueryParameter("s", query)
                .build()
        }

        return request.newBuilder()
            .url(urlBuilder.build())
            .build()
    }

    override val orderByFilterOptions = arrayOf(
        Pair(intl["order_by_filter_default"], ""),
        Pair(intl["order_by_filter_az"], "a-z"),
        Pair(intl["order_by_filter_za"], "z-a"),
        Pair(intl["order_by_filter_latest_update"], "update"),
        Pair(intl["order_by_filter_latest_added"], "added"),
        Pair(intl["order_by_filter_popular"], "popular"),
    )

    override fun searchMangaNextPageSelector() = "a[rel=next]"

    override val seriesTitleSelector = "h1[itemprop=headline]"
    override val seriesArtistSelector = "span:contains(الناشر) i"
    override val seriesAuthorSelector = "span:contains(المؤلف) i"
    override val seriesGenreSelector = "span:contains(التصنيف) a, .mgen a"
    override val seriesTypeSelector = "span:contains(النوع) a"
    override val seriesStatusSelector = "span:contains(الحالة)"

    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.selectFirst("script:containsData(ts_reader)")!!.data()
        val jsonString = scriptContent.substringAfter("ts_reader.run(").substringBefore(");")
        val tsReader = json.decodeFromString<TSReader>(jsonString)
        val imageUrls = tsReader.sources.firstOrNull()?.images ?: return emptyList()
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
    }

    override fun chapterListSelector() = "div.bxcl li, ul div:has(span.lchx)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lch a, .chapternum").text().ifBlank { urlElements.last()!!.text() }
        date_upload = element.selectFirst(".chapter-date")?.text().parseChapterDate()
    }

    @Serializable
    class TSReader(
        val sources: List<ReaderImageSource>,
    )

    @Serializable
    class ReaderImageSource(
        val images: List<String>,
    )

    @Serializable
    class MoreMangaHomeDto(
        val html: String,
    )

    companion object {
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
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
            dialogMessage = "Default: ${super.baseUrl}"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
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
