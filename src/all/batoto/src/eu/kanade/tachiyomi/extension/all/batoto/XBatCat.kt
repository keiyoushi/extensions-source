package eu.kanade.tachiyomi.extension.all.batoto

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import rx.Observable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class XBatCat(
    final override val lang: String,
    private val siteLang: String = lang,
) : ConfigurableSource, HttpSource() {

    private val preferences by getPreferencesLazy()

    override val name: String = "XBatCat"

    override val baseUrl: String
        get() {
            val index = preferences.getString(MIRROR_PREF_KEY, "0")!!.toInt()
                .coerceAtMost(mirrors.size - 1)

            return mirrors[index]
        }

    override val id: Long = when (lang) {
        "zh-Hans" -> 2818874445640189582
        "zh-Hant" -> 38886079663327225
        "ro-MD" -> 8871355786189601023
        else -> LANGUAGE_IDS[lang] ?: super.id
    }

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageFallbackInterceptor)
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Referer", "$baseUrl/")
                .build()

            chain.proceed(request)
        }
        .build()

    private val imageServerManager: ImageServerManager = ImageServerManager()

    // ************ Search ************ //
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", SortFilter.LATEST)
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", SortFilter.POPULAR)
    }

    // searchMangaRequest is not used, see fetchSearchManga instead
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        // Handle URL and ID search
        val idMatch = when {
            query.startsWith("https://") -> {
                val id = urlIdRegex.find(query)?.groupValues?.get(1)
                    ?: return Observable.error(Exception("Unknown url"))
                idQueryRegex.matchEntire("id:$id")
            }
            else -> idQueryRegex.matchEntire(query.trim())
        }
        if (idMatch != null) {
            val id = idMatch.groupValues[1]

            if (id.toIntOrNull() == null) {
                return Observable.error(Exception("comic id must be integer"))
            }

            val apiVariables = ApiComicNodeVariables(id)

            return client.newCall(graphQLRequest(apiVariables, COMIC_NODE_QUERY))
                .asObservableSuccess()
                .map { response ->
                    MangasPage(listOf(mangaDetailsParse(response)), false)
                }
        }

        // Handle user comic list search
        filters.firstInstanceOrNull<UserComicFilter>()?.takeIf { it.state != 0 }?.also {
            return userComicListSearch(page, it.selected)
        }

        // Handle personal list search
        filters.firstInstanceOrNull<PersonalListFilter>()?.takeIf { it.state != 0 }?.also {
            return when (it.selected) {
                "updates" -> {
                    val apiVariables = ApiMyUpdatesVariables(
                        page = page,
                        size = BROWSE_PAGE_SIZE,
                    )
                    client.newCall(graphQLRequest(apiVariables, SSER_MY_UPDATES_QUERY))
                        .asObservableSuccess()
                        .map { response ->
                            myUpdatesParse(response)
                        }
                }
                "history" -> {
                    client.newCall(myHistoryRequest(page))
                        .asObservableSuccess()
                        .map { response ->
                            myHistoryParse(response)
                        }
                }
                else -> Observable.empty() // Should never happen
            }
        }

        // Handle normal search
        var sort: String? = null
        var letterMode = false
        var incGenres = emptyList<String>()
        var excGenres = emptyList<String>()
        var incOLangs = emptyList<String>()
        var incTLangs = if (siteLang.isEmpty()) emptyList() else listOf(siteLang)
        var origStatus = ""
        var uploadStatus = ""
        var chapCount = ""

        filters.forEach { filter ->
            when (filter) {
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
                    if (filter.selected.isNotEmpty() && lang == "all") {
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
                    sort = filter.selected
                }
                is ChapterCountFilter -> {
                    chapCount = filter.selected
                }
                else -> {}
            }
        }

        val apiVariables = ApiComicSearchVariables(
            pageNumber = page,
            size = BROWSE_PAGE_SIZE,
            sortby = sort,
            query = query,
            where = if (letterMode) "letter" else "browse",
            incGenres = incGenres,
            excGenres = excGenres,
            incOLangs = incOLangs,
            incTLangs = incTLangs,
            origStatus = origStatus,
            siteStatus = uploadStatus,
            chapCount = chapCount,
            ignoreGlobalGenres = isIgnoreGenreBlocklist(),
        )
        val graphQLQuery = COMIC_SEARCH_QUERY

        return client.newCall(graphQLRequest(apiVariables, graphQLQuery))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ApiComicSearchResponse>().data.response

        val mangas = data.items.map { item ->
            item.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }

        return MangasPage(mangas, data.hasNextPage())
    }

    // ************ Personal: My Updates ************ //
    private fun myUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<ApiMyUpdatesResponse>().data.response

        val mangas = data.items.map { item ->
            item.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }

        return MangasPage(mangas, data.hasNextPage())
    }

    // ************ Personal: My History ************ //
    private var myHistoryCursor: String? = null

    private fun myHistoryRequest(page: Int): Request {
        // Reset cursor on first page
        if (page == 1) {
            myHistoryCursor = null
        }

        val apiVariables = ApiMyHistoryVariables(
            start = myHistoryCursor,
            limit = BROWSE_PAGE_SIZE,
        )

        return graphQLRequest(apiVariables, SSER_MY_HISTORY_QUERY)
    }

    private fun myHistoryParse(response: Response): MangasPage {
        val data = response.parseAs<ApiMyHistoryResponse>().data.response

        // Update cursor for next page
        myHistoryCursor = data.newStart

        val mangas = data.items.map { item ->
            item.comicNode.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }

        return MangasPage(mangas, mangas.size == data.reqLimit)
    }

    // ************ Personal: User's Publish Comic List ************ //
    private fun userComicListSearch(page: Int, filterParams: String): Observable<MangasPage> {
        val params = parseUtilsFilterParams(filterParams)

        val apiVariables = ApiUserComicListVariables(
            userId = getUserId(),
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

        return client.newCall(graphQLRequest(apiVariables, USER_COMIC_LIST_QUERY))
            .asObservableSuccess()
            .map { response ->
                userComicListParse(response)
            }
    }

    private var userId: String? = null
    private fun getUserId(): String {
        if (userId != null) {
            return userId!!
        }

        return client.newCall(GET(baseUrl, headers))
            .execute()
            .use { response ->
                val document = response.asJsoup()
                val href = document.selectFirst("header div.avatar a[href*=/u/]")
                    ?.absUrl("href").orEmpty()

                userIdRegex.find(href)?.groupValues?.get(1)
                    ?: throw Exception("Please login in WebView")
            }
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
        val data = response.parseAs<ApiUserComicListResponse>().data.response

        val mangas = data.items.map { item ->
            item.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }

        return MangasPage(mangas, data.hasNextPage())
    }

    // ************ Manga Details ************ //
    override fun mangaDetailsRequest(manga: SManga): Request {
        val apiVariables = ApiComicNodeVariables(id = getMangaId(manga.url))

        return graphQLRequest(apiVariables, COMIC_NODE_QUERY)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiComicNodeResponse>()
        val comicData = result.data.response.data

        return comicData.toSManga(baseUrl, ::cleanTitleIfNeeded)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/title/${getMangaId(manga.url)}"
    }

    private fun getMangaId(url: String): String {
        val matchResult = urlIdRegex.find(url)
        return matchResult?.groups?.get(1)?.value ?: url
    }

    // ************ Chapter List ************ //
    override fun chapterListRequest(manga: SManga): Request {
        val apiVariables = ApiChapterListVariables(
            comicId = getMangaId(manga.url),
            start = -1,
        )

        return graphQLRequest(apiVariables, CHAPTER_LIST_QUERY)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ApiChapterListResponse>().data.response

        return data
            .map { it.data.toSChapter() }
            .asReversed()
    }

    // ************ Page List ************ //
    override fun pageListRequest(chapter: SChapter): Request {
        val apiVariables = ApiChapterNodeVariables(id = chapter.url)

        return graphQLRequest(apiVariables, CHAPTER_NODE_QUERY)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ApiChapterNodeResponse>().data.response.data

        updateHistory(data.comicId, data.id)

        return data.imageFile.urlList
            .mapIndexed { index, url ->
                Page(
                    index = index,
                    imageUrl = url.toHttpUrl().newBuilder()
                        .build()
                        .toString(),
                )
            }
    }

    private fun updateHistory(comicId: String, chapterId: String) {
        val payload = HistoryChapterAdd(
            comicId = comicId,
            chapterId = chapterId,
        ).toJsonString().toRequestBody(jsonMediaType)

        val request = POST("$baseUrl/ap1/history-chapter-add", headers, payload)

        client.newCall(request).enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.closeQuietly()
                }
                override fun onFailure(call: Call, e: IOException) {}
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/title/chapter/${chapter.url}"
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private fun cleanTitleIfNeeded(title: String): String {
        var tempTitle = title
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

        // Extract server, or proceed normally if not found
        val urlString = request.url.toString()
        val originalServer = this.imageServerManager.extractServerFromUrl(urlString) ?: return chain.proceed(request)

        // Try original server if not skipped
        if (!this.imageServerManager.shouldSkip(originalServer)) {
            tryServer(chain, request, originalServer)?.let { return it }
        }

        // Try fallback servers
        for (server in this.imageServerManager.fallbackServers) {
            if (this.imageServerManager.shouldSkip(server)) continue

            val newUrl = this.imageServerManager.replaceServerInUrl(urlString, server)
            val newRequest = request.newBuilder().url(newUrl).build()

            tryServer(chain, newRequest, server, withTimeout = true)?.let { return it }
        }

        return chain.proceed(request)
    }

    private fun tryServer(
        chain: Interceptor.Chain,
        request: Request,
        server: String,
        withTimeout: Boolean = false,
    ): Response? {
        return try {
            // FORCE SHORT TIMEOUTS FOR FALLBACKS
            // If a fallback server doesn't answer in 5 seconds, kill it and move to next server.
            val modifiedChain = if (withTimeout) {
                chain.withConnectTimeout(5, TimeUnit.SECONDS)
                    .withReadTimeout(10, TimeUnit.SECONDS)
            } else {
                chain
            }

            val response = modifiedChain.proceed(request)
            imageServerManager.recordImageServerStatus(server, response.code)

            if (response.isSuccessful) {
                response
            } else {
                response.close()
                null
            }
        } catch (_: Exception) {
            imageServerManager.recordImageServerStatus(server, 0) // Unknown error
            null
        }
    }

    override fun getFilterList(): FilterList {
        val filters = buildList {
            addAll(
                listOf(
                    SortFilter(SortFilter.POPULAR_INDEX),
                    OriginalStatusFilter(),
                    UploadStatusFilter(),
                    GenreGroupFilter(),
                    OriginalLanguageFilter(),
                ),
            )
            if (lang == "all") {
                add(TranslationLanguageFilter())
            }
            addAll(
                listOf(
                    ChapterCountFilter(),
                    LetterFilter(),
                    Filter.Separator(),
                    Filter.Header("NOTE: Filters below are incompatible with any other filters!"),
                    Filter.Header("NOTE: Login required!"),
                    UserComicFilter(),
                    PersonalListFilter(),
                ),
            )
        }

        return FilterList(filters)
    }

    private inline fun <reified T> graphQLRequest(variables: T, query: String): Request {
        val payload = GraphQLPayload(variables, query).toJsonString()
            .toRequestBody(jsonMediaType)

        return POST("$baseUrl/ap2/", headers, payload)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Preferred Mirror"
            entries = mirrors
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the app to apply changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        CheckBoxPreference(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles.\n" +
                "To update existing entries, enable 'Update library manga titles' in advanced settings and refresh manually."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = REMOVE_TITLE_CUSTOM_PREF
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
        }.also(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = IGNORE_GENRE_BLOCKLIST_PREF
            title = "Ignore webview genre blocklist"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun isRemoveTitleVersion(): Boolean {
        return preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    }

    private fun customRemoveTitle(): String =
        preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!

    private fun isIgnoreGenreBlocklist(): Boolean {
        return preferences.getBoolean(IGNORE_GENRE_BLOCKLIST_PREF, false)
    }

    companion object {
        private val jsonMediaType = "application/json".toMediaType()

        private const val MIRROR_PREF_KEY = "MIRROR"
        private val mirrors = arrayOf(
            "https://xbat.app",
            "https://xbat.si",
            "https://xbat.io",
            "https://xbat.me",
            "https://xbat.tv",
            "https://xbat.la",
        )

        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val IGNORE_GENRE_BLOCKLIST_PREF = "IGNORE_GENRE_BLOCKLIST"

        private val idQueryRegex = Regex("^id\\s*:?\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
        private val userIdRegex = Regex("""/u/(\d+)""")
        private val urlIdRegex = Regex("""(?:series|title)/(\d+)""") // match both old v2 or v4 url

        private const val BROWSE_PAGE_SIZE = 36

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)

        private val LANGUAGE_IDS: Map<String, Long> = mapOf(
            "all" to 4531444389842992129L,
            "en" to 7890050626002177109L,
            "ar" to 5096384382752696500L,
            "bg" to 6495955027968241042L,
            "zh" to 3158884414181268012L,
            "cs" to 7576440018916883094L,
            "da" to 5316229865560945519L,
            "nl" to 2451829407813501910L,
            "fil" to 313424729392609270L,
            "fi" to 1251974539278082369L,
            "fr" to 5836611700806032846L,
            "de" to 139923318887989403L,
            "el" to 8473797648002215114L,
            "he" to 2408078218204857839L,
            "hi" to 7084618587403216116L,
            "hu" to 4530528320166985351L,
            "id" to 1720762928205210073L,
            "it" to 2160637426591463299L,
            "ja" to 5203173367987472935L,
            "ko" to 8427126052180576160L,
            "ms" to 5990851390838920547L,
            "pl" to 3394902802486008664L,
            "pt" to 6179954312892676695L,
            "pt-BR" to 3484603489360202582L,
            "ro" to 5145326243818977877L,
            "ru" to 5188408707919914484L,
            "es" to 6204540321264480489L,
            "es-419" to 7185350469868201379L,
            "sv" to 8913559243351013088L,
            "tr" to 6468780937168595917L,
            "uk" to 1782621454240367603L,
            "vi" to 3164440079022386695L,
            "af" to 9193031427501250739L,
            "sq" to 6847493053848422645L,
            "am" to 2526577153570564673L,
            "hy" to 1586739058301487676L,
            "az" to 713056016405537102L,
            "be" to 7232505995053152192L,
            "bn" to 3402500071662542228L,
            "bs" to 6908072148468152785L,
            "my" to 5762198617335159887L,
            "km" to 6796421298740947063L,
            "ca" to 1392737883785118332L,
            "ceb" to 5837595011776494775L,
            "zh-Hans" to 2818874445640189582L,
            "zh-Hant" to 38886079663327225L,
            "hr" to 7668596971407381245L,
            "et" to 3006949234613526276L,
            "fo" to 7703342981951715697L,
            "ka" to 5905178459829728484L,
            "gn" to 4894494172631551657L,
            "gu" to 4891408516910497422L,
            "ht" to 8773112916243804182L,
            "ha" to 8106799273220933842L,
            "is" to 2566480683201153375L,
            "ig" to 3424308522705464084L,
            "ga" to 3707936942181329862L,
            "jv" to 6913455010116693655L,
            "kn" to 1088655658033412867L,
            "kk" to 4920750007190577961L,
            "ku" to 5924797894030210791L,
            "ky" to 191250206471387059L,
            "lo" to 374730826849442838L,
            "lv" to 7483906778681041617L,
            "lt" to 2909322037877472903L,
            "lb" to 7998953198185983599L,
            "mk" to 4493782214399724320L,
            "mg" to 7844022624810291146L,
            "ml" to 3443092856670936701L,
            "mt" to 7404477799743142598L,
            "mi" to 8225972509362693639L,
            "mr" to 6030352007472109485L,
            "mo" to 8871355786189601023L,
            "mn" to 1683078263344915307L,
            "ne" to 128549604427027000L,
            "no" to 3907280050995528877L,
            "ny" to 5715855562377571991L,
            "ps" to 6105060607877949219L,
            "fa" to 1302226087224798096L,
            "rm" to 8327243820444225063L,
            "sm" to 2859258045373504715L,
            "sr" to 6589258274800936036L,
            "sh" to 2675056792049767704L,
            "st" to 7963579117812866356L,
            "sn" to 2403628417982139515L,
            "sd" to 7126242426383705161L,
            "si" to 2913942844788726362L,
            "sk" to 7182321933689697136L,
            "sl" to 8410771330361157651L,
            "so" to 2909968758031724143L,
            "sw" to 1981693893234457639L,
            "tg" to 8403246223033985015L,
            "ta" to 8440188015298648289L,
            "te" to 6410585353225177292L,
            "th" to 2706040171335467974L,
            "ti" to 5554111601105349413L,
            "to" to 2410241309695200602L,
            "tk" to 3635004776434724843L,
            "ur" to 7602124517732860619L,
            "uz" to 4555523281386098571L,
            "yo" to 727983903143794104L,
            "zu" to 3786926370127246407L,
            "other" to 1897863245819012494L,
        )
    }
}

/**
 * Manages image server fallback logic, including blacklisting and backoff tracking.
 */
class ImageServerManager() {
    val serverPattern = Regex("https://([a-zA-Z]\\d{2})")

    val fallbackServers = listOf(
        "i00", "i01", "i02", "i03", "i04", "i05", "i06", "i07", "i08", "i09", "i10", "i11", "i12",
        "i50", "i51", "i52", "i53", "i54", "i55", "i56", "i57", "i58",
    )
    val blacklist = emptyList<String>()

    // Server status tracking
    data class ServerStatus(
        val canBackoff: Boolean,
        val statusCode: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
    )

    val serverStatus = ConcurrentHashMap<String, ServerStatus>()

    val BACKOFF_DURATION_MS = 3_600_000L // 1 hour

    fun shouldSkip(server: String): Boolean {
        return server in blacklist || isInBackoff(server)
    }

    fun isInBackoff(server: String): Boolean {
        val status = serverStatus[server] ?: return false
        return status.canBackoff && System.currentTimeMillis() - status.timestamp < BACKOFF_DURATION_MS
    }

    fun recordImageServerStatus(server: String, statusCode: Int) {
        val now = System.currentTimeMillis()
        serverStatus[server] = ServerStatus(
            canBackoff = statusCode in 500..599,
            statusCode = statusCode,
            timestamp = now,
        )
    }

    fun extractServerFromUrl(url: String): String? {
        return serverPattern.find(url)?.groups?.get(1)?.value
    }

    fun replaceServerInUrl(url: String, newServer: String): String {
        return url.replace(serverPattern, "https://$newServer")
    }
}
