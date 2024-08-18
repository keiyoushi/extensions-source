package eu.kanade.tachiyomi.multisrc.mangathemesia

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

// Formerly WPMangaStream & WPMangaReader -> MangaThemesia
abstract class MangaThemesia(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    val mangaUrlDirectory: String = "/manga",
    val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) : ParsedHttpSource() {

    protected open val json: Json by injectLazy()

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es"),
        classLoader = javaClass.classLoader!!,
    )

    open val projectPageString = "/project"

    // Popular (Search with popular order and nothing else)
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest (Search with update order and nothing else)
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX).not()) return super.fetchSearchManga(page, query, filters)

        val mangaPath = try {
            mangaPathFromUrl(query.substringAfter(URL_SEARCH_PREFIX))
                ?: return Observable.just(MangasPage(emptyList(), false))
        } catch (e: Exception) {
            return Observable.error(e)
        }

        return fetchMangaDetails(
            SManga.create()
                .apply { this.url = "$mangaUrlDirectory/$mangaPath/" },
        )
            .map {
                // Isn't set in returned manga
                it.url = "$mangaUrlDirectory/$mangaPath/"
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("title", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.selectedValue())
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.selectedValue())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.selectedValue())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val value = if (it.state == Filter.TriState.STATE_EXCLUDE) "-${it.value}" else it.value
                            url.addQueryParameter("genre[]", value)
                        }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }
        url.addPathSegment("")
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (genrelist == null) {
            genrelist = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }

        return super.searchMangaParse(response)
    }

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx, .listo .bs .bsx"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun searchMangaNextPageSelector() = "div.pagination .next, div.hpage .r"

    // Manga details
    private fun selector(selector: String, contains: List<String>): String {
        return contains.joinToString(", ") { selector.replace("%s", it) }
    }

    open val seriesDetailsSelector = "div.bigcontent, div.animefull, div.main-info, div.postbody"

    open val seriesTitleSelector = "h1.entry-title, .ts-breadcrumb li:last-child span"

    open val seriesArtistSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span, span:contains(%s)",
        listOf(
            "artist",
            "Artiste",
            "Artista",
            "الرسام",
            "الناشر",
            "İllüstratör",
            "Çizer",
        ),
    )

    open val seriesAuthorSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span, span:contains(%s)",
        listOf(
            "Author",
            "Auteur",
            "autor",
            "المؤلف",
            "Mangaka",
            "seniman",
            "Pengarang",
            "Yazar",
        ),
    )

    open val seriesDescriptionSelector = ".desc, .entry-content[itemprop=description]"

    open val seriesAltNameSelector = ".alternative, .wd-full:contains(alt) span, .alter, .seriestualt, " +
        selector(
            ".infotable tr:contains(%s) td:last-child",
            listOf(
                "Alternative",
                "Alternatif",
                "الأسماء الثانوية",
            ),
        )

    open val seriesGenreSelector = "div.gnr a, .mgen a, .seriestugenre a, " +
        selector(
            "span:contains(%s)",
            listOf(
                "genre",
                "التصنيف",
            ),
        )

    open val seriesTypeSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .tsinfo .imptdt:contains(%s) a, .fmed b:contains(%s)+span, span:contains(%s) a",
        listOf(
            "type",
            "ประเภท",
            "النوع",
            "tipe",
            "Türü",
        ),
    ) + ", a[href*=type\\=]"

    open val seriesStatusSelector = selector(
        ".infotable tr:contains(%s) td:last-child, .tsinfo .imptdt:contains(%s) i, .fmed b:contains(%s)+span span:contains(%s)",
        listOf(
            "status",
            "Statut",
            "Durum",
            "連載状況",
            "Estado",
            "الحالة",
            "حالة العمل",
            "สถานะ",
            "stato",
            "Statüsü",
        ),
    )

    open val seriesThumbnailSelector = ".infomanga > div[itemprop=image] img, .thumb img"

    open val altNamePrefix = "${intl["alt_names_heading"]} "

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst(seriesDetailsSelector)?.let { seriesDetails ->
            title = seriesDetails.selectFirst(seriesTitleSelector)!!.text()
            artist = seriesDetails.selectFirst(seriesArtistSelector)?.ownText().removeEmptyPlaceholder()
            author = seriesDetails.selectFirst(seriesAuthorSelector)?.ownText().removeEmptyPlaceholder()
            description = seriesDetails.select(seriesDescriptionSelector).joinToString("\n") { it.text() }.trim()
            // Add alternative name to manga description
            val altName = seriesDetails.selectFirst(seriesAltNameSelector)?.ownText().takeIf { it.isNullOrBlank().not() }
            altName?.let {
                description = "$description\n\n$altNamePrefix$altName".trim()
            }
            val genres = seriesDetails.select(seriesGenreSelector).map { it.text() }.toMutableList()
            // Add series type (manga/manhwa/manhua/other) to genre
            seriesDetails.selectFirst(seriesTypeSelector)?.ownText().takeIf { it.isNullOrBlank().not() }?.let { genres.add(it) }
            genre = genres.map { genre ->
                genre.lowercase(Locale.forLanguageTag(lang)).replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.forLanguageTag(lang))
                    } else {
                        char.toString()
                    }
                }
            }
                .joinToString { it.trim() }

            status = seriesDetails.selectFirst(seriesStatusSelector)?.text().parseStatus()
            thumbnail_url = seriesDetails.select(seriesThumbnailSelector).imgAttr()
        }
    }

    protected fun String?.removeEmptyPlaceholder(): String? {
        return if (this.isNullOrBlank() || this == "-" || this == "N/A" || this == "n/a") null else this
    }

    open fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN

        listOf(
            "مستمرة", "en curso", "ongoing", "on going", "ativo", "en cours", "en cours de publication",
            "đang tiến hành", "em lançamento", "онгоінг", "publishing", "devam ediyor", "em andamento",
            "in corso", "güncel", "berjalan", "продолжается", "updating", "lançando", "in arrivo",
            "emision", "en emision", "مستمر", "curso", "en marcha", "publicandose", "publicando",
            "连载中", "devam etmekte", "連載中",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING

        listOf(
            "completed", "completo", "complété", "fini", "achevé", "terminé", "tamamlandı", "đã hoàn thành",
            "hoàn thành", "مكتملة", "завершено", "finished", "finalizado", "completata", "one-shot",
            "bitti", "tamat", "completado", "concluído", "完結", "concluido", "已完结", "bitmiş",
        ).any { this.contains(it, ignoreCase = true) } -> SManga.COMPLETED

        listOf("canceled", "cancelled", "cancelado", "cancellato", "cancelados", "dropped", "discontinued", "abandonné")
            .any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED

        listOf("hiatus", "on hold", "pausado", "en espera", "en pause", "en attente")
            .any { this.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS

        else -> SManga.UNKNOWN
    }

    // Chapter list
    override fun chapterListSelector() = "div.bxcl li, div.cl li, #chapterlist li, ul li:has(div.chbox):has(div.eph-num)"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        countViews(document)

        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Add timestamp to latest chapter, taken from "Updated On".
        // So source which not provide chapter timestamp will have at least one
        if (chapters.isNotEmpty() && chapters.first().date_upload == 0L) {
            val date = document
                .select(".listinfo time[itemprop=dateModified], .fmed:contains(update) time, span:contains(update) time")
                .attr("datetime")
            if (date.isNotEmpty()) chapters.first().date_upload = parseUpdatedOnDate(date)
        }

        return chapters
    }

    private fun parseUpdatedOnDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lch a, .chapternum").text().ifBlank { urlElements.first()!!.text() }
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
    }

    protected open fun String?.parseChapterDate(): Long {
        if (this == null) return 0
        return try {
            dateFormat.parse(this)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Pages
    open val pageSelector = "div#readerarea img"

    override fun pageListParse(document: Document): List<Page> {
        countViews(document)

        val chapterUrl = document.location()
        val htmlPages = document.select(pageSelector)
            .filterNot { it.imgAttr().isEmpty() }
            .mapIndexed { i, img -> Page(i, chapterUrl, img.imgAttr()) }

        // Some sites also loads pages via javascript
        if (htmlPages.isNotEmpty()) { return htmlPages }

        val docString = document.toString()
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            Page(i, chapterUrl, jsonEl.jsonPrimitive.content)
        }

        return scriptPages
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    /**
     * Set it to false if you want to disable the extension reporting the view count
     * back to the source website through admin-ajax.php.
     */
    protected open val sendViewCount: Boolean = true

    protected open fun countViewsRequest(document: Document): Request? {
        val wpMangaData = document.select("script:containsData(dynamic_view_ajax)").firstOrNull()
            ?.data() ?: return null

        val postId = CHAPTER_PAGE_ID_REGEX.find(wpMangaData)?.groupValues?.get(1)
            ?: MANGA_PAGE_ID_REGEX.find(wpMangaData)?.groupValues?.get(1)
            ?: return null

        val formBody = FormBody.Builder()
            .add("action", "dynamic_view_ajax")
            .add("post_id", postId)
            .build()

        val newHeaders = headersBuilder()
            .set("Referer", document.location())
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, formBody)
    }

    /**
     * Send the view count request to the sites endpoint.
     *
     * @param document The response document with the wp-manga data
     */
    protected open fun countViews(document: Document) {
        if (!sendViewCount) {
            return
        }

        val request = countViewsRequest(document) ?: return
        val callback = object : Callback {
            override fun onResponse(call: Call, response: Response) = response.close()
            override fun onFailure(call: Call, e: IOException) = Unit
        }

        client.newCall(request).enqueue(callback)
    }

    // Filters
    protected class AuthorFilter(name: String) : Filter.Text(name)

    protected class YearFilter(name: String) : Filter.Text(name)

    open class SelectFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    protected class StatusFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    protected open val statusOptions = arrayOf(
        Pair(intl["status_filter_option_all"], ""),
        Pair(intl["status_filter_option_ongoing"], "ongoing"),
        Pair(intl["status_filter_option_completed"], "completed"),
        Pair(intl["status_filter_option_hiatus"], "hiatus"),
        Pair(intl["status_filter_option_dropped"], "dropped"),
    )

    protected class TypeFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    protected open val typeFilterOptions = arrayOf(
        Pair(intl["type_filter_option_all"], ""),
        Pair(intl["type_filter_option_manga"], "Manga"),
        Pair(intl["type_filter_option_manhwa"], "Manhwa"),
        Pair(intl["type_filter_option_manhua"], "Manhua"),
        Pair(intl["type_filter_option_comic"], "Comic"),
    )

    protected class OrderByFilter(
        name: String,
        options: Array<Pair<String, String>>,
        defaultOrder: String? = null,
    ) : SelectFilter(
        name,
        options,
        defaultOrder,
    )

    protected open val orderByFilterOptions = arrayOf(
        Pair(intl["order_by_filter_default"], ""),
        Pair(intl["order_by_filter_az"], "title"),
        Pair(intl["order_by_filter_za"], "titlereverse"),
        Pair(intl["order_by_filter_latest_update"], "update"),
        Pair(intl["order_by_filter_latest_added"], "latest"),
        Pair(intl["order_by_filter_popular"], "popular"),
    )

    protected open val popularFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "popular")) }
    protected open val latestFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "update")) }

    protected class ProjectFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    protected open val projectFilterOptions = arrayOf(
        Pair(intl["project_filter_all_manga"], ""),
        Pair(intl["project_filter_only_project"], "project-filter-on"),
    )

    protected class GenreData(
        val name: String,
        val value: String,
        val state: Int = Filter.TriState.STATE_IGNORE,
    )

    protected class Genre(
        name: String,
        val value: String,
        state: Int,
    ) : Filter.TriState(name, state)

    protected class GenreListFilter(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

    protected var genrelist: List<GenreData>? = null

    protected open fun getGenreList(): List<Genre> {
        return genrelist?.map { Genre(it.name, it.value, it.state) }.orEmpty()
    }

    open val hasProjectPage = false

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            AuthorFilter(intl["author_filter_title"]),
            YearFilter(intl["year_filter_title"]),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], getGenreList()),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header(intl["project_filter_warning"]),
                    Filter.Header(intl.format("project_filter_name", name)),
                    ProjectFilter(intl["project_filter_title"], projectFilterOptions),
                ),
            )
        }
        return FilterList(filters)
    }

    // Helpers
    /**
     * Given some string which represents an http urlString, returns path for a manga
     * which can be used to fetch its details at "$baseUrl$mangaUrlDirectory/$mangaPath"
     *
     * @param urlString: String
     *
     * @returns Path of a manga, or null if none could be found
     */
    protected open fun mangaPathFromUrl(urlString: String): String? {
        val baseMangaUrl = "$baseUrl$mangaUrlDirectory".toHttpUrl()
        val url = urlString.toHttpUrlOrNull() ?: return null

        val isMangaUrl = (baseMangaUrl.host == url.host && pathLengthIs(url, 2) && url.pathSegments[0] == baseMangaUrl.pathSegments[0])
        if (isMangaUrl) return url.pathSegments[1]

        val potentiallyChapterUrl = pathLengthIs(url, 1)
        if (potentiallyChapterUrl) {
            val response = client.newCall(GET(urlString, headers)).execute()
            if (response.isSuccessful.not()) {
                response.close()
                throw IllegalStateException("HTTP error ${response.code}")
            } else if (response.isSuccessful) {
                val links = response.asJsoup().select("a[itemprop=item]")
                //  near the top of page: home > manga > current chapter
                if (links.size == 3) {
                    val newUrl = links[1].attr("href").toHttpUrlOrNull() ?: return null
                    val isNewMangaUrl = (baseMangaUrl.host == newUrl.host && pathLengthIs(newUrl, 2) && newUrl.pathSegments[0] == baseMangaUrl.pathSegments[0])
                    if (isNewMangaUrl) return newUrl.pathSegments[1]
                }
            }
        }

        return null
    }

    private fun pathLengthIs(url: HttpUrl, n: Int, strict: Boolean = false): Boolean {
        return url.pathSegments.size == n && url.pathSegments[n - 1].isNotEmpty() ||
            (!strict && url.pathSegments.size == n + 1 && url.pathSegments[n].isEmpty())
    }

    protected open fun parseGenres(document: Document): List<GenreData>? {
        return document.selectFirst("ul.genrez")?.select("li")?.map { li ->
            GenreData(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.attr("value"),
            )
        }
    }

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    // Unused
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        const val URL_SEARCH_PREFIX = "url:"

        // More info: https://issuetracker.google.com/issues/36970498
        private val MANGA_PAGE_ID_REGEX = "post_id\\s*:\\s*(\\d+)\\}".toRegex()
        private val CHAPTER_PAGE_ID_REGEX = "chapter_id\\s*=\\s*(\\d+);".toRegex()

        val JSON_IMAGE_LIST_REGEX = "\"images\"\\s*:\\s*(\\[.*?])".toRegex()
    }
}
