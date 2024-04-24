package eu.kanade.tachiyomi.multisrc.galleryadults

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.thumbnailToFull
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.toBinary
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.toDate
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat

abstract class GalleryAdults(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "all",
    protected open val mangaLang: String = LANGUAGE_MULTI,
    protected val simpleDateFormat: SimpleDateFormat? = null,
) : ConfigurableSource, ParsedHttpSource() {

    override val supportsLatest = false

    protected open val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val SharedPreferences.parseImagesMethod
        get() = getString(PREF_PARSE_IMAGES, PARSE_METHOD_DEFAULT_VALUE)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_PARSE_IMAGES
            title = "Method to parse for images' URL"
            entries = PARSE_METHODS
            entryValues = PARSE_METHOD_VALUES
            setDefaultValue(PARSE_METHOD_DEFAULT_VALUE)
            summary = "%s"
        }.also(screen::addPreference)

        addRandomUAPreferenceToScreen(screen)
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                userAgentType = preferences.getPrefUAType(),
                customUA = preferences.getPrefCustomUA(),
                filterInclude = listOf("chrome"),
            )
            .rateLimit(4)
            .build()
    }

    protected open val xhrHeaders = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    /* List detail */
    protected class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String?,
        val lang: String,
    )

    protected open fun Element.mangaTitle(selector: String = ".caption"): String? =
        selectFirst(selector)?.text()

    protected open fun Element.mangaUrl() =
        selectFirst(".inner_thumb a")?.attr("abs:href")

    protected open fun Element.mangaThumbnail() =
        selectFirst(".inner_thumb img")?.imgAttr()

    // Overwrite this to filter other languages' manga from search result.
    // Default to [mangaLang] won't filter anything
    protected open fun Element.mangaLang() = mangaLang

    protected open fun HttpUrl.Builder.addPageUri(page: Int): HttpUrl.Builder {
        val url = toString()
        if (!url.endsWith('/') && !url.contains('?')) {
            addPathSegment("") // trailing slash (/)
        }
        if (page > 1) addQueryParameter("page", page.toString())
        return this
    }

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (mangaLang.isNotBlank()) addPathSegments("language/$mangaLang")
            if (supportsLatest) addPathSegment("popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaSelector() = "div.thumb"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.mangaTitle()!!
            setUrlWithoutDomain(element.mangaUrl()!!)
            thumbnail_url = element.mangaThumbnail()
        }
    }

    override fun popularMangaNextPageSelector() = ".pagination li.active + li:not(.disabled)"

    /* Latest */
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (mangaLang.isNotBlank()) addPathSegments("language/$mangaLang")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, id) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    protected open val idPrefixUri = "g"

    protected open fun searchMangaByIdRequest(id: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(idPrefixUri)
            addPathSegments("$id/")
        }
        return GET(url.build(), headers)
    }

    protected open fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/$idPrefixUri/$id/"
        return MangasPage(listOf(details), false)
    }

    protected open val useIntermediateSearch: Boolean = false
    protected open val supportAdvanceSearch: Boolean = false
    protected open val supportSpeechless: Boolean = false
    private val useBasicSearch: Boolean
        get() = !useIntermediateSearch

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()

        // Speechless
        val speechlessFilter = filters.filterIsInstance<SpeechlessFilter>().firstOrNull()
        // Intermediate search
        val categoryFilters = filters.filterIsInstance<CategoryFilters>().firstOrNull()
        // Advance search
        val advancedSearchFilters = filters.filterIsInstance<Filter.Text>()

        return when {
            favoriteFilter?.state == true -> {
                val url = "$baseUrl/$favoritePath".toHttpUrl().newBuilder()
                return POST(
                    url.build().toString(),
                    xhrHeaders,
                    FormBody.Builder()
                        .add("page", page.toString())
                        .build(),
                )
            }
            supportSpeechless && speechlessFilter?.state == true -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("language")
                    addPathSegment(LANGUAGE_SPEECHLESS)
                    if (sortOrderFilter?.state == 0) addPathSegment("popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
            supportAdvanceSearch && advancedSearchFilters.any { it.state.isNotBlank() } -> {
                val url = "$baseUrl/advsearch".toHttpUrl().newBuilder().apply {
                    getSortOrderURIs().forEachIndexed { index, pair ->
                        addQueryParameter(pair.second, toBinary(sortOrderFilter?.state == index))
                    }
                    categoryFilters?.state?.forEach {
                        addQueryParameter(it.uri, toBinary(it.state))
                    }
                    getLanguageURIs().forEach { pair ->
                        addQueryParameter(
                            pair.second,
                            toBinary(
                                mangaLang == pair.first ||
                                    mangaLang == LANGUAGE_MULTI,
                            ),
                        )
                    }

                    // Build this query string: +tag:"bat+man"+-tag:"cat"+artist:"Joe"...
                    // +tag must be encoded into %2Btag while the rest are not needed to encode
                    val keys = emptyList<String>().toMutableList()
                    keys.addAll(selectedGenres.map { "%2Btag:\"${it.name}\"" })
                    advancedSearchFilters.forEach { filter ->
                        val key = when (filter) {
                            is TagsFilter -> "tag"
                            is ParodiesFilter -> "parody"
                            is ArtistsFilter -> "artist"
                            is CharactersFilter -> "character"
                            is GroupsFilter -> "group"
                            else -> null
                        }
                        if (key != null) {
                            keys.addAll(
                                filter.state.trim()
                                    // any space except after a comma (we're going to replace spaces only between words)
                                    .replace(Regex("""(?<!,)\s+"""), "+")
                                    .replace(" ", "")
                                    .split(',')
                                    .mapNotNull {
                                        val match = Regex("""^(-?)"?(.+)"?""").find(it)
                                        match?.groupValues?.let { groups ->
                                            "${if (groups[1].isNotBlank()) "-" else "%2B"}$key:\"${groups[2]}\""
                                        }
                                    },
                            )
                        }
                    }
                    addEncodedQueryParameter("key", keys.joinToString("+"))
                    addPageUri(page)
                }
                GET(url.build())
            }
            selectedGenres.size == 1 && query.isBlank() -> {
                // Browsing single tag's catalog
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("tag")
                    addPathSegment(selectedGenres.single().uri)
                    if (sortOrderFilter?.state == 0) addPathSegment("popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
            useIntermediateSearch -> {
                // Only for query string or multiple tags
                val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                    getSortOrderURIs().forEachIndexed { index, pair ->
                        addQueryParameter(pair.second, toBinary(sortOrderFilter?.state == index))
                    }
                    categoryFilters?.state?.forEach {
                        addQueryParameter(it.uri, toBinary(it.state))
                    }
                    getLanguageURIs().forEach { pair ->
                        addQueryParameter(
                            pair.second,
                            toBinary(mangaLang == pair.first || mangaLang == LANGUAGE_MULTI),
                        )
                    }
                    addEncodedQueryParameter("key", buildQueryString(selectedGenres.map { it.name }, query))
                    addPageUri(page)
                }
                GET(url.build())
            }
            useBasicSearch && (selectedGenres.size > 1 || query.isNotBlank()) -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegments("search/")
                    addEncodedQueryParameter("q", buildQueryString(selectedGenres.map { it.name }, query))
                    // Search results sorting is not supported by AsmHentai
                    if (sortOrderFilter?.state == 0) addQueryParameter("sort", "popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
            sortOrderFilter?.state == 1 -> latestUpdatesRequest(page)
            else -> popularMangaRequest(page)
        }
    }

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - uses plus(+) to search for exact match
     * - use comma(,) for separate terms, as AND condition.
     * Plus(+) after comma(,) doesn't have any effect.
     */
    protected open fun buildQueryString(tags: List<String>, query: String): String {
        return (tags + query).filterNot { it.isBlank() }.joinToString(",") {
            // any space except after a comma (we're going to replace spaces only between words)
            it.trim()
                .replace(Regex("""(?<!,)\s+"""), "+")
                .replace(" ", "")
        }
    }

    protected open val favoritePath = "includes/user_favs.php"

    protected open fun loginRequired(document: Document, url: String): Boolean {
        return (
            url.contains("/login/") &&
                document.select("input[value=Login]").isNotEmpty()
            )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (loginRequired(document, response.request.url.toString())) {
            throw Exception("Log in via WebView to view favorites")
        } else {
            val mangas = document.select(searchMangaSelector())
                .map {
                    SMangaDto(
                        title = it.mangaTitle()!!,
                        url = it.mangaUrl()!!,
                        thumbnail = it.mangaThumbnail(),
                        lang = it.mangaLang(),
                    )
                }
                .let { unfiltered ->
                    if (mangaLang.isNotBlank()) unfiltered.filter { it.lang == mangaLang } else unfiltered
                }
                .map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.thumbnail
                    }
                }

            return MangasPage(mangas, document.select(searchMangaNextPageSelector()).isNotEmpty())
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    /* Details */
    protected open fun Element.getCover() =
        selectFirst(".cover img")?.imgAttr()

    protected open fun Element.getInfo(tag: String): String {
        return select("ul.${tag.lowercase()} a")
            .joinToString { it.ownText() }
    }

    protected open fun Element.getDescription(): String = (
        listOf("Parodies", "Characters", "Languages", "Categories")
            .mapNotNull { tag ->
                getInfo(tag)
                    .let { if (it.isNotBlank()) "$tag: $it" else null }
            } +
            listOfNotNull(
                selectFirst(".pages:contains(Pages:)")?.ownText(),
            )
        )
        .joinToString("\n\n")

    protected open val mangaDetailInfoSelector = ".gallery_top"
    protected open val timeSelector = "time[datetime]"

    protected open fun Element.getTime(): Long {
        return selectFirst(timeSelector)
            ?.attr("datetime")
            .toDate(simpleDateFormat)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return document.selectFirst(mangaDetailInfoSelector)!!.run {
            SManga.create().apply {
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
                title = mangaTitle("h1")!!
                thumbnail_url = getCover()
                genre = getInfo("Tags")
                author = getInfo("Artists")
                description = getDescription()
            }
        }
    }

    /* Chapters */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = document.selectFirst(mangaDetailInfoSelector)
                    ?.getInfo("Groups")
                date_upload = document.getTime()
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    /* Pages */
    protected open fun Document.inputIdValueOf(string: String): String {
        return select("input[id=$string]").attr("value")
    }

    protected open val galleryIdSelector = "gallery_id"
    protected open val loadIdSelector = "load_id"
    protected open val loadDirSelector = "load_dir"
    protected open val totalPagesSelector = "load_pages"
    protected open val pageUri = "g"
    protected open val pageSelector = ".gallery_thumb"

    private val jsonFormat: Json by injectLazy()

    override fun pageListParse(document: Document): List<Page> {
        if (preferences.parseImagesMethod == "1") {
            val json = document.selectFirst("script:containsData(var g_th)")?.data()
                ?.substringAfter("$.parseJSON('")
                ?.substringBefore("');")?.trim()

            val loadDir = document.inputIdValueOf(loadDirSelector)
            val loadId = document.inputIdValueOf(loadIdSelector)
            val galleryId = document.inputIdValueOf(galleryIdSelector)
            val pageUrl = "$baseUrl/$pageUri/$galleryId"

            val randomServer = getServer(document, galleryId)
            val imagesUri = "https://$randomServer/$loadDir/$loadId"

            if (json != null) {
                val images = jsonFormat.parseToJsonElement(json).jsonObject
                val pages = mutableListOf<Page>()

                // JSON string in this form: {"1":"j,1100,1148","2":"j,728,689",...
                for (image in images) {
                    val ext = image.value.toString().replace("\"", "").split(",")[0]
                    val imageExt = when (ext) {
                        "p" -> "png"
                        "b" -> "bmp"
                        "g" -> "gif"
                        else -> "jpg"
                    }
                    val idx = image.key.toInt()
                    pages.add(
                        Page(
                            index = idx,
                            imageUrl = "$imagesUri/${image.key}.$imageExt",
                            url = "$pageUrl/$idx/",
                        ),
                    )
                }
                return pages
            } else {
                val images = document.select("$pageSelector img")
                val thumbUrls = images.map { it.imgAttr() }.toMutableList()

                // totalPages only exists if pages > 10 and have to make a request to get the other thumbnails
                val totalPages = document.inputIdValueOf(totalPagesSelector)

                if (totalPages.isNotBlank()) {
                    val imagesExt = images.first()?.imgAttr()!!
                        .substringAfterLast('.')

                    thumbUrls.addAll(
                        listOf((images.size + 1)..totalPages.toInt()).flatten().map {
                            "$imagesUri/${it}t.$imagesExt"
                        },
                    )
                }
                return thumbUrls.mapIndexed { idx, url ->
                    Page(
                        index = idx,
                        imageUrl = url.thumbnailToFull(),
                        url = "$pageUrl/$idx/",
                    )
                }
            }
        } else {
            return pageListRequest(document)
        }
    }

    protected open fun getServer(document: Document, galleryId: String): String {
        val cover = document.getCover()
        return cover!!.toHttpUrl().host
    }

    protected open val pagesRequest = "inc/thumbs_loader.php"

    /**
     * Method to request then parse for a list of manga's page's URL,
     * which will then request one by one to parse for page's image's URL.
     * This method will be used when user set in preference.
     */
    protected open fun pageListRequest(document: Document): List<Page> {
        // input only exists if pages > 10 and have to make a request to get the other thumbnails
        val totalPages = document.inputIdValueOf(totalPagesSelector)

        val isThumbnailsParsing = preferences.parseImagesMethod == "2"
        val galleryId = document.inputIdValueOf(galleryIdSelector)
        val pageUrl = "$baseUrl/$pageUri/$galleryId"

        val pageUrls = document.select("$pageSelector a")
            .map {
                if (isThumbnailsParsing) {
                    it.selectFirst("img")!!.imgAttr()
                } else {
                    it.absUrl("href")
                }
            }
            .toMutableList()

        if (totalPages.isNotBlank()) {
            val form = pageRequestForm(document, totalPages)

            client.newCall(POST("$baseUrl/$pagesRequest", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("a")
                .mapTo(pageUrls) {
                    if (isThumbnailsParsing) {
                        it.selectFirst("img")!!.imgAttr()
                    } else {
                        it.absUrl("href")
                    }
                }
        }
        return pageUrls.mapIndexed { idx, url ->
            if (isThumbnailsParsing) {
                Page(
                    index = idx,
                    imageUrl = url.thumbnailToFull(),
                    url = "$pageUrl/$idx/",
                )
            } else {
                Page(idx, url)
            }
        }
    }

    protected open fun pageRequestForm(document: Document, totalPages: String): FormBody =
        FormBody.Builder()
            .add("u_id", document.inputIdValueOf(galleryIdSelector))
            .add("g_id", document.inputIdValueOf(loadIdSelector))
            .add("img_dir", document.inputIdValueOf(loadDirSelector))
            .add("visible_pages", "10")
            .add("total_pages", totalPages)
            .add("type", "2") // 1 would be "more", 2 is "all remaining"
            .build()

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#gimg, img#fimg")?.imgAttr()!!
    }

    /* Filters */
    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }
    private var tagsFetchAttempt = 0
    private var genres = emptyList<Genre>()

    private fun tagsRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tags/popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    protected open fun tagsParser(document: Document): List<Pair<String, String>> {
        return document.select(".list_tags .tag_item")
            .mapNotNull {
                Pair(
                    it.selectFirst("h3.list_tag")?.ownText() ?: "",
                    it.select("a").attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    private fun getGenres() {
        if (genres.isEmpty() && tagsFetchAttempt < 3) {
            launchIO {
                val tags = mutableListOf<Pair<String, String>>()
                runBlocking {
                    val jobsPool = mutableListOf<Job>()
                    // Get first 3 pages
                    (1..3).forEach { page ->
                        jobsPool.add(
                            launchIO {
                                runCatching {
                                    tags.addAll(
                                        client.newCall(tagsRequest(page))
                                            .execute().asJsoup().let { tagsParser(it) },
                                    )
                                }
                            },
                        )
                    }
                    jobsPool.joinAll()
                    genres = tags.sortedWith(compareBy { it.first }).map { Genre(it.first, it.second) }
                }

                tagsFetchAttempt++
            }
        }
    }

    override fun getFilterList(): FilterList {
        getGenres()
        val filters = emptyList<Filter<*>>().toMutableList()
        if (useIntermediateSearch)
            filters.add(Filter.Header("HINT: Separate search term with comma (,)"))

        filters.add(SortOrderFilter(getSortOrderURIs()))

        if (genres.isEmpty()) {
            filters.add(Filter.Header("Press 'reset' to attempt to load tags"))
        } else {
            filters.add(GenresFilter(genres))
        }

        if (useIntermediateSearch) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    CategoryFilters(getCategoryURIs()),
                ),
            )
        }

        if (supportAdvanceSearch) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("Advanced filters will ignore query search. Separate terms by comma (,) and precede term with minus (-) to exclude."),
                    TagsFilter(),
                    ParodiesFilter(),
                    ArtistsFilter(),
                    CharactersFilter(),
                    GroupsFilter(),
                ),
            )
        }

        filters.add(Filter.Separator())

        if (supportSpeechless)
            filters.add(SpeechlessFilter())
        filters.add(FavoriteFilter())

        return FilterList(filters)
    }

    protected open fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
    ) + if (useIntermediateSearch || supportAdvanceSearch) {
        listOf(
            Pair("Downloads", "dl"),
            Pair("Top Rated", "tr"),
        )
    } else {
        emptyList()
    }

    protected open fun getCategoryURIs() = listOf(
        SearchFlagFilter("Manga", "m"),
        SearchFlagFilter("Doujinshi", "d"),
        SearchFlagFilter("Western", "w"),
        SearchFlagFilter("Image Set", "i"),
        SearchFlagFilter("Artist CG", "a"),
        SearchFlagFilter("Game CG", "g"),
    )

    protected open fun getLanguageURIs() = listOf(
        Pair(LANGUAGE_ENGLISH, "en"),
        Pair(LANGUAGE_JAPANESE, "jp"),
        Pair(LANGUAGE_SPANISH, "es"),
        Pair(LANGUAGE_FRENCH, "fr"),
        Pair(LANGUAGE_KOREAN, "kr"),
        Pair(LANGUAGE_GERMAN, "de"),
        Pair(LANGUAGE_RUSSIAN, "ru"),
    )

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private const val PREF_PARSE_IMAGES = "pref_parse_images_methods"
        private val PARSE_METHODS get() = arrayOf(
            "Fast generator",
            "Query for list of all pages (fast)",
            "Query each page one-by-one (slow - safe)",
        )
        private val PARSE_METHOD_VALUES get() = arrayOf(
            "1",
            "2",
            "3",
        )
        private val PARSE_METHOD_DEFAULT_VALUE get() = PARSE_METHOD_VALUES[0]

        // references to be used in factory
        const val LANGUAGE_MULTI = ""
        const val LANGUAGE_ENGLISH = "english"
        const val LANGUAGE_JAPANESE = "japanese"
        const val LANGUAGE_CHINESE = "chinese"
        const val LANGUAGE_KOREAN = "korean"
        const val LANGUAGE_SPANISH = "spanish"
        const val LANGUAGE_FRENCH = "french"
        const val LANGUAGE_GERMAN = "german"
        const val LANGUAGE_RUSSIAN = "russian"
        const val LANGUAGE_SPEECHLESS = "speechless"
        const val LANGUAGE_TRANSLATED = "translated"
    }
}
