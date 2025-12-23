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
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.cryptoaes.Deobfuscator
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
        val altChapterListPref = CheckBoxPreference(screen.context).apply {
            key = "${ALT_CHAPTER_LIST_PREF_KEY}_$lang"
            title = ALT_CHAPTER_LIST_PREF_TITLE
            summary = ALT_CHAPTER_LIST_PREF_SUMMARY
            setDefaultValue(ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE)
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
        screen.addPreference(altChapterListPref)
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

    private fun getAltChapterListPref(): Boolean = preferences.getBoolean("${ALT_CHAPTER_LIST_PREF_KEY}_$lang", ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE)
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
    override fun searchMangaSelector(): String {
        return "div[q:key=\"Fc_9\"]"
    }
    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("> div:nth-child(1)")
        val imgurl = item.select("> div > a > img").attr("src")
        val link = item.select("> div > a")
        manga.setUrlWithoutDomain(stripSeriesUrl(link.attr("href")))
        manga.title = element.select("> div:nth-child(2) > h3 > a > span").text().removeEntities()
            .cleanTitleIfNeeded()
        manga.thumbnail_url = baseUrl + imgurl
        return manga
    }
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector())
            .map { element -> searchMangaFromElement(element) }
        val select = buildSearchPayloadFromUrl(response.request.url)

        val nextPage = runCatching { checkNextPage(select) }
            .onFailure { Log.d("BatoToV4", "checkNextPage GraphQL request failed: ${it.message}") }
            .getOrDefault(false)
        return MangasPage(mangas, nextPage)
    }

    private fun buildSearchPayloadFromUrl(url: HttpUrl): SearchPayload {
        val page = url.queryParameter("page")?.toIntOrNull() ?: 1
        val size = url.queryParameter("size")?.toIntOrNull() ?: BROWSE_PAGE_SIZE
        val word = url.queryParameter("word")?.takeIf { it.isNotBlank() }
        val sortby = url.queryParameter("sortby")
        val siteStatus = url.queryParameter("status")
        val chapCount = url.queryParameter("chapters")

        val langParam = url.queryParameter("lang")
        val incTLangs = (langParam?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() })
            ?: listOf(siteLang)

        val origParam = url.queryParameter("orig")
        val incOLangs = origParam?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

        val genresParam = url.queryParameter("genres")
        val (incGenres, excGenres) = when {
            genresParam == null -> null to null
            genresParam.contains("|") -> {
                val parts = genresParam.split("|", limit = 2)
                val included = parts.getOrNull(0)?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                val excluded = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                included to excluded
            }
            else -> genresParam.split(",").filter { it.isNotBlank() }.takeIf { it.isNotEmpty() } to null
        }

        return SearchPayload(
            query = word,
            incGenres = incGenres,
            excGenres = excGenres,
            incTLangs = incTLangs,
            incOLangs = incOLangs,
            sortby = sortby,
            chapCount = chapCount,
            siteStatus = siteStatus,
            page = page,
            size = size,
        )
    }

    /**
     * Use the site's GraphQL pager (get_comic_browse_pager) to determine if there is a next page.
     */
    fun checkNextPage(select: SearchPayload): Boolean {
        val payload = GraphQL(
            SearchVariables(select),
            BROWSE_PAGER_QUERY,
        )

        val body = json.encodeToString(payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = POST("$baseUrl/ap2/", headers, body)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d("BatoToV4", "checkNextPage HTTP ${response.code}")
                return false
            }

            val bodyString = response.body.string()
            val parsed = json.decodeFromString<PagerResponse>(bodyString)
            return parsed.data.pager.next != 0
        }
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
        manga.thumbnail_url = imgurl
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
                val url = "$baseUrl/comics".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())

                if (query.isNotBlank()) {
                    url.addQueryParameter("word", query)
                }

                // Ignore local cookie settings
                url.addQueryParameter("igplangs", "1") // Ignore general language preferences
                // url.addQueryParameter("iggenres", "1") // Ignore general genre blocking (this ignores the default nsfw filter)

                // Set the default site language (will be overriden later)
                url.addQueryParameter("lang", siteLang)

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
                            if (filter.state == 1) {
                                url.addQueryParameter("mode", "letter")
                            }
                        }
                        is GenreGroupFilter -> {
                            if (filter.included.isNotEmpty() || filter.excluded.isNotEmpty()) {
                                val included = filter.included.joinToString(",")
                                val excluded = filter.excluded.joinToString(",")
                                val genresValue = if (filter.excluded.isNotEmpty()) {
                                    "$included|$excluded"
                                } else {
                                    included
                                }
                                url.addQueryParameter("genres", genresValue)
                            }
                        }
                        is OriginalLanguageFilter -> {
                            if (filter.selected.isNotEmpty()) {
                                url.addQueryParameter("orig", filter.selected.joinToString(","))
                            }
                        }
                        is TranslationLanguageFilter -> {
                            if (filter.selected.isNotEmpty()) { // Override site lang if present
                                url.setQueryParameter("lang", filter.selected.joinToString(","))
                            }
                        }
                        is OriginalStatusFilter -> {
                            if (filter.selected.isNotEmpty()) {
                                url.addQueryParameter("status", filter.selected)
                            }
                        }
                        is SortFilter -> {
                            if (filter.state != null) {
                                val sort = filter.selected
                                val value = when (filter.state!!.ascending) {
                                    true -> "az"
                                    false -> "za"
                                }
                                // Bato.to does not seem to have ascending/descending sort. Code is kept for future use.
                                url.addQueryParameter("sortby", sort)
                            }
                        }
                        is ChapterCountFilter -> {
                            if (filter.selected.isNotEmpty()) {
                                url.addQueryParameter("chapters", filter.selected)
                            }
                        }
                        else -> { /* Do Nothing */ }
                    }
                }

                Log.d("BatoToV4", "Search URL: ${url.build()}")

                client.newCall(GET(url.build(), headers)).asObservableSuccess()
                    .map { response ->
                        searchMangaParse(response)
                    }
            }
        }
    }

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
        val infoElement = document.selectFirst("div#mainer div.container-fluid")!!
        val manga = SManga.create()
        val workStatus = infoElement.selectFirst("div.attr-item:contains(original work) span")?.text()
        val uploadStatus = infoElement.selectFirst("div.attr-item:contains(upload status) span")?.text()
        val originalTitle = infoElement.select("h3").text().removeEntities()
        val description = buildString {
            append(infoElement.select("div.limit-html").text())
            infoElement.selectFirst(".episode-list > .alert-warning")?.also {
                append("\n\n${it.text()}")
            }
            infoElement.selectFirst("h5:containsOwn(Extra Info:) + div")?.also {
                append("\n\nExtra Info:\n${it.wholeText()}")
            }
            document.selectFirst("div.pb-2.alias-set.line-b-f")?.takeIf { it.hasText() }?.also {
                append("\n\nAlternative Titles:\n")
                append(it.text().split('/').joinToString("\n") { "• ${it.trim()}" })
            }
        }.trim()
        val cleanedTitle = originalTitle.cleanTitleIfNeeded()

        manga.title = cleanedTitle
        manga.author = infoElement.select("div.attr-item:contains(author) span").text()
        manga.artist = infoElement.select("div.attr-item:contains(artist) span").text()
        manga.status = parseStatus(workStatus, uploadStatus)
        manga.genre = infoElement.select(".attr-item b:contains(genres) + span ").joinToString { it.text() }
        manga.description = if (originalTitle.trim() != cleanedTitle) {
            listOf(originalTitle, description)
                .joinToString("\n\n")
        } else {
            description
        }
        manga.thumbnail_url = document.select("div.attr-cover img").attr("abs:src")
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

    private fun altChapterParse(response: Response): List<SChapter> {
        return Jsoup.parse(response.body.string(), response.request.url.toString(), Parser.xmlParser())
            .select("channel > item").map { item ->
                SChapter.create().apply {
                    setUrlWithoutDomain(item.selectFirst("guid")!!.text())
                    name = item.selectFirst("title")!!.text()
                    date_upload = parseAltChapterDate(item.selectFirst("pubDate")!!.text())
                }
            }
    }

    private val altDateFormat = SimpleDateFormat("E, dd MMM yyyy H:m:s Z", Locale.US)
    private fun parseAltChapterDate(date: String): Long {
        return try {
            altDateFormat.parse(date)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    private fun checkChapterLists(document: Document): Boolean {
        return document.select(".episode-list > .alert-warning").text().contains("This comic has been marked as deleted and the chapter list is not available.")
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = seriesIdRegex.find(manga.url)
            ?.groups?.get(1)?.value?.trim()
        return if (getAltChapterListPref() && !id.isNullOrBlank()) {
            GET("$baseUrl/rss/series/$id.xml", headers)
        } else if (manga.url.startsWith("http")) {
            // Check if trying to use a deprecated mirror, force current mirror
            val httpUrl = manga.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                return GET(newHttpUrl.build(), headers)
            }
            GET(manga.url, headers)
        } else {
            super.chapterListRequest(manga)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (getAltChapterListPref()) {
            return altChapterParse(response)
        }

        val document = response.asJsoup()

        if (checkChapterLists(document)) {
            throw Exception("Deleted from site")
        }

        return document.select(chapterListSelector())
            .map(::chapterFromElement)
    }

    override fun chapterListSelector() = "div.main div.p-2"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select("a.chapt")
        val group = element.select("div.extra > a:not(.ps-3)").text()
        val user = element.select("div.extra > a.ps-3").text()
        val time = element.select("div.extra > i.ps-3").text()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.scanlator = when {
            group.isNotBlank() -> group
            user.isNotBlank() -> user
            else -> "Unknown"
        }
        if (time != "") {
            chapter.date_upload = parseChapterDate(time)
        }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "secs" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, -value)
            }.timeInMillis
            "mins" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, -value)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -value)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value * 7)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, -value)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, -value)
            }.timeInMillis
            "sec" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, -value)
            }.timeInMillis
            "min" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, -value)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -value)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value * 7)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, -value)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, -value)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            // Check if trying to use a deprecated mirror, force current mirror
            val httpUrl = chapter.url.toHttpUrl()
            if ("https://${httpUrl.host}" in DEPRECATED_MIRRORS) {
                val newHttpUrl = httpUrl.newBuilder().host(getMirrorPref().toHttpUrl().host)
                return GET(newHttpUrl.build(), headers)
            }
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(imgHttps):containsData(batoWord):containsData(batoPass)")?.html()
            ?: throw RuntimeException("Couldn't find script with image data.")

        val imgHttpsString = script.substringAfter("const imgHttps =").substringBefore(";").trim()
        val imageUrls = json.parseToJsonElement(imgHttpsString).jsonArray.map { it.jsonPrimitive.content }
        val batoWord = script.substringAfter("const batoWord =").substringBefore(";").trim()
        val batoPass = script.substringAfter("const batoPass =").substringBefore(";").trim()

        val evaluatedPass: String = Deobfuscator.deobfuscateJsPassword(batoPass)
        val imgAccListString = CryptoAES.decrypt(batoWord.removeSurrounding("\""), evaluatedPass)
        val imgAccList = json.parseToJsonElement(imgAccListString).jsonArray.map { it.jsonPrimitive.content }

        return imageUrls.mapIndexed { i, it ->
            val acc = imgAccList.getOrNull(i)
            val url = if (acc != null) {
                "$it?$acc"
            } else {
                it
            }

            Page(i, imageUrl = url)
        }
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

    companion object {
        private val SERVER_PATTERN = Regex("https://[a-zA-Z]\\d{2}")
        private val seriesUrlRegex = Regex("""(.*/series/\d+)/.*""")
        private val seriesIdRegex = Regex("""series/(\d+)""")
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

        private const val ALT_CHAPTER_LIST_PREF_KEY = "ALT_CHAPTER_LIST"
        private const val ALT_CHAPTER_LIST_PREF_TITLE = "Alternative Chapter List"
        private const val ALT_CHAPTER_LIST_PREF_SUMMARY = "If checked, uses an alternate chapter list"
        private const val ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE = false

        private const val BROWSE_PAGE_SIZE = 36
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        private val BROWSE_PAGER_QUERY = """
            query get_comic_browse_pager(${"$"}select: Comic_Browse_Select) {
                get_comic_browse_pager(
                    select: ${"$"}select
                ) {
                    total
                    pages
                    page
                    init
                    size
                    skip
                    limit
                    prev
                    next
                }
            }
        """.trimIndent()

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
