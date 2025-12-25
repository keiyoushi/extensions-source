package eu.kanade.tachiyomi.extension.all.batotov4

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
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
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.random.Random
import kotlin.text.Regex

open class BatoToV4(
    final override val lang: String,
    private val siteLang: String,
) : ConfigurableSource, ParsedHttpSource() {

    private val preferences by getPreferencesLazy { migrateMirrorPref() }

    override val name: String = "Bato.to V4"

    override val baseUrl: String get() = getMirrorPref()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = "${MIRROR_PREF_KEY}_$lang"
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
        }
        val removeOfficialPref = CheckBoxPreference(screen.context).apply {
            key = "${REMOVE_TITLE_VERSION_PREF}_$lang"
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }
        val removeCustomPref = EditTextPreference(screen.context).apply {
            key = "${REMOVE_TITLE_CUSTOM_PREF}_$lang"
            title = "Custom regex to be removed from title"
            summary = customRemoveTitle()
            setDefaultValue("")

            val validate = { str: String ->
                runCatching { Regex(str) }
                    .map { true to "" }
                    .getOrElse { false to it.message }
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
                            editText.error = if (!valid.first) valid.second else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val (isValid, message) = validate(newValue as String)
                if (isValid) {
                    summary = newValue
                } else {
                    Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                }
                isValid
            }
        }
        val userIdPref = EditTextPreference(screen.context).apply {
            key = "${USER_ID_PREF}_$lang"
            title = "User ID (Default Auto-Detect)"
            summary = if (getUserIdPref().isNotEmpty()) {
                "Manually Provided ID: ${getUserIdPref()}"
            } else {
                "Auto-detect from logged-in user"
            }
            setDefaultValue("")

            setOnPreferenceChangeListener { _, newValue ->
                val newId = newValue as String
                summary = if (newId.isNotEmpty()) {
                    // Validate it's numeric
                    if (newId.matches(Regex("\\d+"))) {
                        "Manually Provided ID: $newId"
                    } else {
                        Toast.makeText(screen.context, "User ID must be numeric", Toast.LENGTH_SHORT).show()
                        return@setOnPreferenceChangeListener false
                    }
                } else {
                    "Auto-detect from logged-in user"
                }
                true
            }
        }
        screen.addPreference(mirrorPref)
        screen.addPreference(removeOfficialPref)
        screen.addPreference(removeCustomPref)
        screen.addPreference(userIdPref)
    }

    private fun getMirrorPref(): String {
        if (System.getenv("CI") == "true") {
            return (MIRROR_PREF_ENTRY_VALUES.drop(1) + DEPRECATED_MIRRORS).joinToString("#, ")
        }

        return preferences.getString("${MIRROR_PREF_KEY}_$lang", MIRROR_PREF_DEFAULT_VALUE)
            ?.takeUnless { it == MIRROR_PREF_DEFAULT_VALUE }
            ?: let {
                /* Semi-sticky mirror:
                 * - Don't randomize on boot
                 * - Don't randomize per language
                 * - Fallback for non-Android platform
                 */
                val seed = runCatching {
                    val pm = Injekt.get<Application>().packageManager
                    pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0).lastUpdateTime
                }.getOrElse {
                    BuildConfig.VERSION_NAME.hashCode().toLong()
                }

                MIRROR_PREF_ENTRY_VALUES.drop(1).random(Random(seed))
            }
    }

    private fun isRemoveTitleVersion(): Boolean {
        return preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)
    }
    private fun customRemoveTitle(): String =
        preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!

    private fun getUserIdPref(): String =
        preferences.getString("${USER_ID_PREF}_$lang", "")!!

    private fun SharedPreferences.migrateMirrorPref() {
        val selectedMirror = getString("${MIRROR_PREF_KEY}_$lang", MIRROR_PREF_DEFAULT_VALUE)!!

        if (selectedMirror in DEPRECATED_MIRRORS) {
            edit().putString("${MIRROR_PREF_KEY}_$lang", MIRROR_PREF_DEFAULT_VALUE).apply()
        }
    }

    override val supportsLatest = true
    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder().apply {
        addInterceptor(::imageFallbackInterceptor)
    }.build()

    // ************ Search ************ //
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", SortFilter.LATEST)
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", SortFilter.POPULAR)
    }

    // searchMangaRequest is not used, see fetchSearchManga instead
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith("ID:") -> {
                val apiVariables = ApiComicNodeVariables(
                    id = query.substringAfter("ID:"),
                )
                val graphQLQuery = COMIC_NODE_QUERY

                client.newCall(sendGraphQLRequest(apiVariables, graphQLQuery)).asObservableSuccess()
                    .map { response ->
                        MangasPage(listOf(mangaDetailsParse(response)), false)
                    }
            }
            else -> {
                // Build filter parameters
                var sortby: String? = null
                var letterMode = false
                var incGenres = emptyList<String>()
                var excGenres = emptyList<String>()
                var incOLangs = emptyList<String>()
                var incTLangs = if (siteLang.isEmpty()) emptyList() else listOf(siteLang) // default to site lang
                var origStatus = ""
                var uploadStatus = ""
                var chapCount = ""

                filters.forEach { filter ->
                    when (filter) {
                        // Special Filters (Require auth)
                        is UtilsFilter -> {
                            if (filter.state != 0) {
                                return userComicListSearch(page, filter.selected)
                            }
                        }
                        is PersonalListFilter -> {
                            if (filter.state != 0) {
                                return when (filter.selected) {
                                    "updates" -> {
                                        val apiVariables = ApiMyUpdatesVariables(
                                            page = page,
                                            size = BROWSE_PAGE_SIZE,
                                        )
                                        client.newCall(sendGraphQLRequest(apiVariables, SSER_MY_UPDATES_QUERY))
                                            .asObservableSuccess()
                                            .map { response ->
                                                myUpdatesParse(response)
                                            }
                                    }
                                    "history" -> {
                                        client.newCall(sendMyHistoryRequest(page))
                                            .asObservableSuccess()
                                            .map { response ->
                                                myHistoryParse(response)
                                            }
                                    }
                                    else -> Observable.empty() // Should never happen
                                }
                            }
                        }

                        // Normal Filters
                        is LetterFilter -> {
                            letterMode = (filter.state == 1)
                        }
                        is GenreGroupFilter -> {
                            incGenres = filter.included
                            excGenres = filter.excluded
                        }
                        is OriginalLanguageFilter -> {
                            incOLangs = filter.selected
                        }
                        is TranslationLanguageFilter -> {
                            if (filter.selected.isNotEmpty()) { // Override site lang if present
                                incTLangs = filter.selected
                            }
                        }
                        is OriginalStatusFilter -> {
                            origStatus = filter.selected
                        }
                        is UploadStatusFilter -> {
                            uploadStatus = filter.selected
                        }
                        is SortFilter -> {
                            if (filter.state != null) {
                                sortby = filter.selected
                            }
                        }
                        is ChapterCountFilter -> {
                            chapCount = filter.selected
                        }
                        else -> { /* Do Nothing */ }
                    }
                }

                val apiVariables = ApiComicSearchVariables(
                    pageNumber = page,
                    size = BROWSE_PAGE_SIZE,
                    sortby = sortby,
                    query = query,
                    where = if (letterMode) "letter" else "browse",
                    incGenres = incGenres,
                    excGenres = excGenres,
                    incOLangs = incOLangs,
                    incTLangs = incTLangs,
                    origStatus = origStatus,
                    siteStatus = uploadStatus,
                    chapCount = chapCount,
                )
                val graphQLQuery = COMIC_SEARCH_QUERY

                client.newCall(sendGraphQLRequest(apiVariables, graphQLQuery)).asObservableSuccess()
                    .map { response ->
                        searchMangaParse(response)
                    }
            }
        }
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val browseResponse = response.parseAs<ApiComicSearchResponse>().data.response

        val mangas = browseResponse.items.map { item ->
            item.data.toSManga(baseUrl).apply {
                title = title.cleanTitleIfNeeded()
            }
        }
        val hasNextPage = browseResponse.paging.next != 0

        return MangasPage(mangas, hasNextPage)
    }

    // ************ Legacy v2 code ************ //
    private fun queryUtilsParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("tbody > tr")
            .map { element -> searchUtilsFromElement(element) }
        return MangasPage(mangas, false)
    }

    private fun queryHistoryParse(response: Response): MangasPage {
        val json = json.decodeFromString<JsonObject>(response.body.string())
        val html = json.jsonObject["html"]!!.jsonPrimitive.content

        val document = Jsoup.parse(html, response.request.url.toString())
        val mangas = document.select(".my-history-item")
            .map { element -> searchHistoryFromElement(element) }
        return MangasPage(mangas, false)
    }

    private fun searchUtilsFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(stripSeriesUrl(element.select("td a").attr("href")))
        manga.title = element.select("td a").text()
            .cleanTitleIfNeeded()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    private fun searchHistoryFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(stripSeriesUrl(element.select(".position-relative a").attr("href")))
        manga.title = element.select(".position-relative a").text()
            .cleanTitleIfNeeded()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    open fun formBuilder() = FormBody.Builder().apply {
        add("_where", "browse")
        add("first", "0")
        add("limit", "0")
        add("prevPos", "null")
    }

    // ************ Personal: My Updates ************ //
    private fun myUpdatesParse(response: Response): MangasPage {
        val myUpdatesResponse = response.parseAs<ApiMyUpdatesResponse>().data.response

        val mangas = myUpdatesResponse.items.map { item ->
            item.data.toSManga(baseUrl).apply {
                title = title.cleanTitleIfNeeded()
            }
        }
        val hasNextPage = myUpdatesResponse.paging.next != 0

        return MangasPage(mangas, hasNextPage)
    }

    // ************ Personal: My History ************ //
    private var myHistoryCursor: String? = null

    private fun sendMyHistoryRequest(page: Int): Request {
        // Reset cursor on first page
        if (page == 1) {
            myHistoryCursor = null
        }

        val apiVariables = ApiMyHistoryVariables(
            start = myHistoryCursor,
            limit = BROWSE_PAGE_SIZE,
        )

        return sendGraphQLRequest(apiVariables, SSER_MY_HISTORY_QUERY)
    }

    private fun myHistoryParse(response: Response): MangasPage {
        val myHistoryResponse = response.parseAs<ApiMyHistoryResponse>().data.response

        // Update cursor for next page
        myHistoryCursor = myHistoryResponse.newStart

        val mangas = myHistoryResponse.items.map { item ->
            item.comicNode.data.toSManga(baseUrl).apply {
                title = title.cleanTitleIfNeeded()
            }
        }

        val hasNextPage = mangas.isNotEmpty() // If mangas is empty, then current page does not exist.

        return MangasPage(mangas, hasNextPage)
    }

    // ************ Personal: User's Publish Comic List ************ //
    private fun userComicListSearch(page: Int, filterParams: String): Observable<MangasPage> {
        return getUserId().flatMap { userId ->
            val params = parseUtilsFilterParams(filterParams)

            val apiVariables = ApiUserComicListVariables(
                userId = userId,
                page = page,
                size = BROWSE_PAGE_SIZE,
                editor = params["editor"],
                siteStatus = params["siteStatus"],
                dbStatus = params["dbStatus"],
                mod_lock = params["mod_lock"],
                mod_hide = params["mod_hide"],
                notUpdatedDays = params["notUpdatedDays"]?.toIntOrNull(),
                scope = params["scope"],
            )

            client.newCall(sendGraphQLRequest(apiVariables, USER_COMIC_LIST_QUERY))
                .asObservableSuccess()
                .map { response ->
                    userComicListParse(response)
                }
        }
    }

    private fun getUserIdLoggedIn(): Observable<String> {
        return client.newCall(GET(baseUrl, headers))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val href = document.select("header div.avatar a[href*=/u/]").firstOrNull()?.attr("href") ?: ""

                userIdRegex.find(href)?.groupValues?.get(1)
                    ?: throw Exception("Could not auto-detect user ID. Please log in to the mirror or set User ID manually in extension settings. Explicitly setting the mirror may also help.")
            }
    }

    private fun getUserId(): Observable<String> {
        // Check if user has manually set a user ID in preferences
        val prefUserId = getUserIdPref()
        if (prefUserId.isNotEmpty()) {
            return Observable.just(prefUserId)
        }

        // Auto-detect user ID from homepage
        return getUserIdLoggedIn()
    }

    private fun parseUtilsFilterParams(filterParams: String): Map<String, String> {
        if (filterParams.isEmpty()) {
            return emptyMap()
        }

        // Parse URL query string format: "key1=value1&key2=value2"
        return filterParams.split("&")
            .mapNotNull { param ->
                val parts = param.split("=")
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun userComicListParse(response: Response): MangasPage {
        val userComicListResponse = response.parseAs<ApiUserComicListResponse>().data.response

        val mangas = userComicListResponse.items.map { item ->
            item.data.toSManga(baseUrl).apply {
                title = title.cleanTitleIfNeeded()
            }
        }
        val hasNextPage = userComicListResponse.paging.next != 0

        return MangasPage(mangas, hasNextPage)
    }

    // ************ Manga Details ************ //
    override fun mangaDetailsRequest(manga: SManga): Request {
        // Check if trying to use a deprecated mirror, force current mirror
        val mangaUrl = if (manga.url.startsWith("http")) {
            val httpUrl = manga.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                newHttpUrl.build().toString()
            } else {
                manga.url
            }
        } else {
            "$baseUrl${manga.url}"
        }

        // Extract comic ID from URL (format: /title/{id}/... or /title/{id}-{title}/...)
        val id = titleIdRegex.find(mangaUrl)?.groupValues?.get(1)
            ?: throw Exception("Could not extract title ID from URL: $mangaUrl")

        val apiVariables = ApiComicNodeVariables(id = id)
        val query = COMIC_NODE_QUERY

        return sendGraphQLRequest(apiVariables, query)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiComicNodeResponse>()
        val comicData = result.data.response.data
        val manga = comicData.toSManga(baseUrl)

        // Apply status parsing and title cleaning
        manga.status = parseStatus(comicData.originalStatus, comicData.uploadStatus)
        manga.title = manga.title.cleanTitleIfNeeded()

        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()

    private fun parseStatus(workStatus: String?, uploadStatus: String?): Int {
        val status = workStatus ?: uploadStatus
        return when {
            status == null -> SManga.UNKNOWN
            status.contains("Ongoing") -> SManga.ONGOING
            status.contains("Cancelled") -> SManga.CANCELLED
            status.contains("Hiatus") -> SManga.ON_HIATUS
            status.contains("Completed") -> when {
                uploadStatus?.contains("Ongoing") == true -> SManga.PUBLISHING_FINISHED
                else -> SManga.COMPLETED
            }
            else -> SManga.UNKNOWN
        }
    }

    // ************ Chapter List ************ //
    override fun chapterListRequest(manga: SManga): Request {
        // Check if trying to use a deprecated mirror, force current mirror
        val mangaUrl = if (manga.url.startsWith("http")) {
            val httpUrl = manga.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                newHttpUrl.build().toString()
            } else {
                manga.url
            }
        } else {
            "$baseUrl${manga.url}"
        }

        // Extract comic ID from URL (format: /title/{id}/... or /title/{id}-{title}/...)
        val id = titleIdRegex.find(mangaUrl)?.groupValues?.get(1)
            ?: throw Exception("Could not extract title ID from URL: $mangaUrl")

        val apiVariables = ApiChapterListVariables(
            comicId = id,
            start = -1,
        )
        val query = CHAPTER_LIST_QUERY

        return sendGraphQLRequest(apiVariables, query)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = response.parseAs<ApiChapterListResponse>().data.response

        return chapterListResponse
            .map { it.data.toSChapter() }
            .reversed()
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    // ************ Page List ************ //
    override fun pageListRequest(chapter: SChapter): Request {
        // Force current mirror if on a deprecated mirror
        val chapterUrl = if (chapter.url.startsWith("http")) {
            val httpUrl = chapter.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                newHttpUrl.build().toString()
            } else {
                chapter.url
            }
        } else {
            "$baseUrl${chapter.url}"
        }

        // Extract chapter ID from URL (format: /title/{titleId}-{title}/{chapterId}-{ch_chapterNumber})
        val id = chapterIdRegex.find(chapterUrl)?.groupValues?.get(1)
            ?: throw Exception("Could not extract chapter ID from URL: $chapterUrl")

        val apiVariables = ApiChapterNodeVariables(id = id)
        val query = CHAPTER_NODE_QUERY

        return sendGraphQLRequest(apiVariables, query)
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> {
        val chapterNodeResponse = response.parseAs<ApiChapterNodeResponse>().data.response

        return chapterNodeResponse.data.imageFile.urlList
            .mapIndexed { index, url ->
                Page(index, "", url)
            }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun String.removeEntities(): String = Parser.unescapeEntities(this, true)

    private fun String.cleanTitleIfNeeded(): String {
        var tempTitle = this
        customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(titleRegex, "")
        }
        return tempTitle.trim()
    }

    private fun imageFallbackInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful) return response

        val urlString = request.url.toString()

        // We know the first attempt failed. Close the response body to release the
        // connection from the pool and prevent resource leaks before we start the retry loop.
        // This is critical; otherwise, new requests in the loop may hang or fail.
        response.close()

        if (SERVER_PATTERN.containsMatchIn(urlString)) {
            // Sorted list: Most reliable servers FIRST
            val servers = listOf("k03", "k06", "k07", "k00", "k01", "k02", "k04", "k05", "k08", "k09", "n03", "n00", "n01", "n02", "n04", "n05", "n06", "n07", "n08", "n09", "n10")

            for (server in servers) {
                val newUrl = urlString.replace(SERVER_PATTERN, "https://$server")

                // Skip if we are about to try the exact same URL that just failed
                if (newUrl == urlString) continue

                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                try {
                    // FORCE SHORT TIMEOUTS FOR FALLBACKS
                    // If a fallback server doesn't answer in 5 seconds, kill it and move to next.
                    val newResponse = chain
                        .withConnectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .withReadTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .proceed(newRequest)

                    if (newResponse.isSuccessful) {
                        return newResponse
                    }
                    // If this server also failed, close and loop to the next one
                    newResponse.close()
                } catch (e: Exception) {
                    // Connection error on this mirror, ignore and loop to next
                }
            }
        }

        // If everything failed, re-run original request to return the standard error
        return chain.proceed(request)
    }

    override fun getFilterList() = FilterList(
        SortFilter(SortFilter.POPULAR_INDEX),
        OriginalStatusFilter(),
        UploadStatusFilter(),
        GenreGroupFilter(),
        OriginalLanguageFilter(),
        TranslationLanguageFilter(siteLang),
        ChapterCountFilter(),
        Filter.Separator(),
        LetterFilter(),
        Filter.Separator(),
        Filter.Header("NOTE: Filters below are incompatible with any other filters!"),
        Filter.Separator(),
        Filter.Header("NOTE: Login required! (Utils list also accept a user id in settings)"),
        PersonalListFilter(),
        UtilsFilter(),
    )

    private fun stripSeriesUrl(url: String): String {
        val matchResult = seriesUrlRegex.find(url)
        return matchResult?.groups?.get(1)?.value ?: url
    }

    private inline fun <reified T> sendGraphQLRequest(variables: T, query: String): Request {
        val payload = json.encodeToString(GraphQLPayload(variables, query))
            .toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Content-Length", payload.contentLength().toString())
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$baseUrl/ap2/", apiHeaders, payload)
    }

    companion object {
        val whitespace by lazy { Regex("\\s+") }
        private val SERVER_PATTERN = Regex("https://[a-zA-Z]\\d{2}")
        private val seriesUrlRegex = Regex("""(.*/series/\d+)/.*""")
        private val titleIdRegex = Regex("""title/(\d+)""")
        private val chapterIdRegex = Regex("""title/[^/]+/(\d+)""")
        private val userIdRegex = Regex("""/u/(\d+)""")
        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror"
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val USER_ID_PREF = "USER_ID"
        private val MIRROR_PREF_ENTRIES = arrayOf(
            "Auto",
            // https://batotomirrors.pages.dev/
            "bato.si",
            "bato.ing",
        )
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES.map { "https://$it" }.toTypedArray()
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]

        private val DEPRECATED_MIRRORS = listOf<String>()

        private const val BROWSE_PAGE_SIZE = 36
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
