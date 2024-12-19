package eu.kanade.tachiyomi.multisrc.galleryadults

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import kotlinx.serialization.SerializationException
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

    override val client: OkHttpClient = network.cloudflareClient

    protected open val xhrHeaders = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    /* Preferences */
    protected val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    protected open val useShortTitlePreference = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHORT_TITLE
            title = "Display Short Titles"
            summaryOff = "Showing Long Titles"
            summaryOn = "Showing short Titles"
            setDefaultValue(false)
            setVisible(useShortTitlePreference)
        }.also(screen::addPreference)
    }

    protected val SharedPreferences.shortTitle
        get() = getBoolean(PREF_SHORT_TITLE, false)

    /* List detail */
    protected class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String?,
        val lang: String,
    )

    protected open fun Element.mangaTitle(selector: String = ".caption"): String? =
        mangaFullTitle(selector).let {
            if (preferences.shortTitle) it?.shortenTitle() else it
        }

    protected open fun Element.mangaFullTitle(selector: String) =
        selectFirst(selector)?.text()

    protected open fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    protected open val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

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
        addQueryParameter("page", page.toString())
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

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val randomEntryFilter = filters.filterIsInstance<RandomEntryFilter>().firstOrNull()

        return when {
            randomEntryFilter?.state == true -> {
                client.newCall(randomEntryRequest())
                    .asObservableSuccess()
                    .map { response -> randomEntryParse(response) }
            }
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
            else -> {
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response -> searchMangaParse(response) }
            }
        }
    }

    protected open fun randomEntryRequest(): Request = GET("$baseUrl/random/", headers)

    protected open fun randomEntryParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val url = response.request.url.toString()
        val id = url.removeSuffix("/").substringAfterLast('/')
        return MangasPage(
            listOf(
                SManga.create().apply {
                    title = document.mangaTitle("h1")!!
                    setUrlWithoutDomain("$baseUrl/$idPrefixUri/$id/")
                    thumbnail_url = document.getCover()
                },
            ),
            false,
        )
    }

    /**
     * Manga URL: $baseUrl/$idPrefixUri/<id>/
     */
    protected open val idPrefixUri = "gallery"

    protected open fun searchMangaByIdRequest(id: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(idPrefixUri)
            addPathSegments("$id/")
        }
        return GET(url.build(), headers)
    }

    protected open fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response.asJsoup())
        details.url = "/$idPrefixUri/$id/"
        return MangasPage(listOf(details), false)
    }

    protected open val useIntermediateSearch: Boolean = false
    protected open val supportAdvancedSearch: Boolean = false
    protected open val supportSpeechless: Boolean = false
    protected open val useBasicSearch: Boolean
        get() = !useIntermediateSearch

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()

        // Speechless
        val speechlessFilter = filters.filterIsInstance<SpeechlessFilter>().firstOrNull()

        // Advanced search
        val advancedSearchFilters = filters.filterIsInstance<AdvancedTextFilter>()

        return when {
            favoriteFilter?.state == true ->
                favoriteFilterSearchRequest(page, query, filters)
            supportSpeechless && speechlessFilter?.state == true ->
                speechlessFilterSearchRequest(page, query, filters)
            supportAdvancedSearch && advancedSearchFilters.any { it.state.isNotBlank() } ->
                advancedSearchRequest(page, query, filters)
            selectedGenres.size == 1 && query.isBlank() ->
                tagBrowsingSearchRequest(page, query, filters)
            useIntermediateSearch ->
                intermediateSearchRequest(page, query, filters)
            useBasicSearch && (selectedGenres.size > 1 || query.isNotBlank()) ->
                basicSearchRequest(page, query, filters)
            sortOrderFilter?.state == 1 ->
                latestUpdatesRequest(page)
            else ->
                popularMangaRequest(page)
        }
    }

    protected open val basicSearchKey = "q"

    /**
     * Basic Search: support query string with multiple-genres filter by adding genres to query string.
     */
    protected open fun basicSearchRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search/")
            addEncodedQueryParameter(basicSearchKey, buildQueryString(selectedGenres.map { it.name }, query))
            if (sortOrderFilter?.state == 0) addQueryParameter("sort", "popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    protected open val intermediateSearchKey = "key"

    /**
     * This supports filter query search with languages, categories (manga, doujinshi...)
     * with additional sort orders.
     */
    protected open fun intermediateSearchRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()

        // Intermediate search
        val categoryFilters = filters.filterIsInstance<CategoryFilters>().firstOrNull()

        // Only for query string or multiple tags
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
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
            addEncodedQueryParameter(intermediateSearchKey, buildQueryString(selectedGenres.map { it.name }, query))
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    protected open val advancedSearchKey = "key"
    protected open val advancedSearchUri = "advsearch"

    /**
     * Advanced Search normally won't support search for string but allow include/exclude specific
     * tags/artists/groups/parodies/characters
     */
    protected open fun advancedSearchRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()

        // Intermediate search
        val categoryFilters = filters.filterIsInstance<CategoryFilters>().firstOrNull()
        // Advanced search
        val advancedSearchFilters = filters.filterIsInstance<AdvancedTextFilter>()

        val url = "$baseUrl/$advancedSearchUri/".toHttpUrl().newBuilder().apply {
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
                            .replace(regexSpaceNotAfterComma, "+")
                            .replace(" ", "")
                            .split(',')
                            .mapNotNull {
                                val match = regexExcludeTerm.find(it)
                                match?.groupValues?.let { groups ->
                                    "${if (groups[1].isNotBlank()) "-" else "%2B"}$key:\"${groups[2]}\""
                                }
                            },
                    )
                }
            }
            addEncodedQueryParameter(advancedSearchKey, keys.joinToString("+"))
            addPageUri(page)
        }
        return GET(url.build(), headers)
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
                .replace(regexSpaceNotAfterComma, "+")
                .replace(" ", "")
        }
    }

    protected open fun tagBrowsingSearchRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()

        // Browsing single tag's catalog
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("tag")
            addPathSegment(selectedGenres.single().uri)
            if (sortOrderFilter?.state == 0) addPathSegment("popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    /**
     * Browsing speechless titles. Some sites exclude speechless titles from normal search and
     * allow browsing separately.
     */
    protected open fun speechlessFilterSearchRequest(page: Int, query: String, filters: FilterList): Request {
        // Basic search
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("language")
            addPathSegment(LANGUAGE_SPEECHLESS)
            if (sortOrderFilter?.state == 0) addPathSegment("popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    /**
     * Browsing user's personal favorites saved on site. This requires login in view WebView.
     */
    protected open fun favoriteFilterSearchRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$favoritePath".toHttpUrl().newBuilder()
        return POST(
            url.build().toString(),
            xhrHeaders,
            FormBody.Builder()
                .add("page", page.toString())
                .build(),
        )
    }

    protected open val favoritePath = "user/fav_pags.php"

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
            val hasNextPage = document.select(searchMangaNextPageSelector()).isNotEmpty()
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
                    val results = unfiltered.filter { mangaLang.isBlank() || it.lang == mangaLang }
                    // return at least 1 title if all mangas in current page is of other languages
                    if (results.isEmpty() && hasNextPage) listOf(unfiltered[0]) else results
                }
                .map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.thumbnail
                    }
                }

            return MangasPage(mangas, hasNextPage)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    /* Details */
    protected open val mangaDetailInfoSelector = ".gallery_top"

    override fun mangaDetailsParse(document: Document): SManga {
        return document.selectFirst(mangaDetailInfoSelector)!!.run {
            SManga.create().apply {
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
                title = mangaTitle("h1")!!
                thumbnail_url = getCover()
                genre = getInfo("Tags")
                author = getInfo("Artists")
                description = getDescription(document)
            }
        }
    }

    protected open fun Element.getCover() =
        selectFirst(".cover img")?.imgAttr()

    protected val regexTag = Regex("Tags?")

    /**
     * Parsing document to extract info related to [tag].
     */
    protected abstract fun Element.getInfo(tag: String): String

    protected open fun Element.getDescription(document: Document? = null): String = (
        listOf("Parodies", "Characters", "Languages", "Categories", "Category")
            .mapNotNull { tag ->
                getInfo(tag)
                    .takeIf { it.isNotBlank() }
                    ?.let { "$tag: $it" }
            } +
            listOfNotNull(
                getInfoPages(document),
                getInfoAlternativeTitle(),
                getInfoFullTitle(),
            )
        )
        .joinToString("\n\n")

    protected open fun Element.getInfoPages(document: Document? = null): String? =
        document?.inputIdValueOf(totalPagesSelector)
            ?.takeIf { it.isNotBlank() }
            ?.let { "Pages: $it" }

    protected open fun Element.getInfoAlternativeTitle(): String? =
        selectFirst("h1 + h2, .subtitle")?.ownText()
            .takeIf { !it.isNullOrBlank() }
            ?.let { "Alternative title: $it" }

    protected open fun Element.getInfoFullTitle(): String? =
        if (preferences.shortTitle) "Full title: ${mangaFullTitle("h1")}" else null

    protected open fun Element.getTime(): Long =
        selectFirst(".uploaded")
            ?.ownText()
            .toDate(simpleDateFormat)

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
    protected open fun Element.inputIdValueOf(string: String): String {
        return select("input[id=$string]").attr("value")
    }

    protected open val pagesRequest = "inc/thumbs_loader.php"
    protected open val galleryIdSelector = "gallery_id"
    protected open val loadIdSelector = "load_id"
    protected open val loadDirSelector = "load_dir"
    protected open val totalPagesSelector = "load_pages"
    protected open val serverSelector = "load_server"

    protected open fun pageRequestForm(document: Document, totalPages: String, loadedPages: Int): FormBody {
        val token = document.select("[name=csrf-token]").attr("content")
        val serverNumber = document.serverNumber()

        return FormBody.Builder()
            .add("u_id", document.inputIdValueOf(galleryIdSelector))
            .add("g_id", document.inputIdValueOf(loadIdSelector))
            .add("img_dir", document.inputIdValueOf(loadDirSelector))
            .add("visible_pages", loadedPages.toString())
            .add("total_pages", totalPages)
            .add("type", "2") // 1 would be "more", 2 is "all remaining"
            .apply {
                if (token.isNotBlank()) add("_token", token)
                if (serverNumber != null) add("server", serverNumber)
            }
            .build()
    }

    protected open val thumbnailSelector = ".gallery_thumb"

    private val jsonFormat: Json by injectLazy()

    protected open fun Element.getServer(): String {
        val domain = baseUrl.toHttpUrl().host
        return serverNumber()
            ?.let { "m$it.$domain" }
            ?: getCover()!!.toHttpUrl().host
    }

    protected open fun Element.serverNumber(): String? =
        inputIdValueOf(serverSelector)
            .takeIf { it.isNotBlank() }

    protected open fun Element.parseJson(): String? =
        selectFirst("script:containsData(parseJSON)")?.data()
            ?.substringAfter("$.parseJSON('")
            ?.substringBefore("');")?.trim()

    /**
     * Page URL: $baseUrl/$pageUri/<id>/<page>
     */
    protected open val pageUri = "g"

    override fun pageListParse(document: Document): List<Page> {
        val json = document.parseJson()

        if (json != null) {
            val loadDir = document.inputIdValueOf(loadDirSelector)
            val loadId = document.inputIdValueOf(loadIdSelector)
            val galleryId = document.inputIdValueOf(galleryIdSelector)
            val pageUrl = "$baseUrl/$pageUri/$galleryId"

            val server = document.getServer()
            val imagesUri = "https://$server/$loadDir/$loadId"

            try {
                val pages = mutableListOf<Page>()
                val images = jsonFormat.parseToJsonElement(json).jsonObject

                // JSON string in this form: {"1":"j,1100,1148","2":"j,728,689",...
                for (image in images) {
                    val ext = image.value.toString().replace("\"", "").split(",")[0]
                    val imageExt = when (ext) {
                        "p" -> "png"
                        "b" -> "bmp"
                        "g" -> "gif"
                        "w" -> "webp"
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
            } catch (e: SerializationException) {
                Log.e("GalleryAdults", "Failed to decode JSON")
                return this.pageListParseAlternative(document)
            }
        } else {
            return this.pageListParseAlternative(document)
        }
    }

    /**
     * Overwrite this to force extension not blindly converting thumbnails to full image
     * by simply removing the trailing "t" from file name. Instead, it will open each page,
     * one by one, then parsing for actual image's URL.
     * This will be much slower but guaranteed work.
     *
     * This only apply if site doesn't provide 'parseJSON'.
     */
    protected open val parsingImagePageByPage: Boolean = false

    /**
     * Either:
     *  - Load all thumbnails then convert thumbnails to full images.
     *  - Or request then parse for a list of manga's page's URL,
     *   which will then request one by one to parse for page's image's URL using [imageUrlParse].
     */
    protected open fun pageListParseAlternative(document: Document): List<Page> {
        val totalPages = document.inputIdValueOf(totalPagesSelector)
        val galleryId = document.inputIdValueOf(galleryIdSelector)
        val pageUrl = "$baseUrl/$pageUri/$galleryId"

        val pages = document.select("$thumbnailSelector a")
            .map {
                if (parsingImagePageByPage) {
                    it.absUrl("href")
                } else {
                    it.selectFirst("img")!!.imgAttr()
                }
            }
            .toMutableList()

        if (totalPages.isNotBlank() && totalPages.toInt() > pages.size) {
            val form = pageRequestForm(document, totalPages, pages.size)

            val morePages = client.newCall(POST("$baseUrl/$pagesRequest", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("a")
                .map {
                    if (parsingImagePageByPage) {
                        it.absUrl("href")
                    } else {
                        it.selectFirst("img")!!.imgAttr()
                    }
                }
            if (morePages.isNotEmpty()) {
                pages.addAll(morePages)
            } else {
                return pageListParseDummy(document)
            }
        }

        return pages.mapIndexed { idx, url ->
            if (parsingImagePageByPage) {
                Page(idx, url)
            } else {
                Page(
                    index = idx,
                    imageUrl = url.thumbnailToFull(),
                    url = "$pageUrl/$idx/",
                )
            }
        }
    }

    /**
     * Generate all images using `totalPages`. Supposedly they are sequential.
     * Use in case any extension doesn't know how to request for "All thumbnails"
     */
    protected open fun pageListParseDummy(document: Document): List<Page> {
        val loadDir = document.inputIdValueOf(loadDirSelector)
        val loadId = document.inputIdValueOf(loadIdSelector)
        val galleryId = document.inputIdValueOf(galleryIdSelector)
        val pageUrl = "$baseUrl/$pageUri/$galleryId"

        val server = document.getServer()
        val imagesUri = "https://$server/$loadDir/$loadId"

        val images = document.select("$thumbnailSelector img")
        val thumbUrls = images.map { it.imgAttr() }.toMutableList()

        val totalPages = document.inputIdValueOf(totalPagesSelector)

        if (totalPages.isNotBlank() && totalPages.toInt() > thumbUrls.size) {
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

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#gimg, img#fimg")?.imgAttr()!!
    }

    /* Filters */
    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }
    private var tagsFetched = false
    private var tagsFetchAttempt = 0

    /**
     * List of tags in <name, uri> pairs
     */
    protected var genres: MutableMap<String, String> = mutableMapOf()

    protected open fun tagsRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tags/popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    /**
     * Parsing [document] to return a list of tags in <name, uri> pairs.
     */
    protected open fun tagsParser(document: Document): List<Genre> {
        return document.select("a.tag_btn")
            .mapNotNull {
                Genre(
                    it.select(".list_tag, .tag_name").text(),
                    it.attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    protected open fun requestTags() {
        if (!tagsFetched && tagsFetchAttempt < 3) {
            launchIO {
                val tags = mutableListOf<Genre>()
                runBlocking {
                    val jobsPool = mutableListOf<Job>()
                    // Get first 5 pages
                    (1..5).forEach { page ->
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
                    tags.sortedWith(compareBy { it.name })
                        .forEach {
                            genres[it.name] = it.uri
                        }
                    tagsFetched = true
                }

                tagsFetchAttempt++
            }
        }
    }

    override fun getFilterList(): FilterList {
        requestTags()
        val filters = emptyList<Filter<*>>().toMutableList()
        if (useIntermediateSearch) {
            filters.add(Filter.Header("HINT: Separate search term with comma (,)"))
        }

        filters.add(SortOrderFilter(getSortOrderURIs()))

        if (genres.isEmpty()) {
            filters.add(Filter.Header("Press 'reset' to attempt to load tags"))
        } else {
            filters.add(GenresFilter(genres))
        }

        if (useIntermediateSearch || supportAdvancedSearch) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    CategoryFilters(getCategoryURIs()),
                ),
            )
        }

        if (supportAdvancedSearch) {
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

        if (supportSpeechless) {
            filters.add(SpeechlessFilter())
        }
        filters.add(FavoriteFilter())

        filters.add(RandomEntryFilter())

        return FilterList(filters)
    }

    protected open fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
    ) + if (useIntermediateSearch || supportAdvancedSearch) {
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

        private const val PREF_SHORT_TITLE = "pref_short_title"

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
