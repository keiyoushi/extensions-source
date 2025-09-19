package eu.kanade.tachiyomi.extension.all.myreadingmanga

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.URLUtil
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

open class MyReadingManga(override val lang: String, private val siteLang: String, private val latestLang: String) : ParsedHttpSource() {

    /*
     *  ========== Basic Info ==========
     */
    override val name = "MyReadingManga"
    final override val baseUrl = "https://myreadingmanga.info"
    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder()
            .set("User-Agent", "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.7258.159 Mobile Safari/537.36")
            .add("X-Requested-With", randomString((1..20).random()))
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .build()

    override val supportsLatest = true

    /*
     *  ========== Popular - Random ==========
     */
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/", headers)
    }

    override fun popularMangaNextPageSelector() = null
    override fun popularMangaSelector() = ".wpp-list li"
    override fun popularMangaFromElement(element: Element) = buildManga(element.selectFirst(".wpp-post-title")!!, element.selectFirst(".wpp-thumbnail"))
    override fun popularMangaParse(response: Response): MangasPage {
        cacheAssistant()
        return super.popularMangaParse(response)
    }

    /*
     * ========== Latest ==========
     */
    @SuppressLint("DefaultLocale")
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/lang/${latestLang.lowercase()}" + if (page > 1) "/page/$page/" else "", headers) // Home Page - Latest Manga
    }

    override fun latestUpdatesNextPageSelector() = "li.pagination-next"
    override fun latestUpdatesSelector() = "article"
    override fun latestUpdatesFromElement(element: Element) = buildManga(element.selectFirst("a.entry-title-link")!!, element.selectFirst("a.entry-image-link img"))
    override fun latestUpdatesParse(response: Response): MangasPage {
        cacheAssistant()
        return super.latestUpdatesParse(response)
    }

    /*
     * ========== Search ==========
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val uri = Uri.parse("$baseUrl/page/$page/").buildUpon()
            .appendQueryParameter("s", query)
        filterList.forEach { filter ->
            // If enforce language is checked, then apply language filter automatically
            if (filter is EnforceLanguageFilter && filter.state) {
                filter.addToUri(uri)
            } else if (filter is UriFilter) {
                filter.addToUri(uri)
            }
        }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = "li.pagination-next"
    override fun searchMangaSelector() = "article"
    override fun searchMangaFromElement(element: Element) = buildManga(element.selectFirst("a.entry-title-link")!!, element.selectFirst("a.entry-image-link img"))
    override fun searchMangaParse(response: Response): MangasPage {
        cacheAssistant()
        return super.searchMangaParse(response)
    }

    /*
     * ========== Building manga from element ==========
     */
    private fun buildManga(titleElement: Element, thumbnailElement: Element?): SManga {
        val manga = SManga.create().apply {
            setUrlWithoutDomain(titleElement.attr("href"))
            title = cleanTitle(titleElement.text())
        }
        if (thumbnailElement != null) manga.thumbnail_url = getThumbnail(getImage(thumbnailElement))
        return manga
    }

    private val extensionRegex = Regex("""\.(jpg|png|jpeg|webp)""")

    private fun getImage(element: Element): String? {
        val url = when {
            element.attr("data-src").contains(extensionRegex) -> element.attr("abs:data-src")
            element.attr("data-cfsrc").contains(extensionRegex) -> element.attr("abs:data-cfsrc")
            element.attr("src").contains(extensionRegex) -> element.attr("abs:src")
            else -> element.attr("abs:data-lazy-src")
        }

        return if (URLUtil.isValidUrl(url)) url else null
    }

    // removes resizing
    private fun getThumbnail(thumbnailUrl: String?): String? {
        thumbnailUrl ?: return null
        val url = thumbnailUrl.substringBeforeLast("-") + "." + thumbnailUrl.substringAfterLast(".")
        return if (URLUtil.isValidUrl(url)) url else null
    }

    // cleans up the name removing author and language from the title
    private val titleRegex = Regex("""\[[^]]*]""")
    private fun cleanTitle(title: String) = title.replace(titleRegex, "").substringBeforeLast("(").trim()

    // Manga Details
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val needCover = manga.thumbnail_url?.let { !client.newCall(GET(it, headers)).execute().isSuccessful } ?: true

        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response.asJsoup(), needCover).apply { initialized = true }
            }
    }

    private fun mangaDetailsParse(document: Document, needCover: Boolean = true): SManga {
        return SManga.create().apply {
            title = cleanTitle(document.select("h1").text())
            author = document.selectFirst(".entry-terms a[href*=artist]")?.text()
            artist = author
            genre = document.select(".entry-header p a[href*=genre], [href*=tag], span.entry-categories a").joinToString { it.text() }
            val basicDescription = document.select("h1").text()
            // too troublesome to achieve 100% accuracy assigning scanlator group during chapterListParse
            val scanlatedBy = document.select(".entry-terms:has(a[href*=group])").firstOrNull()
                ?.select("a[href*=group]")?.joinToString(prefix = "Scanlated by: ") { it.text() }
            val extendedDescription = document.select(".entry-content p:not(p:containsOwn(|)):not(.chapter-class + p)").joinToString("\n") { it.text() }
            description = listOfNotNull(basicDescription, scanlatedBy, extendedDescription).joinToString("\n").trim()
            status = when (document.selectFirst("a[href*=status]")?.text()) {
                "Completed" -> SManga.COMPLETED
                "Ongoing" -> SManga.ONGOING
                "Licensed" -> SManga.LICENSED
                "Dropped" -> SManga.CANCELLED
                "Discontinued" -> SManga.CANCELLED
                "Hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            if (needCover) {
                thumbnail_url = getThumbnail(
                    getImage(
                        client.newCall(GET("$baseUrl/search/?search=${document.location()}", headers))
                            .execute().asJsoup().selectFirst("div.wdm_results div.p_content img")!!,
                    ),
                )
            }
        }
    }

    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException()

    /*
     * ========== Building chapters from element ==========
     */
    override fun chapterListSelector() = "a[class=page-numbers]"

    @SuppressLint("DefaultLocale")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val date = parseDate(document.select(".entry-time").text())
        // create first chapter since its on main manga page
        chapters.add(createChapter("1", document.baseUri(), date, "Part 1"))
        // see if there are multiple chapters or not
        val lastChapterNumber = document.select(chapterListSelector()).last()?.text()
        if (lastChapterNumber != null) {
            // There are entries with more chapters but those never show up,
            // so we take the last one and loop it to get all hidden ones.
            // Example: 1 2 3 4 .. 7 8 9 Next
            for (i in 2..lastChapterNumber.toInt()) {
                chapters.add(createChapter(i.toString(), document.baseUri(), date, "Part $i"))
            }
        }
        chapters.reverse()
        return chapters
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(date)?.time ?: 0
    }

    private fun createChapter(pageNumber: String, mangaUrl: String, date: Long, chname: String): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("$mangaUrl/$pageNumber")
        chapter.name = chname
        chapter.date_upload = date
        return chapter
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    /*
     * ========== Building pages from element ==========
     */
    override fun pageListParse(document: Document): List<Page> {
        return (document.select("div.entry-content img") + document.select("div.separator img[data-src]"))
            .mapNotNull { getImage(it) }
            .distinct()
            .mapIndexed { i, url -> Page(i, "", url) }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    /*
     * ========== Parse filters from pages ==========
     *
     * In a recent (2025) update, MRM updated their search interface. As such, there is no longer
     * pages listing every tags, every author, etc. (except for Langs and Genres). The search page
     * display the top 25 results for each filter category. Since these lists aren't exhaustive, we
     * call them "Popular"
     *
     * TODO : MRM have a meta sitemap (https://myreadingmanga.info/sitemap_index.xml) that links to
     * tag/genre/pairing/etc xml sitemaps. Filters could be populated from those instead of HTML pages
     */
    private var filtersCached = false
    private var mainPage = ""
    private var searchPage = ""

    // Grabs page containing filters and puts it into cache
    private fun filterAssist(url: String): String {
        val response = client.newCall(GET(url, headers)).execute()
        return response.body.string()
    }

    private fun cacheAssistant() {
        if (!filtersCached) {
            mainPage = filterAssist(baseUrl)
            searchPage = filterAssist("$baseUrl/?s=")
            filtersCached = true
        }
    }

    // Parses main page for filters
    private fun getFiltersFromMainPage(filterTitle: String): List<MrmFilter> {
        val document = if (mainPage == "") {
            filtersCached = false
            null
        } else {
            filtersCached = true
            Jsoup.parse(mainPage)
        }
        val parent = document?.select(".widget-title")?.first { it.text() == filterTitle }?.parent()
        return parent?.select(".tag-cloud-link")
            ?.map { MrmFilter(it.text(), it.attr("href").split("/").reversed()[1]) }
            ?: listOf(MrmFilter("Press 'Reset' to load filters", ""))
    }

    // Parses search page for filters
    private fun getFiltersFromSearchPage(filterTitle: String, isSelectDropdown: Boolean = false): List<MrmFilter> {
        val document = if (searchPage == "") {
            filtersCached = false
            null
        } else {
            filtersCached = true
            Jsoup.parse(searchPage)
        }
        val parent = document?.select(".ep-filter-title")?.first { it.text() == filterTitle }?.parent()

        val filters: List<MrmFilter>? = if (isSelectDropdown) {
            parent?.select("option")?.map { MrmFilter(it.text(), it.attr("value")) }
        } else {
            parent?.select(".term")?.map { MrmFilter(it.text(), it.attr("data-term-slug")) }
        }

        return filters ?: listOf(MrmFilter("Press 'Reset' to load filters", ""))
    }

    // Generates the filter lists for app
    override fun getFilterList(): FilterList {
        return FilterList(
            EnforceLanguageFilter(siteLang),
            SearchSortTypeList(getFiltersFromSearchPage("Sort by", true)),
            GenreFilter(getFiltersFromMainPage("Genres")),
            CatFilter(getFiltersFromSearchPage("Category")),
            TagFilter(getFiltersFromSearchPage("Tag")),
            ArtistFilter(getFiltersFromSearchPage("Circle/ artist")),
            PairingFilter(getFiltersFromSearchPage("Pairing")),
            StatusFilter(getFiltersFromSearchPage("Status")),
        )
    }

    private class EnforceLanguageFilter(val siteLang: String) : Filter.CheckBox("Enforce language", true), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state) uri.appendQueryParameter("ep_filter_lang", siteLang)
        }
    }

    private class SearchSortTypeList(SORT: List<MrmFilter>) : UriSelectOneFilter("Sort by", "ep_sort", SORT)
    private class GenreFilter(GENRES: List<MrmFilter>) : UriSelectFilter("Genre", "ep_filter_genre", GENRES)
    private class CatFilter(CATID: List<MrmFilter>) : UriSelectFilter("Popular Categories", "ep_filter_category", CATID)
    private class TagFilter(POPTAG: List<MrmFilter>) : UriSelectFilter("Popular Tags", "ep_filter_post_tag", POPTAG)
    private class ArtistFilter(POPART: List<MrmFilter>) : UriSelectFilter("Popular Artists", "ep_filter_artist", POPART)
    private class PairingFilter(PAIR: List<MrmFilter>) : UriSelectFilter("Popular Pairings", "ep_filter_pairing", PAIR)
    private class StatusFilter(STATUS: List<MrmFilter>) : UriSelectFilter("Status", "ep_filter_status", STATUS)

    private class MrmFilter(name: String, val value: String) : Filter.CheckBox(name)
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        vals: List<MrmFilter>,
    ) : Filter.Group<MrmFilter>(displayName, vals), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            val checked = state.filter { it.state }.ifEmpty { return }
                .joinToString(",") { it.value }

            uri.appendQueryParameter(uriParam, checked)
        }
    }

    private open class UriSelectOneFilter(
        displayName: String,
        val uriParam: String,
        val vals: List<MrmFilter>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0) {
                uri.appendQueryParameter(uriParam, vals[state].value)
            }
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }
}
