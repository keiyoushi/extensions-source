package eu.kanade.tachiyomi.extension.en.bookwalker

import android.app.Application
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.BookUpdateDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.HoldBookEntityDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.HoldBooksInfoDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SeriesDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SingleDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.Single
import uy.kohesive.injekt.injectLazy
import java.util.regex.PatternSyntaxException

class BookWalker : ConfigurableSource, ParsedHttpSource(), BookWalkerPreferences {

    override val name = "BookWalker Global"
    private val domain = "bookwalker.jp"

    override val baseUrl = "https://global.$domain"

    override val lang = "en"

    override val supportsLatest = true

    private val rimgUrl = "https://rimg.$domain"
    private val cUrl = "https://c.$domain"
    private val memberApiUrl = "https://member-app.$domain/api"

    override val client = network.client.newBuilder()
        .addInterceptor(BookWalkerImageRequestInterceptor(this))
        .build()

    // The UA should be a desktop UA because all research was done with a desktop UA, and the site
    // renders differently with a mobile user agent.
    // However, we're not overriding headersBuilder because we don't want to show the desktop site
    // when a user opens a WebView to e.g. log in or make a purchase.
    // We just need the desktop site when making requests from extension logic.
    val callHeaders = headers.newBuilder()
        .set("User-Agent", USER_AGENT_DESKTOP)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule += SerializersModule {
            polymorphic(HoldBookEntityDto::class) {
                subclass(SingleDto::class)
                subclass(SeriesDto::class)
            }
        }
    }

    val app by injectLazy<Application>()

    private val preferences by getPreferencesLazy()

    override val showLibraryInPopular
        get() = preferences.getBoolean(PREF_SHOW_LIBRARY_IN_POPULAR, false)

    override val shouldValidateLogin
        get() = preferences.getBoolean(PREF_VALIDATE_LOGGED_IN, true)

    override val imageQuality
        get() = ImageQualityPref.fromKey(
            preferences.getString(ImageQualityPref.PREF_KEY, ImageQualityPref.defaultOption.key)!!,
        )

    override val filterChapters
        get() = FilterChaptersPref.fromKey(
            preferences.getString(
                FilterChaptersPref.PREF_KEY,
                FilterChaptersPref.defaultOption.key,
            )!!,
        )

    override val attemptToReadPreviews
        get() = preferences.getBoolean(PREF_ATTEMPT_READ_PREVIEWS, false)

    override val excludeCategoryFilters
        get() = Regex(
            preferences.getString(
                PREF_CATEGORY_EXCLUDE_REGEX,
                categoryExcludeRegexDefault,
            )!!,
            RegexOption.IGNORE_CASE,
        )

    override val excludeGenreFilters
        get() = Regex(
            preferences.getString(PREF_GENRE_EXCLUDE_REGEX, genreExcludeRegexDefault)!!,
            RegexOption.IGNORE_CASE,
        )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        fun regularExpressionPref(block: EditTextPreference.() -> Unit): EditTextPreference {
            fun validateRegex(regex: String): String? {
                return try {
                    Regex(regex)
                    null
                } catch (e: PatternSyntaxException) {
                    e.message
                }
            }

            return EditTextPreference(screen.context).apply {
                dialogMessage = "Enter a regular expression. " +
                    "Sub-string matches will be counted as matches. Matches are case-insensitive."

                setOnBindEditTextListener { field ->
                    field.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable) {
                                validateRegex(s.toString())?.let { field.error = it }
                            }
                        },
                    )
                }

                setOnPreferenceChangeListener { _, new ->
                    validateRegex(new as String) == null
                }

                block()
            }
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_VALIDATE_LOGGED_IN
            title = "Validate Login"
            summary = "Validate that you are logged in before allowing certain actions. This is " +
                "recommended to avoid confusing behavior when your login session expires.\n" +
                "If you are using this extension as an anonymous user, disable this option."

            setDefaultValue(true)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = ImageQualityPref.PREF_KEY
            title = "Image Quality"
            summary = "%s"

            entries = arrayOf(
                "Automatic",
                "Medium",
                "High",
            )

            entryValues = arrayOf(
                ImageQualityPref.DEVICE.key,
                ImageQualityPref.MEDIUM.key,
                ImageQualityPref.HIGH.key,
            )

            setDefaultValue(ImageQualityPref.defaultOption.key)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LIBRARY_IN_POPULAR
            title = "Show My Library in Popular"
            summary = "Show your library instead of popular manga when browsing \"Popular\"."

            setDefaultValue(false)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = FilterChaptersPref.PREF_KEY
            title = "Filter Shown Chapters"
            summary = "Choose what types of chapters to show."

            entries = arrayOf(
                "Show owned and free chapters",
                "Show obtainable chapters",
                "Show all chapters",
            )

            entryValues = arrayOf(
                FilterChaptersPref.OWNED.key,
                FilterChaptersPref.OBTAINABLE.key,
                FilterChaptersPref.ALL.key,
            )

            setDefaultValue(FilterChaptersPref.defaultOption.key)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ATTEMPT_READ_PREVIEWS
            title = "Show Previews When Available"
            summary = "Determines whether attempting to read an un-owned chapter should show the " +
                "preview. Even when disabled, you will still be able to read free chapters you " +
                "have not \"purchased\"."

            setDefaultValue(true)
        }.also(screen::addPreference)

        regularExpressionPref {
            key = PREF_CATEGORY_EXCLUDE_REGEX
            title = "Exclude Category Filters"
            summary = "Hide certain categories from being listed in the search filters. " +
                "This will not hide manga with those categories from search results."

            setDefaultValue(categoryExcludeRegexDefault)
        }.also(screen::addPreference)

        regularExpressionPref {
            key = PREF_GENRE_EXCLUDE_REGEX
            title = "Exclude Genre Filters"
            summary = "Hide certain genres from being listed in the search filters. " +
                "This will not hide manga with those genres from search results."

            setDefaultValue(genreExcludeRegexDefault)
        }.also(screen::addPreference)
    }

    private val filterInfo by lazy { BookWalkerFilters(this) }

    override fun getFilterList(): FilterList {
        filterInfo.fetchIfNecessaryInBackground()

        fun Iterable<FilterInfo>.prependAll(): List<FilterInfo> {
            return mutableListOf(allFilter).apply { addAll(this@prependAll) }
        }

        return FilterList(
            SelectOneFilter(
                "Sort By",
                "order",
                listOf(
                    FilterInfo("Relevancy", "score"),
                    FilterInfo("Popularity", "rank"),
                    FilterInfo("Release Date", "release"),
                    FilterInfo("Title", "title"),
                ),
            ),
            SelectOneFilter(
                "Categories",
                QUERY_PARAM_CATEGORY,
                filterInfo.categories
                    ?.filterNot { excludeCategoryFilters.containsMatchIn(it.name) }
                    ?.prependAll() ?: fallbackFilters,
            ),
            SelectMultipleFilter(
                "Genre",
                QUERY_PARAM_GENRE,
                filterInfo.genres
                    ?.filterNot { excludeGenreFilters.containsMatchIn(it.name) }
                    ?: fallbackFilters,
            ),
            // Author filter disabled for now, since the performance/UX in-app is pretty bad
//            SelectMultipleFilter(
//                "Author",
//                QUERY_PARAM_AUTHOR,
//                filterInfo.authors ?: fallbackFilters,
//            ),
            SelectOneFilter(
                "Publisher",
                QUERY_PARAM_PUBLISHER,
                filterInfo.publishers?.prependAll() ?: fallbackFilters,
            ),
            OthersFilter(),
            PriceFilter(),
            ExcludeFilter(),
        )
    }

    override fun popularMangaRequest(page: Int): Request {
        filterInfo.fetchIfNecessaryInBackground()

        if (showLibraryInPopular) {
            return POST(
                "$baseUrl/prx/holdBooks-api/hold-book-list/",
                callHeaders,
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("holdBook-series", "1")
                    .build(),
            )
        }
        // /categories/2/ - manga
        // np=0 - display by series
        // order=rank - sort by popularity
        return GET("$baseUrl/categories/2/?order=rank&np=0&page=$page", callHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (showLibraryInPopular) {
            val manga = runBlocking(Dispatchers.IO) {
                response.parseAs<HoldBooksInfoDto>().holdBookList.entities
                    .mapNotNull { entity ->
                        when (entity) {
                            is SeriesDto -> {
                                SManga.create().apply {
                                    url = "/series/${entity.seriesId}/"
                                    title = entity.seriesName.cleanTitle()
                                    thumbnail_url = getHiResCoverFromLegacyUrl(entity.imageUrl)
                                }
                            }
                            is SingleDto -> {
                                val bookUpdate = fetchBookUpdate(entity.uuid)
                                bookUpdate?.let { bookUpdate ->
                                    SManga.create().apply {
                                        url = "/de${entity.uuid}/"
                                        title = bookUpdate.seriesName?.cleanTitle() ?: bookUpdate.productName.cleanTitle()
                                        thumbnail_url = bookUpdate.coverImageUrl
                                        author = bookUpdate.authors.joinToString { it.authorName }
                                    }
                                }
                            }
                        }
                    }
            }
            return MangasPage(manga, false)
        }
        return super.popularMangaParse(response)
    }

    override fun popularMangaNextPageSelector(): String =
        ".pager-area .next > a"

    override fun popularMangaSelector(): String =
        ".book-list-area .o-tile"

    override fun popularMangaFromElement(element: Element): SManga {
        val titleElt = element.select(".a-tile-ttl a")

        return SManga.create().apply {
            url = titleElt.attr("href").substring(baseUrl.length)
            title = titleElt.attr("title").cleanTitle()
            thumbnail_url = getHiResCoverFromLegacyUrl(
                element.selectFirst(".a-tile-thumb-img > img")?.attr("data-srcset")?.getHighestQualitySrcset(),
            )
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        filterInfo.fetchIfNecessaryInBackground()

        // qcat=2 - only show manga
        // np=0 - display by series
        return GET("$baseUrl/new/?order=release&qcat=2&np=0&page=$page", callHeaders)
    }

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        filterInfo.fetchIfNecessaryInBackground()

        val urlBuilder = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("np", "0") // display by series
            addQueryParameter("page", page.toString())
            addQueryParameter("word", query)

            filters.list
                .filterIsInstance<QueryParamFilter>()
                .flatMap { it.getQueryParams() }
                .forEach {
                    // special case since sorting by relevance doesn't work without search terms
                    if (query.isEmpty() && it.first == "order" && it.second == "score") {
                        addQueryParameter(it.first, "rank") // sort by popularity
                    } else {
                        addQueryParameter(it.first, it.second)
                    }
                }
        }

        return GET(urlBuilder.build(), callHeaders)
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // Manga with "/series/" in their URL are actual series.
        // Manga without are individual chapters/volumes (referred to here as "singles").
        return if (manga.url.startsWith("/series/")) {
            fetchSeriesMangaDetails(manga)
        } else {
            fetchSingleMangaDetails(manga)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga =
        throw UnsupportedOperationException()

    private fun fetchSeriesMangaDetails(manga: SManga): Observable<SManga> {
        return rxSingle {
            val seriesPage = client.newCall(
                GET("$baseUrl${manga.url}?order=release&np=1", callHeaders),
            ).awaitSuccess()
                .asJsoup()
                .let { validateLogin(it) }

            // It generally doesn't matter which chapter we take the description from, but for
            // series that release in volumes we want the earliest one, which will _usually_ be the
            // last one on the page. With that said, it's not worth it to paginate in order to find
            // the earliest volume, and volume releases don't usually have 60+ volumes anyways.
            val firstItem = seriesPage.selectFirst(".o-tile:not(:has(.a-ribbon-pre-order)) .a-tile-ttl a")
            val uuid = firstItem!!.absUrl("href").substringAfter("/de").substringBefore("/")
            val bookUpdate = fetchBookUpdate(uuid)

            SManga.create().apply {
                title = bookUpdate!!.seriesName?.cleanTitle() ?: bookUpdate.productName.cleanTitle()
                author = bookUpdate.authors.joinToString { it.authorName }
                description = listOfNotNull(bookUpdate.productExplanationShort, bookUpdate.productExplanationDetails)
                    .joinToString("\n\n")
                    .trim()
                thumbnail_url = bookUpdate.coverImageUrl
                genre = getAvailableFilterNames(seriesPage, "side-genre").joinToString()
                val statusIndicators = seriesPage.select("ul.side-others > li > a").map { it.ownText() }
                status = parseStatus(statusIndicators)
            }
        }.toObservable()
    }

    private fun fetchSingleMangaDetails(manga: SManga): Observable<SManga> {
        return rxSingle {
            val uuid = manga.url.substringAfter("/de").substringBefore("/")
            val bookUpdate = fetchBookUpdate(uuid)

            SManga.create().apply {
                title = bookUpdate!!.seriesName?.cleanTitle() ?: bookUpdate.productName.cleanTitle()
                author = bookUpdate.authors.joinToString { it.authorName }
                description = listOfNotNull(bookUpdate.productExplanationShort, bookUpdate.productExplanationDetails)
                    .joinToString("\n\n")
                    .trim()
                thumbnail_url = bookUpdate.coverImageUrl

                // From the browse pages we can't distinguish between a true one-shot and a
                // serial manga with only one chapter, but we can detect if there's a series
                // reference in the chapter page. If there is, we should let the user know that
                // they may need to take some action in the future to correct the error.
                val document = client.newCall(GET(baseUrl + manga.url, callHeaders)).awaitSuccess().asJsoup()
                if (document.selectFirst(".product-detail th:contains(Series Title)") != null) {
                    this.description = (
                        "WARNING: This entry is being treated as a one-shot but appears to " +
                            "have an associated series. If another chapter is released in " +
                            "the future, you will likely need to migrate this to itself." +
                            "\n\n$description"
                        ).trim()
                }
            }
        }.toObservable()
    }

    private fun parseStatus(statusIndicators: List<String>): Int {
        return if (statusIndicators.any { it.startsWith("Completed") }) {
            if (statusIndicators.any { it.startsWith("Pre-Order") }) {
                SManga.PUBLISHING_FINISHED
            } else {
                SManga.COMPLETED
            }
        } else {
            SManga.ONGOING
        }
    }

    private fun getTitleFromChapterPage(document: Document): String? {
        return document.selectFirst(".detail-book-title-box h1[itemprop='name']")?.ownText()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return rxSingle {
            if (!manga.url.startsWith("/series/")) {
                val document = client.newCall(GET(baseUrl + manga.url, callHeaders))
                    .awaitSuccess()
                    .asJsoup()

                return@rxSingle listOfNotNull(
                    chapterFromChapterPage(document)?.apply {
                        url = manga.url
                    },
                )
            }

            suspend fun getDocumentForPage(page: Int): Document {
                return client.newCall(
                    GET("$baseUrl${manga.url}?order=release&page=$page", callHeaders),
                ).awaitSuccess().asJsoup()
            }

            val firstPage = validateLogin(getDocumentForPage(1))
            val publishers = getAvailableFilterNames(firstPage, "side-publisher")
                .joinToString()

            val pageCount = firstPage.selectFirst(".pager-area li:has(+ .next) > a")
                ?.ownText()?.toIntOrNull()
                ?: 1

            val laterPages = (2..pageCount).map { n -> async { getDocumentForPage(n) } }
                .awaitAll()

            (listOf(firstPage) + laterPages).flatMap { document ->
                document.select(chapterListSelector()).map {
                    chapterFromElement(it).apply {
                        scanlator = publishers
                    }
                }
            }
        }.toObservable()
    }

    override fun chapterListSelector(): String {
        return when (filterChapters) {
            FilterChaptersPref.OWNED ->
                ".book-list-area .o-tile:has(.a-read-btn-s, .a-free-btn-s)"
            FilterChaptersPref.OBTAINABLE ->
                ".book-list-area .o-tile:not(:has(.a-ribbon-bundle, .a-ribbon-pre-order))"
            else -> // preorders shown, still not showing bundles since those aren't chapters
                ".book-list-area .o-tile:not(:has(.a-ribbon-bundle))"
        }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val statusSuffix =
            if (element.selectFirst("[data-action-label='Read']") != null) {
                "" // user is currently able to read the chapter
            } else if (element.selectFirst(".a-label-free") != null) {
                " $FREE_ICON" // it's free to read but the user technically doesn't "own" it
            } else if (element.selectFirst(".a-cart-btn-s, .a-cart-btn-s--on") != null) {
                " $PURCHASE_ICON"
            } else if (element.selectFirst(".a-ribbon-pre-order") != null) {
                " $PREORDER_ICON"
            } else {
                // Some content does not fall into any of the above bins. It seems to mostly be
                // bonus content you get from purchasing other items, but there may be exceptions.
                " $UNKNOWN_ICON"
            }

        val titleElt = element.select(".a-tile-ttl a")
        val title = titleElt.attr("title").cleanTitle()
        val chapterNumber = title.parseChapterNumber()

        url = titleElt.attr("href").substring(baseUrl.length)
        name = WORD_JOINER + (chapterNumber?.first ?: title) + statusSuffix
        chapter_number = chapterNumber?.second ?: -1f
        // scanlator set by caller
    }

    private fun chapterFromChapterPage(document: Document): SChapter? = SChapter.create().apply {
        // See chapterFromElement for info on these statuses
        val statusSuffix =
            if (document.selectFirst(".a-read-on-btn") != null) {
                ""
            } else if (document.selectFirst(".a-cart-btn:contains(Free)") != null) {
                " $FREE_ICON"
            } else if (document.selectFirst(".a-cart-btn:contains(Cart)") != null) {
                if (!filterChapters.includes(FilterChaptersPref.OBTAINABLE)) {
                    return null
                }
                " $PURCHASE_ICON"
            } else if (document.selectFirst(".a-order-btn") != null) {
                if (!filterChapters.includes(FilterChaptersPref.ALL)) {
                    return null
                }
                " $PREORDER_ICON"
            } else {
                if (!filterChapters.includes(FilterChaptersPref.ALL)) {
                    return null
                }
                " $UNKNOWN_ICON"
            }

        val title = getTitleFromChapterPage(document).orEmpty().cleanTitle()

        val chapterNumber = title.parseChapterNumber()

        // No need to set URL, that will be handled by the caller.
        name = WORD_JOINER + (chapterNumber?.first ?: title) + statusSuffix
        scanlator = document.select(".product-detail tr:has(th:contains(Publisher)) > td").text()
        chapter_number = chapterNumber?.second ?: -1f
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return rxSingle {
            val document = client.newCall(GET(baseUrl + chapter.url, callHeaders))
                .awaitSuccess()
                .asJsoup()
                .let { validateLogin(it) }

            val pagesText = document
                .select(".product-detail tr:has(th:contains(Page count)) > td").text()

            val pagesCount = Regex("\\d+").find(pagesText)?.value?.toIntOrNull()
                ?: throw Error("Could not determine number of pages in chapter")

            if (pagesCount == 0) {
                throw Error("The page count is 0. If this chapter was just released, wait a bit for the page count to update.")
            }

            val isFreeChapter = document.selectFirst(".a-cart-btn:contains(Free)") != null
            val readerUrl = document.selectFirst("a.a-read-on-btn")?.attr("href")
                ?: if (attemptToReadPreviews || isFreeChapter) {
                    document.selectFirst(".free-preview > a")?.attr("href")
                        ?: throw Error("No preview available")
                } else {
                    throw Error("You don't own this chapter, or you aren't logged in")
                }

            // We need to use the full cooperative URL every time we try to load a chapter since
            // the reader page relies on transient cookies set in the cooperative flow.
            // This call is simply being used to ensure the user is logged in.
            // Note that this is not fool-proof since the app may cache the page list, so sometimes
            // the best we can do is detect that the user is not logged in when loading the page
            // and fail to load the image at that point.
            tryCooperativeRedirect(readerUrl, "You must log in again. Open in WebView and click the shopping cart.")

            IntRange(0, pagesCount - 1).map {
                // The page index query parameter exists only to prevent the app from trying to
                // be smart about caching by page URLs, since the URL is the same for all the pages.
                // It doesn't do anything, and in fact gets stripped back out in imageRequest.
                Page(
                    it,
                    imageUrl = readerUrl.toHttpUrl().newBuilder()
                        .setQueryParameter(PAGE_INDEX_QUERY_PARAM, it.toString())
                        .build()
                        .toString(),
                )
            }
        }.toObservable()
    }

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        // This URL doesn't actually contain the image. It will be intercepted, and the actual image
        // will be extracted from a webview of the URL being sent here.
        val imageUrl = page.imageUrl!!.toHttpUrl()
        return GET(
            imageUrl.newBuilder()
                .removeAllQueryParameters(PAGE_INDEX_QUERY_PARAM)
                .build()
                .toString(),
            callHeaders.newBuilder()
                .set(HEADER_IS_REQUEST_FROM_EXTENSION, "true")
                .set(HEADER_PAGE_INDEX, imageUrl.queryParameter(PAGE_INDEX_QUERY_PARAM)!!)
                .build(),
        )
    }

    private suspend fun validateLogin(document: Document): Document {
        if (!shouldValidateLogin) {
            return document
        }
        val signInBtn = document.selectFirst(".logout-nav-area .btn-sign-in a")
        if (signInBtn != null) {
            // Sometimes just clicking on the button will sign the user in without needing to input
            // credentials, so we'll try to log in automatically.
            val signInUrl = signInBtn.attr("href")
            val redirectedPage = tryCooperativeRedirect(signInUrl)
            return client.newCall(GET(redirectedPage, callHeaders)).awaitSuccess().asJsoup()
        }
        return document
    }

    private suspend fun tryCooperativeRedirect(url: String, message: String = "Logged out, check website in WebView"): HttpUrl {
        return client.newCall(GET(url, callHeaders)).await().use {
            val redirectUrl = it.request.url

            if (redirectUrl.host == "member.bookwalker.jp" && redirectUrl.pathSegments.contains("login")) {
                throw Exception(message)
            }

            Log.d("bookwalker", "Successfully redirected to $redirectUrl")
            redirectUrl
        }
    }

    private suspend fun Call.awaitSuccess(): Response {
        return await().also {
            if (!it.isSuccessful) {
                it.close()
                throw Exception("HTTP Error ${it.code}")
            }
        }
    }

    private fun <T> rxSingle(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: suspend CoroutineScope.() -> T): Single<T> {
        return Single.create { sub ->
            CoroutineScope(dispatcher).launch {
                try {
                    sub.onSuccess(block())
                } catch (e: Throwable) {
                    sub.onError(e)
                }
            }
        }
    }

    private fun getAvailableFilterNames(doc: Document, filterClassName: String): List<String> {
        return doc.select("ul.$filterClassName > li > a > span").map { it.ownText() }
    }

    private fun String.cleanTitle(): String {
        return replace(CLEAN_TITLE_PATTERN, "").trim()
    }

    private fun String.getHighestQualitySrcset(): String? {
        val srcsetPairs = split(',').map {
            val parts = it.trim().split(' ')
            Pair(parts[1].trimEnd('x').toIntOrNull(), parts[0])
        }
        return srcsetPairs.maxByOrNull { it.first ?: 0 }?.second
    }

    // This function works if the cover is from before mid-dec'23 (non-hexadecimal).
    // If it's a newer cover, it will fall back to the low-res version.
    private fun getHiResCoverFromLegacyUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return url
        return try {
            val extension = url.substringAfterLast(".")
            val numericId = when {
                url.startsWith(rimgUrl) -> {
                    val id = url.substringAfter("$rimgUrl/").substringBefore('/')
                    id.reversed().toLongOrNull()
                }
                // For legacy covers of "series" from the user's library.
                url.contains("thumbnailImage_") -> {
                    val id = url.substringAfter("thumbnailImage_").substringBefore(".$extension")
                    id.toLongOrNull()
                }
                else -> null
            }
            numericId?.let { "$cUrl/coverImage_${it - 1}.$extension" } ?: url
        } catch (e: Exception) {
            url
        }
    }

    // Fetch manga details form api.
    private suspend fun fetchBookUpdate(uuid: String): BookUpdateDto? {
        val apiUrl = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
            .addQueryParameter("fileType", "EPUB")
            .addQueryParameter(uuid, "0")
            .build()

        return client.newCall(GET(apiUrl, headers)).awaitSuccess()
            .parseAs<List<BookUpdateDto>>().firstOrNull()
    }

    private fun String.parseChapterNumber(): Pair<String, Float>? {
        for (pattern in CHAPTER_NUMBER_PATTERNS) {
            val match = pattern.find(this)
            if (match != null) {
                return Pair(
                    match.groups[0]!!.value.replaceFirstChar(Char::titlecase),
                    match.groups[1]!!.value.toFloat(),
                )
            }
        }
        // Cannot parse chapter number
        return null
    }

    companion object {

        private val allFilter = FilterInfo("All", "")
        private val fallbackFilters = listOf(FilterInfo("Press reset to load filters", ""))

        private val categoryExcludeRegexDefault = arrayOf(
            "audiobooks",
            "bookshelf skin",
            "int'l manga", // already present in genres
        ).joinToString("|")

        private val genreExcludeRegexDefault = arrayOf(
            "coupon",
            "bundle set",
            "bonus items",
            "completed series", // already present in others
            "\\d{4}", // the genre list is bloated with things like "fall anime 2019"
            "kc simulpub", // this is a specific publisher; "Simulpub Release" is separate
            "kodansha promotion",
            "shonen jump",
            "media do",
            "youtuber recommendations",
        ).joinToString("|")

        private val CLEAN_TITLE_PATTERN = Regex(
            listOf(
                "(manga)",
                "(comic)",
                "<serial>",
                "(serial)",
                "<chapter release>",
                // Deliberately not stripping tags like "(Light Novels)"
            ).joinToString("|", transform = Regex::escape),
            RegexOption.IGNORE_CASE,
        )

        private val CHAPTER_NUMBER_PATTERNS = listOf(
            // All must have exactly one capture group for the chapter number
            Regex("""vol\.?\s*([0-9.]+)""", RegexOption.IGNORE_CASE),
            Regex("""volume\s+([0-9.]+)""", RegexOption.IGNORE_CASE),
            Regex("""chapter\s+([0-9.]+)""", RegexOption.IGNORE_CASE),
            Regex("""#([0-9.]+)""", RegexOption.IGNORE_CASE),
        )

        // Word joiners (zero-width non-breaking spaces) are used to avoid series titles from
        // getting automatically stripped out from the start of chapter names.
        private const val WORD_JOINER = "\u2060"
        private const val PURCHASE_ICON = "\uD83D\uDCB5" // dollar bill emoji
        private const val PREORDER_ICON = "\uD83D\uDD51" // two-o-clock emoji
        private const val FREE_ICON = "\uD83C\uDF81" // wrapped present emoji
        private const val UNKNOWN_ICON = "\u2753" // question mark emoji

        private const val PAGE_INDEX_QUERY_PARAM = "nocache_pagenum"
    }
}
