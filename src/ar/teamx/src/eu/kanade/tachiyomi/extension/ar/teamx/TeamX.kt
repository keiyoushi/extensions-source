package eu.kanade.tachiyomi.extension.ar.teamx

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

class TeamX :
    HttpSource(),
    ConfigurableSource {
    override val name = "Team X"

    private val defaultBaseUrl = "https://olympustaff.com"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "ar"

    override val supportsLatest = true

    override val client = network.cloudflareClient
        .newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(10, 1, TimeUnit.SECONDS)
        .build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    /**
     * Whether filters have been fetched
     */
    private var filtersFetched: Boolean = false

    private val nextPageSelector = "a[rel=next]"

    // Popular

    private val popularMangaSelector = "div.listupd div.bsx"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/" + if (page > 1) "?page=$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                title = element.select("a").attr("title")
                setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
                thumbnail_url =
                    element.select("img").let {
                        if (it.hasAttr("data-src")) {
                            it.attr("abs:data-src")
                        } else {
                            it.attr("abs:src")
                        }
                    }
            }
        }

        val hasNextPage = nextPageSelector.let { document.selectFirst(it) } != null

        fetchFiltersIfNeeded(document)

        return MangasPage(entries, hasNextPage)
    }

    // Latest

    private val titlesAdded = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) titlesAdded.clear()

        return GET(baseUrl + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val unfilteredManga = document.select("div.last-chapter div.box")

        val mangaList =
            unfilteredManga
                .map { element ->
                    SManga.create().apply {
                        val linkElement = element.select("div.info a")
                        title = linkElement.select("h3").text()
                        setUrlWithoutDomain(linkElement.first()!!.attr("href"))
                        thumbnail_url = element.select("div.imgu img").first()!!.absUrl("src")
                    }
                }.distinctBy {
                    it.title
                }.filter {
                    !titlesAdded.contains(it.title)
                }

        titlesAdded.addAll(mangaList.map { it.title })

        return MangasPage(mangaList, document.select(nextPageSelector).isNotEmpty())
    }

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!query.startsWith("http")) {
            return super.fetchSearchManga(page, query, filters)
        }

        val baseHost = baseUrl.toHttpUrl().host
        val seriesUrl = query.toHttpUrl()

        if (seriesUrl.host != baseHost) throw Exception("Unsupported URL")
        val segment =
            seriesUrl.pathSegments
                .getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: throw Exception("Invalid URL format")

        val manga = SManga.create().apply { url = "/series/$segment" }

        return fetchMangaDetails(manga)
            .map {
                MangasPage(
                    listOf(
                        it.apply {
                            url = manga.url
                            initialized = true
                        },
                    ),
                    false,
                )
            }
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = if (query.isNotBlank()) {
        val url = "$baseUrl/ajax/search".toHttpUrl().newBuilder()
        url.addQueryParameter("keyword", query)
        GET(url.build(), headers)
    } else {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.toUriPart())
                }
                is GenreFilter -> {
                    url.addQueryParameter("genre", filter.toUriPart())
                }
                else -> {}
            }
        }

        GET(url.build(), headers)
    }

    private val searchMangaSelector = "a.items-center, " + popularMangaSelector

    override fun searchMangaParse(response: Response): MangasPage = if ("series" in response.request.url.pathSegments) {
        popularMangaParse(response)
    } else {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector).map { element ->
            SManga.create().apply {
                title = element.selectFirst("h4")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        MangasPage(mangas, false)
    }

    private fun fetchFiltersIfNeeded(document: Document) {
        if (filtersFetched) return

        fun load(selector: String, target: MutableList<Pair<String?, String>>) {
            document.select(selector).forEach {
                target.add(it.attr("value") to it.text())
            }
        }

        load("select#select_genre option", genreFilters)
        load("select#select_type option", typeFilters)
        load("select#select_state option", statusFilters)

        filtersFetched = true
    }

    override fun getFilterList() = FilterList(
        if (filtersFetched) {
            listOf(
                Filter.Header("NOTE: Filters are ignored when using text search."),
                Filter.Separator(),
                TypeFilter(typeFilters),
                StatusFilter(statusFilters),
                GenreFilter(genreFilters),
            )
        } else {
            listOf(
                Filter.Header(
                    "Filters are not loaded yet.\n" +
                        "Open Popular Manga and press 'Reset' to load filters.",
                ),
            )
        },
    )

    private class TypeFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Type", vals)

    private class StatusFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Status", vals)

    private class GenreFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Category", vals)

    private val typeFilters: MutableList<Pair<String?, String>> = mutableListOf()
    private val statusFilters: MutableList<Pair<String?, String>> = mutableListOf()
    private val genreFilters: MutableList<Pair<String?, String>> = mutableListOf()

    open class UriPartFilter(
        displayName: String,
        private val vals: List<Pair<String?, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.second }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].first
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select("div.author-info-title h1").text()
        description = document.select("div.review-content").text()
        if (description.isNullOrBlank()) {
            description = document.select("div.review-content p").text()
        }
        genre = document.select("div.review-author-info a").joinToString { it.text() }
        thumbnail_url = document.select("div.text-right img").first()!!.absUrl("src")
        status =
            document
                .selectFirst(".full-list-info > small:first-child:contains(الحالة) + small")
                ?.text()
                .toStatus()
        author =
            document
                .selectFirst(".full-list-info > small:first-child:contains(الرسام) + small")
                ?.text()
                ?.takeIf { it != "غير معروف" }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val allElements = mutableListOf<Element>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select("div.chapter-card")
            if (pageChapters.isEmpty()) {
                break
            }

            allElements += pageChapters

            val hasNextPage = document.select(nextPageSelector).isNotEmpty()
            if (!hasNextPage) {
                break
            }

            val nextUrl = document.select(nextPageSelector).attr("href")

            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return allElements.map { element ->
            SChapter.create().apply {
                val chpNum = element.attr("data-number")
                val chpTitle = element.selectFirst("div.chapter-info div.chapter-title")?.text()

                name = buildString {
                    append("الفصل $chpNum")
                    chpTitle
                        ?.takeIf {
                            it.isNotBlank() &&
                                it != chpNum &&
                                it != "الفصل $chpNum" &&
                                it != "الفصل رقم $chpNum"
                        }?.let { append(" - $it") }
                } + "\u200F"

                // data-date is Unix timestamp (seconds)
                date_upload = element
                    .attr("data-date")
                    .toLongOrNull()
                    ?.times(1000)
                    ?: 0L

                setUrlWithoutDomain(element.select("a").attr("href"))
            }
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمرة" -> SManga.ONGOING
        "قادم قريبًا" -> SManga.ONGOING // "coming soon"
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document
            .select("div.image_list canvas[data-src], div.image_list img[src]")
            .mapIndexed { i, element ->
                val url =
                    when {
                        element.hasAttr("src") -> element.absUrl("src")
                        else -> element.absUrl("data-src")
                    }
                Page(i, imageUrl = url)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val RESTART_APP = ".لتطبيق الإعدادات الجديدة أعد تشغيل التطبيق"
        private const val BASE_URL_PREF_TITLE = "تعديل الرابط"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = ".للاستخدام المؤقت. تحديث التطبيق سيؤدي الى حذف الإعدادات"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref =
            androidx.preference.EditTextPreference(screen.context).apply {
                key = BASE_URL_PREF
                title = BASE_URL_PREF_TITLE
                summary = BASE_URL_PREF_SUMMARY
                this.setDefaultValue(defaultBaseUrl)
                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "Default: $defaultBaseUrl"

                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                    true
                }
            }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences
                    .edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }
}
