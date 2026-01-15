package eu.kanade.tachiyomi.extension.all.batotov4

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.batoto.ImageServerManager
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
import java.util.concurrent.TimeUnit

class BatoToV4(
    override val baseUrl: String,
    override val lang: String,
    private val siteLang: String = lang,
    private val preferences: SharedPreferences,
) : ConfigurableSource, HttpSource() {

    override val name: String = "Bato.to"

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
        if (query.startsWith("id:", true)) {
            val id = query.lowercase().substringAfter("id:")

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

        filters.firstInstanceOrNull<UserComicFilter>()?.takeIf { it.state != 0 }?.also {
            return userComicListSearch(page, it.selected)
        }

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

    /* Match old v2 Url */
    private fun getMangaId(url: String): String {
        val matchResult = seriesIdRegex.find(url)
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
    }

    private fun isRemoveTitleVersion(): Boolean {
        return preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    }

    private fun customRemoveTitle(): String =
        preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!
}

private val jsonMediaType = "application/json".toMediaType()

private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"

private val userIdRegex = Regex("""/u/(\d+)""")

private const val BROWSE_PAGE_SIZE = 36

// Match old v2 Url
private val seriesIdRegex = Regex("""series/(\d+)""")
private val titleRegex: Regex =
    Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
