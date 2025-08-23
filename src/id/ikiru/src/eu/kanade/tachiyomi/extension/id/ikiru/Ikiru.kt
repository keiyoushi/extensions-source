package eu.kanade.tachiyomi.extension.id.ikiru

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Ikiru() : ParsedHttpSource(), ConfigurableSource {
    // Formerly "MangaTale"
    override val id = 1532456597012176985

    override val name = "Ikiru"
    private val defaultBaseUrl: String = "https://01.ikiru.wtf"
    override val lang = "id"
    override val supportsLatest = true

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val isCi = System.getenv("CI") == "true"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(12, 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-update/?the_page=$page", headers)
    }

    override fun latestUpdatesSelector() = "#search-results > div:not(.col-span-full)"

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "#search-results ~ div.col-span-full a:has(svg):last-of-type"

    // Search
    private var searchNonce: String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // TODO: Filter

        if (searchNonce.isNullOrEmpty()) {
            try {
                val response = client.newCall(
                    GET("$baseUrl/ajax-call?type=search_form&action=get_nonce", headers),
                ).execute()
                val document = response.asJsoup()
                searchNonce = document.select("input[name=search_nonce]").attr("value")
            } catch (e: Exception) {
                Log.e(name, "Error fetching nonce: ", e)
            }
        }

        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("query", query)
            .addFormDataPart("page", "$page")
            .addFormDataPart("genre", query)
            .addFormDataPart("nonce", searchNonce ?: "")
            .build()

        val requestHeaders: Headers = Headers.Builder()
            .add("Content-Type", "multipart/form-data")
            .build()

        return POST("$baseUrl/ajax-call?action=advanced_search", requestHeaders, requestBody)
    }

    override fun searchMangaSelector() = "div.overflow-hidden:has(a.font-medium)"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.select("img").attr("abs:src")
        title = element.select("a.font-medium").text()
        status = parseStatus(element.select("div span ~ p").text())
    }

    override fun searchMangaNextPageSelector() = "div button:has(svg)"

    // Manga Details
    private fun Element.getMangaId() = selectFirst("#gallery-list")?.attr("hx-get")
        ?.substringAfter("manga_id=")?.substringBefore("&")

    override fun mangaDetailsParse(document: Document): SManga {
        document.select("article > section").let { element ->
            return SManga.create().apply {
                thumbnail_url = element.select(".contents img").attr("abs:src")
                title = element.select("h1.font-bold").text()
                // TODO: prevent status value from browse change back to default

                val altNames = element.select("h1 ~ .line-clamp-1").text()
                val synopsis = element.select("#tabpanel-description div[data-show='false']").text()
                val mangaId = "\n\nID: ${document.getMangaId()}" // for fetching chapter list
                description = when (altNames) {
                    null -> synopsis + mangaId
                    else -> "$synopsis\n\nAlternative Title: $altNames$mangaId"
                }
                genre = element.select(".space-y-2 div:has(img) p, #tabpanel-description .flex-wrap span").joinToString { it.text() }
            }
        }
    }

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val mangaId = manga.description
            ?.substringAfterLast("ID: ", "")
            ?.takeIf { it.toIntOrNull() != null }
            ?: client.newCall(mangaDetailsRequest(manga)).execute().asJsoup().getMangaId()
            ?: throw Exception("Could not find manga ID")

        val chapterListUrl = "$baseUrl/ajax-call".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", "") // keep empty for loading hidden chapter
            .addQueryParameter("action", "chapter_list")
            .build()

        val response = client.newCall(GET(chapterListUrl.toString(), headers)).execute()

        response.asJsoup().select("#chapter-list .cursor-pointer a").asReversed().map { element ->
            SChapter.create().apply {
                url = element.attr("href").substringAfter(baseUrl)
                name = element.select("span").text()
                date_upload = dateFormat.tryParse(element.select("time").attr("datetime"))
            }
        }
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("main .relative section > img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private var _cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (_cachedBaseUrl == null) {
                _cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return _cachedBaseUrl!!
        }
        set(value) {
            _cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    private val preferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, defaultBaseUrl).let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Edit source URL"
            summary = "$baseUrl.Restart aplikasi jika belum berubah \nFor temporary use, if the extension is updated the change will be lost."
            dialogTitle = title
            dialogMessage = "Default URL:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the application to apply the changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private fun parseStatus(element: String?): Int {
            if (element.isNullOrEmpty()) {
                return SManga.UNKNOWN
            }
            return when (element.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "on hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        }

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }
}
