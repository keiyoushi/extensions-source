package eu.kanade.tachiyomi.extension.vi.hangtruyen

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HangTruyen :
    ParsedHttpSource(),
    ConfigurableSource {
    override val name = "HangTruyen"
    override val lang = "vi"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val prefsLock = Any()

    override val baseUrl: String
        get() {
            return getCustomDomain().ifBlank { "https://hangtruyen.app" }
        }

    override val client = network.cloudflareClient.newBuilder()
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
                        preferences.edit().putString(CUSTOM_URL_PREF, redirectedDomain).commit()
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

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = super.fetchPopularManga(page)
        .map {
            if (page == 1) {
                MangasPage(it.mangas, true)
            } else {
                it
            }
        }

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrl/hot-nhat?type=week")
    } else {
        searchMangaRequest(page - 1, "", FilterList(SortFilter(Filter.Sort.Selection(1, false))))
    }

    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    // Latest
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter(Filter.Sort.Selection(2, false))))

    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
            .filterNotNull()
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            filterList.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val uriPart = filter.toUriPart()
                        if (uriPart.isNotEmpty()) {
                            addQueryParameter("orderBy", uriPart)
                        }
                    }

                    is UriPartMultiSelectFilter -> {
                        val uriPart = filter.toUriPart()
                        if (uriPart.isNotEmpty()) {
                            addQueryParameter(filter.param, uriPart)
                        }
                    }

                    else -> {}
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.search-result .m-post, div.list-managas .m-post"
    override fun searchMangaNextPageSelector() = ".next-page"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-detail a")!!.text()
        author = document.selectFirst("div.author p")?.text()
        description = document.selectFirst("div.sort-des div.line-clamp")?.text()
        genre = document.select("div.kind a, div.m-tags a").joinToString { it.text() }
        status = when (document.selectFirst("div.status p")?.text()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.col-image img")?.attr("abs:src")
    }

    // Chapters
    override fun chapterListSelector() = "div.list-chapters div.l-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a.ll-chap")!!
        setUrlWithoutDomain(a.attr("href"))
        name = a.text()
        date_upload = element.selectFirst("span.ll-update")?.text()?.toDate() ?: 0L
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = document.select("#read-chaps .mi-item img.reading-img").mapIndexed { index, element ->
        val img = when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            else -> element.attr("abs:src")
        }
        Page(index, imageUrl = img)
    }.distinctBy { it.imageUrl }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun getCustomDomain(): String = synchronized(prefsLock) {
        preferences.getString(CUSTOM_URL_PREF, "")!!
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = CUSTOM_URL_PREF
            title = CUSTOM_URL_PREF_TITLE
            summary = "$CUSTOM_URL_PREF_SUMMARY${getCustomDomain()}"
            setDefaultValue("")
            dialogTitle = CUSTOM_URL_PREF_TITLE

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
                        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}

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
                    summary = "$CUSTOM_URL_PREF_SUMMARY$newValue"
                }
                isValid
            }
        }.also(screen::addPreference)
    }

    // ============================= Filters ==============================

    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    override fun getFilterList(): FilterList {
        launchIO { fetchMetadata(baseUrl, client) }
        return FilterList(
            SortFilter(),
            CategoriesFilter(),
            GenresFilter(),
        )
    }

    companion object {
        private const val CUSTOM_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val CUSTOM_URL_PREF = "overrideBaseUrl"
        private const val CUSTOM_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt.\n" +
                "Để trống để sử dụng URL mặc định.\n" +
                "Hiện tại sử dụng: "

        private val domainRegex = Regex("""^https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9]{1,6}$""")
    }

    private fun String?.toDate(): Long {
        this ?: return 0L

        val secondWords = "giây"
        val minuteWords = "phút"
        val hourWords = "giờ"
        val dayWords = "ngày"
        val monthWords = "tháng"
        val yearWords = "năm"
        val agoWords = "trước"

        return try {
            if (contains(agoWords, ignoreCase = true)) {
                val trimmedDate = substringBefore(agoWords).trim().split(" ")
                val calendar = Calendar.getInstance()

                when {
                    yearWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                    monthWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                    dayWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                    hourWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                    minuteWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                    secondWords.equals(trimmedDate[1].trim(), true) -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }
                }

                calendar.timeInMillis
            } else {
                substringAfterLast(" ").let {
                    // timestamp has year
                    if (Regex("""\d+/\d+/\d\d\d\d""").find(it)?.value != null) {
                        dateFormat.parse(it)?.time ?: 0L
                    } else {
                        // Timestamp sometimes doesn't have year (current year implied)
                        dateFormat.parse("$it/$currentYear")?.time ?: 0L
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[Calendar.YEAR].toString() }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }
}
