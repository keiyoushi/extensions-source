package eu.kanade.tachiyomi.multisrc.madara

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class Madara(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    protected open val json: Json by injectLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    /**
     * If enabled, will attempt to remove non-manga items in popular and latest.
     * The filter will not be used in search as the theme doesn't set the CSS class.
     * Can be disabled if the source incorrectly sets the entry types.
     */
    protected open val filterNonMangaItems = true

    /**
     * The CSS selector used to filter manga items in popular and latest
     * if `filterNonMangaItems` is set to `true`. Can be override if needed.
     * If the flag is set to `false`, it will be empty by default.
     */
    protected open val mangaEntrySelector: String by lazy {
        if (filterNonMangaItems) ".manga" else ""
    }

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    private var genresList: List<Genre> = emptyList()

    /**
     * Inner variable to control the genre fetching failed state.
     */
    private var fetchGenresFailed: Boolean = false

    /**
     * Inner variable to control how much tries the genres request was called.
     */
    private var fetchGenresAttempts: Int = 0

    /**
     * Disable it if you don't want the genres to be fetched.
     */
    protected open val fetchGenres: Boolean = true

    /**
     * The path used in the URL for the manga pages. Can be
     * changed if needed as some sites modify it to other words.
     */
    protected open val mangaSubString = "manga"

    /**
     * enable if the site use "madara_load_more"
     * to load manga on the site
     * Typically has "load More" instead of next/previous page
     */
    protected open val useLoadMoreRequest = false

    /**
     * internal variable to save if site uses load_more or not
     */
    private var loadMoreRequestDetected = false

    // Popular Manga

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null

        if (!loadMoreRequestDetected && !useLoadMoreRequest) {
            loadMoreRequestDetected = document.selectFirst("nav.navigation-ajax") != null
        }

        return MangasPage(entries, hasNextPage)
    }

    // exclude/filter bilibili manga from list
    override fun popularMangaSelector() = "div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"

    open val popularMangaUrlSelector = "div.post-title a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun popularMangaRequest(page: Int): Request =
        if (useLoadMoreRequest || loadMoreRequestDetected) {
            loadMoreRequest(page - 1, "_wp_manga_views")
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=views", headers)
        }

    override fun popularMangaNextPageSelector(): String? =
        if (useLoadMoreRequest || loadMoreRequestDetected) {
            "body:not(:has(.no-posts))"
        } else {
            searchMangaNextPageSelector()
        }

    // Latest Updates

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {
        // Even if it's different from the popular manga's list, the relevant classes are the same
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        if (useLoadMoreRequest || loadMoreRequestDetected) {
            loadMoreRequest(page - 1, "_latest_update")
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=latest", headers)
        }

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mp = popularMangaParse(response)
        val mangas = mp.mangas.distinctBy { it.url }
        return MangasPage(mangas, mp.hasNextPage)
    }

    // load more
    protected fun loadMoreRequest(page: Int, metaKey: String): Request {
        val formBody = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", page.toString())
            add("template", "madara-core/content/content-archive")
            add("vars[paged]", "1")
            add("vars[orderby]", "meta_value_num")
            add("vars[template]", "archive")
            add("vars[sidebar]", "right")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", metaKey)
            add("vars[meta_query][0][paged]", "1")
            add("vars[meta_query][0][orderby]", "meta_value_num")
            add("vars[meta_query][0][template]", "archive")
            add("vars[meta_query][0][sidebar]", "right")
            add("vars[meta_query][0][post_type]", "wp-manga")
            add("vars[meta_query][0][post_status]", "publish")
            add("vars[meta_query][0][meta_key]", metaKey)
            add("vars[meta_query][relation]", "AND")
            add("vars[manga_archives_item_layout]", "default")
        }.build()

        val xhrHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }

    // Search Manga

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val mangaUrl = "$baseUrl/$mangaSubString/${query.substringAfter(URL_SEARCH_PREFIX)}"
            return client.newCall(GET(mangaUrl, headers))
                .asObservable().map { response ->
                    MangasPage(listOf(mangaDetailsParse(response.asJsoup()).apply { url = "/$mangaSubString/${query.substringAfter(URL_SEARCH_PREFIX)}/" }), false)
                }
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    // Error message for exceeding last page
                    if (response.code == 404) {
                        error("Already on the Last Page!")
                    } else {
                        throw Exception("HTTP error ${response.code}")
                    }
                }
            }
            .map { response ->
                searchMangaParse(response)
            }
    }

    protected open fun searchPage(page: Int): String {
        return if (page == 1) {
            ""
        } else {
            "page/$page/"
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder()
        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is ArtistFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("artist", filter.state)
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("release", filter.state)
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter("status[]", it.id)
                        }
                    }
                }
                is OrderByFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("m_orderby", filter.toUriPart())
                    }
                }
                is AdultContentFilter -> {
                    url.addQueryParameter("adult", filter.toUriPart())
                }
                is GenreConditionFilter -> {
                    url.addQueryParameter("op", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .let { list ->
                            if (list.isNotEmpty()) { list.forEach { genre -> url.addQueryParameter("genre[]", genre.id) } }
                        }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    protected open val statusFilterOptions: Map<String, String> =
        mapOf(
            intl["status_filter_completed"] to "end",
            intl["status_filter_ongoing"] to "on-going",
            intl["status_filter_canceled"] to "canceled",
            intl["status_filter_on_hold"] to "on-hold",
        )

    protected open val orderByFilterOptions: Map<String, String> = mapOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_views"] to "views",
        intl["order_by_filter_new"] to "new-manga",
    )

    protected open val genreConditionFilterOptions: Array<String> =
        arrayOf(
            intl["genre_condition_filter_or"],
            intl["genre_condition_filter_and"],
        )

    protected open val adultContentFilterOptions: Array<String> =
        arrayOf(
            intl["adult_content_filter_all"],
            intl["adult_content_filter_none"],
            intl["adult_content_filter_only"],
        )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    open class Tag(val id: String, name: String) : Filter.CheckBox(name)

    protected class AuthorFilter(title: String) : Filter.Text(title)
    protected class ArtistFilter(title: String) : Filter.Text(title)
    protected class YearFilter(title: String) : Filter.Text(title)
    protected class StatusFilter(title: String, status: List<Tag>) :
        Filter.Group<Tag>(title, status)

    protected class OrderByFilter(title: String, options: List<Pair<String, String>>, state: Int = 0) :
        UriPartFilter(title, options.toTypedArray(), state)

    protected class GenreConditionFilter(title: String, options: Array<String>) : UriPartFilter(
        title,
        options.zip(arrayOf("", "1")).toTypedArray(),
    )

    protected class AdultContentFilter(title: String, options: Array<String>) : UriPartFilter(
        title,
        options.zip(arrayOf("", "0", "1")).toTypedArray(),
    )

    protected class GenreList(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
    class Genre(name: String, val id: String = name) : Filter.CheckBox(name)

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = mutableListOf(
            AuthorFilter(intl["author_filter_title"]),
            ArtistFilter(intl["artist_filter_title"]),
            YearFilter(intl["year_filter_title"]),
            StatusFilter(
                title = intl["status_filter_title"],
                status = statusFilterOptions.map { Tag(it.key, it.value) },
            ),
            OrderByFilter(
                title = intl["order_by_filter_title"],
                options = orderByFilterOptions.map { Pair(it.key, it.value) },
                state = 0,
            ),
            AdultContentFilter(
                title = intl["adult_content_filter_title"],
                options = adultContentFilterOptions,
            ),
        )

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_filter_header"]),
                GenreConditionFilter(
                    title = intl["genre_condition_filter_title"],
                    options = genreConditionFilterOptions,
                ),
                GenreList(
                    title = intl["genre_filter_title"],
                    genres = genresList,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select("div.post-title a").first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }
            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun searchMangaNextPageSelector(): String? = "div.nav-previous, nav.navigation-ajax, a.nextpostslink"

    // Manga Details Parse

    protected val completedStatusList: Array<String> = arrayOf(
        "Completed",
        "Completo",
        "Completado",
        "Concluído",
        "Concluido",
        "Finalizado",
        "Achevé",
        "Terminé",
        "Hoàn Thành",
        "مكتملة",
        "مكتمل",
        "已完结",
        "Tamamlandı",
    )

    protected val ongoingStatusList: Array<String> = arrayOf(
        "OnGoing", "Продолжается", "Updating", "Em Lançamento", "Em lançamento", "Em andamento",
        "Em Andamento", "En cours", "En Cours", "En cours de publication", "Ativo", "Lançando", "Đang Tiến Hành", "Devam Ediyor",
        "Devam ediyor", "In Corso", "In Arrivo", "مستمرة", "مستمر", "En Curso", "En curso", "Emision",
        "Curso", "En marcha", "Publicandose", "En emision", "连载中", "Em Lançamento", "Devam Ediyo",
    )

    protected val hiatusStatusList: Array<String> = arrayOf(
        "On Hold",
        "Pausado",
        "En espera",
        "Durduruldu",
    )

    protected val canceledStatusList: Array<String> = arrayOf(
        "Canceled",
        "Cancelado",
        "İptal Edildi",
    )

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            select(mangaDetailsSelectorTitle).first()?.let {
                manga.title = it.ownText()
            }
            select(mangaDetailsSelectorAuthor).eachText().filter {
                it.notUpdating()
            }.joinToString().takeIf { it.isNotBlank() }?.let {
                manga.author = it
            }
            select(mangaDetailsSelectorArtist).eachText().filter {
                it.notUpdating()
            }.joinToString().takeIf { it.isNotBlank() }?.let {
                manga.artist = it
            }
            select(mangaDetailsSelectorDescription).let {
                if (it.select("p").text().isNotEmpty()) {
                    manga.description = it.select("p").joinToString(separator = "\n\n") { p ->
                        p.text().replace("<br>", "\n")
                    }
                } else {
                    manga.description = it.text()
                }
            }
            select(mangaDetailsSelectorThumbnail).first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
            select(mangaDetailsSelectorStatus).last()?.let {
                manga.status = with(it.text()) {
                    when {
                        containsIn(completedStatusList) -> SManga.COMPLETED
                        containsIn(ongoingStatusList) -> SManga.ONGOING
                        containsIn(hiatusStatusList) -> SManga.ON_HIATUS
                        containsIn(canceledStatusList) -> SManga.CANCELLED
                        else -> SManga.UNKNOWN
                    }
                }
            }
            val genres = select(mangaDetailsSelectorGenre)
                .map { element -> element.text().lowercase(Locale.ROOT) }
                .toMutableSet()

            // add tag(s) to genre
            val mangaTitle = try {
                manga.title
            } catch (_: UninitializedPropertyAccessException) {
                "not initialized"
            }

            if (mangaDetailsSelectorTag.isNotEmpty()) {
                select(mangaDetailsSelectorTag).forEach { element ->
                    if (genres.contains(element.text()).not() &&
                        element.text().length <= 25 &&
                        element.text().contains("read", true).not() &&
                        element.text().contains(name, true).not() &&
                        element.text().contains(name.replace(" ", ""), true).not() &&
                        element.text().contains(mangaTitle, true).not() &&
                        element.text().contains(altName, true).not()
                    ) {
                        genres.add(element.text().lowercase(Locale.ROOT))
                    }
                }
            }

            // add manga/manhwa/manhua thinggy to genre
            document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it.notUpdating() && it != "-" && genres.contains(it).not()) {
                    genres.add(it.lowercase(Locale.ROOT))
                }
            }

            manga.genre = genres.toList().joinToString(", ") { genre ->
                genre.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale.ROOT,
                        )
                    } else {
                        it.toString()
                    }
                }
            }

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    manga.description = when {
                        manga.description.isNullOrBlank() -> altName + it
                        else -> manga.description + "\n\n$altName" + it
                    }
                }
            }
        }

        return manga
    }

    // Manga Details Selector
    open val mangaDetailsSelectorTitle = "div.post-title h3, div.post-title h1"
    open val mangaDetailsSelectorAuthor = "div.author-content > a"
    open val mangaDetailsSelectorArtist = "div.artist-content > a"
    open val mangaDetailsSelectorStatus = "div.summary-content"
    open val mangaDetailsSelectorDescription = "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"
    open val mangaDetailsSelectorThumbnail = "div.summary_image img"
    open val mangaDetailsSelectorGenre = "div.genres-content a"
    open val mangaDetailsSelectorTag = "div.tags-content a"

    open val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    open val altNameSelector = ".post-content_item:contains(Alt) .summary-content"
    open val altName = intl["alt_names_heading"]
    open val updatingRegex = "Updating|Atualizando".toRegex(RegexOption.IGNORE_CASE)

    fun String.notUpdating(): Boolean {
        return this.contains(updatingRegex).not()
    }

    private fun String.containsIn(array: Array<String>): Boolean {
        return this.lowercase() in array.map { it.lowercase() }
    }

    protected open fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            else -> element.attr("abs:src")
        }
    }

    /**
     * Set it to true if the source uses the new AJAX endpoint to
     * fetch the manga chapters instead of the old admin-ajax.php one.
     */
    protected open val useNewChapterEndpoint: Boolean = false

    /**
     * Internal attribute to control if it should always use the
     * new chapter endpoint after a first check if useNewChapterEndpoint is
     * set to false. Using a separate variable to still allow the other
     * one to be overridable manually in each source.
     */
    private var oldChapterEndpointDisabled: Boolean = false

    protected open fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "manga_get_chapters")
            .add("manga", mangaId)
            .build()

        val xhrHeaders = headersBuilder()
            .add("Content-Length", form.contentLength().toString())
            .add("Content-Type", form.contentType().toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    protected open fun xhrChaptersRequest(mangaUrl: String): Request {
        val xhrHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$mangaUrl/ajax/chapters", xhrHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")
            val mangaId = chaptersWrapper.attr("data-id")

            var xhrRequest = if (useNewChapterEndpoint || oldChapterEndpointDisabled) {
                xhrChaptersRequest(mangaUrl)
            } else {
                oldXhrChaptersRequest(mangaId)
            }
            var xhrResponse = client.newCall(xhrRequest).execute()

            // Newer Madara versions throws HTTP 400 when using the old endpoint.
            if (!useNewChapterEndpoint && xhrResponse.code == 400) {
                xhrResponse.close()
                // Set it to true so following calls will be made directly to the new endpoint.
                oldChapterEndpointDisabled = true

                xhrRequest = xhrChaptersRequest(mangaUrl)
                xhrResponse = client.newCall(xhrRequest).execute()
            }

            chapterElements = xhrResponse.asJsoup().select(chapterListSelector())
            xhrResponse.close()
        }

        return chapterElements.map(::chapterFromElement)
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    protected open fun chapterDateSelector() = "span.chapter-release-date"

    open val chapterUrlSelector = "a"

    // can cause some issue for some site. blocked by cloudflare when opening the chapter pages
    open val chapterUrlSuffix = "?style=list"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.text()
            }
            // Dates can be part of a "new" graphic or plain text
            // Added "title" alternative
            chapter.date_upload = select("img:not(.thumb)").firstOrNull()?.attr("alt")?.let { parseRelativeDate(it) }
                ?: select("span a").firstOrNull()?.attr("title")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(select(chapterDateSelector()).firstOrNull()?.text())
        }

        return chapter
    }

    open fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            // Handle 'yesterday' and 'today', using midnight
            WordSet("yesterday", "يوم واحد").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1) // yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("today").startsWith(date) -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("يومين").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("ago", "atrás", "önce", "قبل").endsWith(date) -> {
                parseRelativeDate(date)
            }
            WordSet("hace").startsWith(date) -> {
                parseRelativeDate(date)
            }
            date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                date.split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { dateFormat.tryParse(it.joinToString(" ")) }
            }
            else -> dateFormat.tryParse(date)
        }
    }

    // Parses dates in this form:
    // 21 horas ago
    protected open fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام", "天").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة", "小时").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("detik", "segundo", "second", "วินาที").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("week", "semana").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("month", "mes").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("year", "año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    open val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

    open val chapterProtectorSelector = "#chapter-protector-data"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val chapterProtector = document.selectFirst(chapterProtectorSelector)
            ?: return document.select(pageListParseSelector).mapIndexed { index, element ->
                val imageUrl = element.selectFirst("img")?.let { imageFromElement(it) }
                Page(index, document.location(), imageUrl)
            }
        val chapterProtectorHtml = chapterProtector.html()
        val password = chapterProtectorHtml
            .substringAfter("wpmangaprotectornonce='")
            .substringBefore("';")
        val chapterData = json.parseToJsonElement(
            chapterProtectorHtml
                .substringAfter("chapter_data='")
                .substringBefore("';")
                .replace("\\/", "/"),
        ).jsonObject

        val unsaltedCiphertext = Base64.decode(chapterData["ct"]!!.jsonPrimitive.content, Base64.DEFAULT)
        val salt = chapterData["s"]!!.jsonPrimitive.content.decodeHex()
        val ciphertext = SALTED + salt + unsaltedCiphertext

        val rawImgArray = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), password)
        val imgArrayString = json.parseToJsonElement(rawImgArray).jsonPrimitive.content
        val imgArray = json.parseToJsonElement(imgArrayString).jsonArray

        return imgArray.mapIndexed { idx, it ->
            Page(idx, document.location(), it.jsonPrimitive.content)
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers.newBuilder().set("Referer", page.url).build())
    }

    override fun imageUrlParse(document: Document) = ""

    /**
     * Set it to false if you want to disable the extension reporting the view count
     * back to the source website through admin-ajax.php.
     */
    protected open val sendViewCount: Boolean = true

    protected open fun countViewsRequest(document: Document): Request? {
        val wpMangaData = document.select("script#wp-manga-js-extra").firstOrNull()
            ?.data() ?: return null

        val wpMangaInfo = wpMangaData
            .substringAfter("var manga = ")
            .substringBeforeLast(";")

        val wpManga = runCatching { json.parseToJsonElement(wpMangaInfo).jsonObject }
            .getOrNull() ?: return null

        if (wpManga["enable_manga_view"]?.jsonPrimitive?.content == "1") {
            val formBuilder = FormBody.Builder()
                .add("action", "manga_views")
                .add("manga", wpManga["manga_id"]!!.jsonPrimitive.content)

            if (wpManga["chapter_slug"] != null) {
                formBuilder.add("chapter", wpManga["chapter_slug"]!!.jsonPrimitive.content)
            }

            val formBody = formBuilder.build()

            val newHeaders = headersBuilder()
                .set("Content-Length", formBody.contentLength().toString())
                .set("Content-Type", formBody.contentType().toString())
                .set("Referer", document.location())
                .build()

            val ajaxUrl = wpManga["ajax_url"]!!.jsonPrimitive.content

            return POST(ajaxUrl, newHeaders, formBody)
        }

        return null
    }

    /**
     * Send the view count request to the Madara endpoint.
     *
     * @param document The response document with the wp-manga data
     */
    protected open fun countViews(document: Document) {
        if (!sendViewCount) {
            return
        }

        val request = countViewsRequest(document) ?: return
        runCatching { client.newCall(request).execute().close() }
    }

    /**
     * Fetch the genres from the source to be used in the filters.
     */
    protected fun fetchGenres() {
        if (fetchGenres && fetchGenresAttempts < 3 && (genresList.isEmpty() || fetchGenresFailed)) {
            try {
                genresList = client.newCall(genresRequest()).execute()
                    .use { parseGenres(it.asJsoup()) }
            } catch (_: Exception) {
                fetchGenresFailed = true
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    /**
     * The request to the search page (or another one) that have the genres list.
     */
    protected open fun genresRequest(): Request {
        return GET("$baseUrl/?s=genre&post_type=wp-manga", headers)
    }

    /**
     * Get the genres from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseGenres(document: Document): List<Genre> {
        return document.selectFirst("div.checkbox-group")
            ?.select("div.checkbox")
            .orEmpty()
            .map { li ->
                Genre(
                    li.selectFirst("label")!!.text(),
                    li.selectFirst("input[type=checkbox]")!!.`val`(),
                )
            }
    }

    // https://stackoverflow.com/a/66614516
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    protected fun launchIO(block: () -> Unit) =
        GlobalScope.launch(Dispatchers.IO) { block() }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
        val SALTED = "Salted__".toByteArray(Charsets.UTF_8)
    }
}

class WordSet(private vararg val words: String) {
    fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
    fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
}
