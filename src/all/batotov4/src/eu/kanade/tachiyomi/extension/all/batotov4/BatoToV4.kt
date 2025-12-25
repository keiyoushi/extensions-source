package eu.kanade.tachiyomi.extension.all.batotov4

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

    override val name: String = "Bato.to (V4)"

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
        screen.addPreference(mirrorPref)
        screen.addPreference(removeOfficialPref)
        screen.addPreference(removeCustomPref)
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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage {
        Log.d("BatoToV4", "searchMangaParse: Attempting parse")
        val browseResponse = response.parseAs<ApiComicSearchResponse>().data.response
        Log.d("BatoToV4", "searchMangaParse: Successfully parsed")

        val mangas = browseResponse.items.map { item ->
            item.data.toSManga(baseUrl)
        }
        val hasNextPage = browseResponse.paging.next != 0

        return MangasPage(mangas, hasNextPage)
    }

    fun searchMangaIdParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = SManga.create()
        val infoElement = document.select("div[q:key=\"fU_13\"]")
        val imgurl = infoElement.select("img").attr("src")
        val link = infoElement.select("a")
        manga.setUrlWithoutDomain(stripSeriesUrl(link.attr("href")))
        manga.title = link.text().removeEntities()
            .cleanTitleIfNeeded()
        manga.thumbnail_url = baseUrl + imgurl
        return MangasPage(listOf(manga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith("ID:") -> {
                val id = query.substringAfter("ID:")
                client.newCall(GET("$baseUrl/title/$id", headers)).asObservableSuccess()
                    .map { response ->
                        searchMangaIdParse(response)
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
                var chapCount = ""

                filters.forEach { filter ->
                    when (filter) {
                        // Special Filters (Probably don't work, still v2)
                        is UtilsFilter -> {
                            if (filter.state != 0) {
                                val filterUrl = "$baseUrl/_utils/comic-list?type=${filter.selected}"
                                return client.newCall(GET(filterUrl, headers)).asObservableSuccess()
                                    .map { response ->
                                        queryUtilsParse(response)
                                    }
                            }
                        }
                        is HistoryFilter -> {
                            if (filter.state != 0) {
                                val filterUrl = "$baseUrl/ajax.my.${filter.selected}.paging"
                                return client.newCall(POST(filterUrl, headers, formBuilder().build())).asObservableSuccess()
                                    .map { response ->
                                        queryHistoryParse(response)
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

    // ************ Manga Details ************ //
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            // Check if trying to use a deprecated mirror, force current mirror
            val httpUrl = manga.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                return GET(newHttpUrl.build(), headers)
            }
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("main > div.flex.flex-col.md\\:flex-row")!!
        val titleCardElement = infoElement.selectFirst("> div.grid > div:first-child")!!
        val propertyCardElement = infoElement.selectFirst("> div.grid > div:nth-child(2)")!!
        val descriptionCardElement = infoElement.selectFirst("> div.grid > div:last-child > div > div:first-child")!!
        val manga = SManga.create()
        val workStatus = propertyCardElement.selectFirst("> div:nth-child(3) > span:nth-child(3)")?.text()
        val uploadStatus = propertyCardElement.selectFirst("> div:nth-child(4) > span:nth-child(3)")?.text()
        val originalTitle = titleCardElement.select("> h3 > a").text().removeEntities()
        val description = buildString {
            append(descriptionCardElement.selectFirst("> div:first-child > react-island")?.text())
            infoElement.selectFirst(".episode-list > .alert-warning")?.also {
                append("\n\n${it.text()}")
            }
            descriptionCardElement.selectFirst("> div:nth-child(2) > div:first-child > b")?.takeIf { bTag -> bTag.text() == "Extra info" }?.also { _ ->
                descriptionCardElement.selectFirst("> div:nth-child(2) > div:nth-child(2) > react-island")?.also { reactIsland ->
                    append("\n\nExtra Info:\n${reactIsland.wholeText()}")
                }
            }
            titleCardElement.select("> div:nth-child(2) > span").filter { span -> span.text() != "/" }.takeIf { spans -> spans.isNotEmpty() }?.also { spans ->
                append("\n\nAlternative Titles:\n")
                append(spans.joinToString("\n") { span -> "• ${span.text().trim()}" })
            }
        }.trim()
        val cleanedTitle = originalTitle.cleanTitleIfNeeded()
        val imgurl = infoElement.selectFirst("img")?.attr("src")

        manga.title = cleanedTitle
        val authorArtistLinks = titleCardElement.select("> :nth-child(3) > a")
        val authors = authorArtistLinks.filter { link -> !link.text().endsWith("(Art)") }.map { it.text() }
        val artists = authorArtistLinks.filter { link -> link.text().endsWith("(Art)") }.map { it.text().removeSuffix("(Art)").trim() }
        manga.author = authors.joinToString(", ")
        manga.artist = artists.joinToString(", ")
        manga.status = parseStatus(workStatus, uploadStatus)
        manga.genre = propertyCardElement.select("b:contains(Genres) ~ span > span:first-child").joinToString { it.text() }
        manga.description = if (originalTitle.trim() != cleanedTitle) {
            listOf(originalTitle, description)
                .joinToString("\n\n")
        } else {
            description
        }
        manga.thumbnail_url = if (imgurl?.startsWith("http") == false) {
            "$baseUrl${if (imgurl.startsWith("/")) "" else "/"}$imgurl"
        } else {
            imgurl
        }
        return manga
    }
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
        GenreGroupFilter(),
        OriginalLanguageFilter(),
        TranslationLanguageFilter(siteLang),
        ChapterCountFilter(),
        Filter.Separator(),
        LetterFilter(),
        Filter.Separator(),
        Filter.Header("NOTE: Filters below are incompatible with any other filters!"),
        Filter.Header("NOTE: Login Required!"),
        Filter.Separator(),
        UtilsFilter(),
        HistoryFilter(),
    )

    private fun stripSeriesUrl(url: String): String {
        val matchResult = seriesUrlRegex.find(url)
        return matchResult?.groups?.get(1)?.value ?: url
    }

    private inline fun <reified T> sendGraphQLRequest(variables: T, query: String): Request {
        val payload = json.encodeToString(GraphQLPayload(variables, query))
            .toRequestBody(JSON_MEDIA_TYPE)

        Log.d("BatoToV4", "GraphQL Payload: ${json.encodeToString(GraphQLPayload(variables, query))}")

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
        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror"
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
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
