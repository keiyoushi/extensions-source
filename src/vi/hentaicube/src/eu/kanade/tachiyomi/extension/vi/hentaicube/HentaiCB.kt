package eu.kanade.tachiyomi.extension.vi.hentaicube

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCB :
    Madara(
        "CBHentai",
        "https://2tencb.pro",
        "vi",
        SimpleDateFormat("dd/MM/yyyy", Locale("vi")),
    ),
    ConfigurableSource {

    override val id: Long = 823638192569572166

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .followRedirects(false)
        .addInterceptor { chain ->
            val maxRedirects = 5
            var request = chain.request()
            var response = chain.proceed(request)
            var redirectCount = 0

            while (response.isRedirect && redirectCount < maxRedirects) {
                val newUrl = response.header("Location") ?: break
                val newUrlHttp = newUrl.toHttpUrl()
                val redirectedDomain = newUrlHttp.run { "$scheme://$host" }
                if (redirectedDomain != baseUrl) {
                    synchronized(prefsLock) {
                        preferences.edit().putString(BASE_URL_PREF, redirectedDomain).commit()
                    }
                }
                response.close()
                request = request.newBuilder()
                    .url(newUrlHttp)
                    .build()
                response = chain.proceed(request)
                redirectCount++
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                throw java.io.IOException("Too many redirects: $maxRedirects")
            }
            response
        }
        .build()

    private val preferences: SharedPreferences = getPreferences()
    private val prefsLock = Any()

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
    private fun getPrefBaseUrl(): String = synchronized(prefsLock) {
        preferences.getString(BASE_URL_PREF, super.baseUrl)!!
    }

    override val baseUrl: String
        get() = getPrefBaseUrl().ifBlank { super.baseUrl }

    override val filterNonMangaItems = false

    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        val img = element.selectFirst("img")
        thumbnail_url = imageFromElement(img!!)?.replace(thumbnailOriginalUrlRegex, "$1")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(mangaSubString)
                addPathSegment(query.substringAfter(URL_SEARCH_PREFIX))
                addPathSegment("")
            }.build()
            return client.newCall(GET(mangaUrl, headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        setUrlWithoutDomain(mangaUrl.toString())
                        initialized = true
                    }

                    MangasPage(listOf(manga), false)
                }
        }

        // Special characters causing search to fail
        val queryFixed = query
            .replace("–", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("…", "...")

        return super.fetchSearchManga(page, queryFixed, filters)
    }

    private val oldMangaUrlRegex = Regex("^$baseUrl/\\w+/")

    // Change old entries from mangaSubString
    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga)
        .replace(oldMangaUrlRegex, "$baseUrl/$mangaSubString/")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")
            val mangaId = chaptersWrapper.attr("data-id")

            val allChapters = Elements()
            var page = 1

            while (true) {
                val xhrRequest = xhrChaptersRequest(mangaUrl, page)
                var xhrResponse = client.newCall(xhrRequest).execute()

                // Newer Madara versions throws HTTP 400 when using the old endpoint.
                if (xhrResponse.code == 400 && page == 1) {
                    xhrResponse.close()
                    val oldRequest = oldXhrChaptersRequest(mangaId)
                    xhrResponse = client.newCall(oldRequest).execute()
                }

                val xhrDocument = xhrResponse.asJsoup()
                allChapters.addAll(xhrDocument.select(chapterListSelector()))

                val hasNextPage = xhrDocument.selectFirst("div.pagination a[data-page='${page + 1}']") != null
                xhrResponse.close()

                if (!hasNextPage) {
                    break
                }
                page++
            }
            chapterElements = allChapters
        }

        return chapterElements.map(::chapterFromElement)
    }

    private fun xhrChaptersRequest(mangaUrl: String, page: Int): Request {
        val request = xhrChaptersRequest(mangaUrl)
        if (page <= 1) return request

        val url = request.url.newBuilder()
            .addQueryParameter("t", page.toString())
            .build()

        return request.newBuilder().url(url).build()
    }

    override fun pageListParse(document: Document): List<Page> = super.pageListParse(document).distinctBy { it.imageUrl }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = "$BASE_URL_PREF_SUMMARY${getPrefBaseUrl()}"
            setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${super.baseUrl}"

            val validate = { str: String ->
                if (str.isBlank()) {
                    true
                } else {
                    runCatching { str.toHttpUrl() }.isSuccess && domainRegex.matchEntire(str) != null
                }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid) "https://example.com" else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val isValid = validate(newValue as String)
                if (isValid) {
                    summary = "$BASE_URL_PREF_SUMMARY$newValue"
                }
                isValid
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt.\n" +
                "Để trống để sử dụng URL mặc định.\n" +
                "Hiện tại sử dụng: "
        private val domainRegex = Regex("""^https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9]{1,6}$""")
    }
}
