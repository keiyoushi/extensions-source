package eu.kanade.tachiyomi.extension.all.batotov4

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun buildQuery(query: String): String = query.trimIndent().replace("%", "$")

class Bato(
    private val sourceLang: String,
    private val siteLang: String,
) : ConfigurableSource, HttpSource() {

    override val name = "Bato.to (v4)"
    override val baseUrl: String get() = getMirrorPref()
    override val lang = sourceLang
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = prefKey(MIRROR_PREF_KEY)
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
        }
        val chapterListPref = ListPreference(screen.context).apply {
            key = prefKey(CHAPTER_LIST_PREF_KEY)
            title = CHAPTER_LIST_PREF_TITLE
            entries = CHAPTER_LIST_PREF_ENTRIES
            entryValues = CHAPTER_LIST_PREF_ENTRY_VALUES
            setDefaultValue(CHAPTER_LIST_PREF_DEFAULT_VALUE)
            summary = "%s"
        }
        val removeOfficialPref = CheckBoxPreference(screen.context).apply {
            key = prefKey(REMOVE_TITLE_VERSION_PREF)
            title = REMOVE_TITLE_VERSION_TITLE
            summary = REMOVE_TITLE_VERSION_SUMMARY
            setDefaultValue(REMOVE_TITLE_VERSION_DEFAULT_VALUE)
        }
        val removeCustomPref = EditTextPreference(screen.context).apply {
            key = prefKey(REMOVE_TITLE_CUSTOM_PREF)
            title = REMOVE_TITLE_CUSTOM_TITLE
            summary = customRemoveTitle()
            setDefaultValue(REMOVE_TITLE_CUSTOM_DEFAULT_VALUE)

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
        screen.addPreference(chapterListPref)
        screen.addPreference(removeOfficialPref)
        screen.addPreference(removeCustomPref)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header(FILTER_GLOBAL_PREFS_HEADER),
        IgnoreLanguageFilter(),
        IgnoreGenreFilter(),
        Filter.Separator(),
        GenreGroupFilter(),
        Filter.Separator(),
        OriginalLanguageFilter(),
        TranslatedLanguageFilter(),
        Filter.Separator(),
        OriginalStatusFilter(),
        UploadStatusFilter(),
        Filter.Separator(),
        ChapterCountFilter(),
    )

    override fun popularMangaRequest(page: Int): Request =
        graphQlRequest(BROWSE_QUERY, buildBrowseVariables(page, null, null))

    override fun popularMangaParse(response: Response): MangasPage =
        parseComicList(response, "get_comic_browse", null)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmedQuery = query.trim()
        return graphQlRequest(BROWSE_QUERY, buildBrowseVariables(page, trimmedQuery, filters))
    }

    override fun searchMangaParse(response: Response): MangasPage =
        parseComicList(response, "get_comic_browse", null)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(ID_PREFIX)) {
            val id = query.substringAfter(ID_PREFIX).trim()
            if (id.isBlank()) {
                return Observable.just(MangasPage(emptyList(), false))
            }
            val request = graphQlRequest(DETAILS_QUERY, buildIdVariables(id))
            return client.newCall(request).asObservableSuccess()
                .map { queryIdParse(it) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        graphQlRequest(LATEST_QUERY, buildLatestVariables(page))

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseComicList(response, "get_latestReleases", siteLang.takeIf { it.isNotBlank() })

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = mangaIdFromUrl(manga.url)
        return graphQlRequest(DETAILS_QUERY, buildIdVariables(id))
    }

    override fun mangaDetailsParse(response: Response): SManga =
        parseMangaDetails(response)

    override fun getMangaUrl(manga: SManga): String = absoluteUrl(manga.url)

    override fun chapterListRequest(manga: SManga): Request {
        return when (getChapterListSource()) {
            ChapterListSource.GRAPHQL -> {
                val id = mangaIdFromUrl(manga.url)
                graphQlRequest(CHAPTERS_QUERY, buildChapterListVariables(id, 0))
            }
            ChapterListSource.QWIK,
            ChapterListSource.HTML,
            -> GET(absoluteUrl(manga.url), headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return when (getChapterListSource()) {
            ChapterListSource.GRAPHQL -> parseChapterListResponse(response)
            ChapterListSource.QWIK -> parseQwikChapterList(response.asJsoup())
            ChapterListSource.HTML -> parseHtmlChapterList(response.asJsoup())
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return when (getChapterListSource()) {
            ChapterListSource.GRAPHQL -> Observable.fromCallable { fetchChapterListGraphql(manga) }
            ChapterListSource.QWIK,
            ChapterListSource.HTML,
            -> super.fetchChapterList(manga)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = absoluteUrl(chapter.url)

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapterIdFromUrl(chapter.url)
        return graphQlRequest(PAGES_QUERY, buildIdVariables(id))
    }

    override fun pageListParse(response: Response): List<Page> =
        parsePageList(response)

    override fun imageUrlParse(response: Response): String = ""

    private fun queryIdParse(response: Response): MangasPage {
        val manga = parseMangaDetails(response)
        return if (manga.title.isBlank()) {
            MangasPage(emptyList(), false)
        } else {
            MangasPage(listOf(manga), false)
        }
    }

    private fun parseComicList(response: Response, rootKey: String, requiredLang: String?): MangasPage {
        val data = response.parseGraphQlData()
        val root = data.optJSONObject(rootKey) ?: return MangasPage(emptyList(), false)
        val items = root.optJSONArray("items") ?: JSONArray()

        val mangaList = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val dataObj = item.optJSONObject("data") ?: continue
                val name = requireField(dataObj.optString("name"), "title")
                val urlPath = requireField(dataObj.optString("urlPath"), "url")
                if (requiredLang != null) {
                    val tranLang = dataObj.optString("tranLang")
                    if (tranLang != requiredLang) continue
                }
                val cover = firstNonBlank(
                    dataObj.optString("urlCover600"),
                    dataObj.optString("urlCover300"),
                    dataObj.optString("urlCoverOri"),
                )
                add(
                    SManga.create().apply {
                        title = name.cleanTitleIfNeeded()
                        url = urlPath
                        thumbnail_url = absoluteUrlOrNull(cover)
                    },
                )
            }
        }.distinctBy { it.url }

        val paging = root.optJSONObject("paging")
        val hasNextPage = hasNextPage(paging, mangaList.size)

        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseMangaDetails(response: Response): SManga {
        val data = response.parseGraphQlData()
        val node = data.optJSONObject("get_comicNode")?.optJSONObject("data")
        val details = SManga.create()
        if (node != null) {
            val originalTitle = requireField(node.optString("name"), "title")
            details.title = originalTitle.cleanTitleIfNeeded()
            details.url = requireField(node.optString("urlPath"), "url")
            val cover = firstNonBlank(
                node.optString("urlCover600"),
                node.optString("urlCover300"),
                node.optString("urlCoverOri"),
            )
            details.thumbnail_url = absoluteUrlOrNull(cover)
            details.description = node.optString("summary")
            val genres = extractStringList(node.opt("genres")).joinToString()
            if (genres.isNotBlank()) details.genre = genres
            val authors = extractStringList(node.opt("authors")).joinToString()
            if (authors.isNotBlank()) details.author = authors
            val artists = extractStringList(node.opt("artists")).joinToString()
            if (artists.isNotBlank()) details.artist = artists
            details.status = parseStatus(node.optString("originalStatus"))
        }
        details.initialized = true
        return details
    }

    private fun fetchChapterListGraphql(manga: SManga): List<SChapter> {
        val id = mangaIdFromUrl(manga.url).takeIf { it.isNotBlank() } ?: return emptyList()
        val totalChapters = fetchChapterCount(id)
        val starts = buildChapterStarts(totalChapters)
        val chapters = mutableListOf<SChapter>()

        for (start in starts) {
            val request = graphQlRequest(CHAPTERS_QUERY, buildChapterListVariables(id, start))
            client.newCall(request).execute().use { response ->
                chapters.addAll(parseChapterListResponse(response))
            }
        }

        return sortChapters(chapters.distinctBy { it.url })
    }

    private fun parseChapterListResponse(response: Response): List<SChapter> {
        val data = response.parseGraphQlData()
        val list = data.optJSONArray("get_comic_chapterList") ?: return emptyList()
        val chapters = mutableListOf<SChapter>()

        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val dataObj = item.optJSONObject("data") ?: continue
            val name = requireField(dataObj.optString("dname").ifBlank { dataObj.optString("title") }, "chapter title")
            val urlPath = requireField(dataObj.optString("urlPath"), "chapter url")
            val dateModify = asLong(dataObj.opt("dateModify")) ?: 0L
            val dateCreate = asLong(dataObj.opt("dateCreate")) ?: 0L
            val date = if (dateModify > 0L) dateModify else dateCreate
            val chapterNumber = parseChapterNumber(name, urlPath)

            chapters.add(
                SChapter.create().apply {
                    url = urlPath
                    this.name = name
                    date_upload = date
                    if (chapterNumber != null) {
                        chapter_number = chapterNumber
                    }
                },
            )
        }

        return chapters
    }

    private fun parsePageList(response: Response): List<Page> {
        val data = response.parseGraphQlData()
        val urlList = data.optJSONObject("get_chapterNode")
            ?.optJSONObject("data")
            ?.optJSONObject("imageFile")
            ?.optJSONArray("urlList")
            ?: return emptyList()

        val pages = mutableListOf<Page>()
        for (i in 0 until urlList.length()) {
            val url = urlList.optString(i).takeIf { it.isNotBlank() } ?: continue
            pages.add(Page(i, "", normalizeImageUrl(url)))
        }
        return pages
    }

    private fun parseHtmlChapterList(document: Document): List<SChapter> {
        val seriesId = SERIES_ID_REGEX.find(document.location())?.groups?.get(1)?.value
        val chapters = mutableListOf<SChapter>()
        val seen = mutableSetOf<String>()

        for (link in document.select("a[href*=\"/title/\"]")) {
            val rawUrl = link.attr("href")
            val path = extractPath(rawUrl) ?: continue
            if (!CHAPTER_URL_REGEX.matches(path)) continue
            val matchSeriesId = SERIES_ID_REGEX.find(path)?.groups?.get(1)?.value ?: continue
            if (seriesId != null && matchSeriesId != seriesId) continue

            val chapterUrl = if (path.startsWith("/")) path else "/$path"
            if (!seen.add(chapterUrl)) continue

            val name = requireField(
                firstNonBlank(
                    link.attr("title").trim(),
                    link.attr("aria-label").trim(),
                    link.text().trim(),
                ),
                "chapter title",
            )
            val chapterNumber = parseChapterNumber(name, chapterUrl)
            val dateText = findDateText(link)
            val date = parseDateText(dateText)

            chapters.add(
                SChapter.create().apply {
                    url = chapterUrl
                    this.name = name
                    if (date > 0L) {
                        date_upload = date
                    }
                    if (chapterNumber != null) {
                        chapter_number = chapterNumber
                    }
                },
            )
        }

        return sortChapters(chapters.distinctBy { it.url })
    }

    private fun parseQwikChapterList(document: Document): List<SChapter> {
        val objs = parseQwikObjs(document) ?: return emptyList()
        val cache = mutableMapOf<Int, Any?>()
        val chapters = mutableListOf<SChapter>()

        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            if (!obj.has("urlPath") || !obj.has("dname")) continue

            val resolved = resolveQwikObject(obj, objs, cache)
            val urlPath = requireField(asString(resolved.opt("urlPath")), "chapter url")
            if (!urlPath.contains("/title/")) continue
            val name = requireField(asString(resolved.opt("dname")), "chapter title")
            val dateCreate = asLong(resolved.opt("dateCreate")) ?: 0L
            val chapterNumber = parseChapterNumber(name, urlPath)

            chapters.add(
                SChapter.create().apply {
                    url = urlPath
                    this.name = name
                    date_upload = dateCreate
                    if (chapterNumber != null) {
                        chapter_number = chapterNumber
                    }
                },
            )
        }

        return sortChapters(chapters.distinctBy { it.url })
    }

    private fun buildBrowseVariables(page: Int, query: String?, filters: FilterList?): JSONObject {
        val select = JSONObject()
            .put("where", "browse")
            .put("page", page)
            .put("size", DEFAULT_PAGE_SIZE)

        if (!query.isNullOrBlank()) {
            select.put("word", query)
        }

        applyFilters(select, filters)
        return JSONObject().put("select", select)
    }

    private fun buildLatestVariables(page: Int): JSONObject {
        val select = JSONObject()
            .put("page", page)
            .put("size", DEFAULT_PAGE_SIZE)
        return JSONObject().put("select", select)
    }

    private fun buildIdVariables(id: String): JSONObject =
        JSONObject().put("id", id)

    private fun buildChapterListVariables(id: String, start: Int): JSONObject =
        JSONObject()
            .put("id", id)
            .put("start", start)

    private fun graphQlRequest(query: String, variables: JSONObject): Request {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        return POST("$baseUrl/ap2/", headers, payload)
    }

    private fun Response.parseGraphQlData(): JSONObject {
        if (!isSuccessful) {
            throw Exception("HTTP $code ${message.ifBlank { "error" }}")
        }
        val body = body.string()
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return JSONObject()
        val errors = json.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val message = errors.optJSONObject(0)?.optString("message")
            if (!message.isNullOrBlank()) {
                throw Exception(message)
            }
        }
        return json.optJSONObject("data") ?: JSONObject()
    }

    private fun parseQwikObjs(document: Document): JSONArray? {
        val scripts = document.select("script[type=qwik/json]")
        for (script in scripts) {
            val jsonText = script.html().trim()
            if (jsonText.isEmpty()) continue
            val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: continue
            val objs = root.optJSONArray("objs")
            if (objs != null) return objs
        }
        return null
    }

    private fun resolveQwikObject(
        obj: JSONObject,
        objs: JSONArray,
        cache: MutableMap<Int, Any?>,
    ): JSONObject {
        val resolved = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = resolveQwikValue(obj.opt(key), objs, cache)
            resolved.put(key, value ?: JSONObject.NULL)
        }
        return resolved
    }

    private fun resolveQwikValue(
        value: Any?,
        objs: JSONArray,
        cache: MutableMap<Int, Any?>,
    ): Any? {
        if (value == null || value === JSONObject.NULL) return null
        if (value is String) {
            val index = value.toIntOrNull(36)
            if (index != null && index in 0 until objs.length()) {
                if (cache.containsKey(index)) return cache[index]
                val resolved = resolveQwikValue(objs.opt(index), objs, cache)
                cache[index] = resolved
                return resolved
            }
            return value
        }
        if (value is JSONObject) {
            return resolveQwikObject(value, objs, cache)
        }
        if (value is JSONArray) {
            val resolved = JSONArray()
            for (i in 0 until value.length()) {
                val item = resolveQwikValue(value.opt(i), objs, cache)
                resolved.put(item ?: JSONObject.NULL)
            }
            return resolved
        }
        return value
    }

    private fun asString(value: Any?): String? {
        return when (value) {
            null, JSONObject.NULL -> null
            is String -> value
            else -> null
        }
    }

    private fun fetchChapterCount(id: String): Int {
        val request = graphQlRequest(DETAILS_QUERY, buildIdVariables(id))
        return client.newCall(request).execute().use { response ->
            val data = response.parseGraphQlData()
            val node = data.optJSONObject("get_comicNode")?.optJSONObject("data")
            asLong(node?.opt("chaps_normal"))?.toInt() ?: 0
        }
    }

    private fun buildChapterStarts(totalChapters: Int): List<Int> {
        if (totalChapters <= CHAPTER_PAGE_SIZE) {
            return listOf(0)
        }
        val starts = mutableListOf(0)
        val maxStart = ((totalChapters - 1) / CHAPTER_PAGE_SIZE) * CHAPTER_PAGE_SIZE
        var start = CHAPTER_PAGE_SIZE
        while (start <= maxStart) {
            starts.add(start)
            start += CHAPTER_PAGE_SIZE
        }
        return starts
    }

    private fun hasNextPage(paging: JSONObject?, itemCount: Int): Boolean {
        val next = asLong(paging?.opt("next"))?.toInt()
        if (next != null) return next > 0
        val page = asLong(paging?.opt("page"))?.toInt()
        val pages = asLong(paging?.opt("pages"))?.toInt()
        if (page != null && pages != null) return page < pages
        return itemCount >= DEFAULT_PAGE_SIZE
    }

    private fun mangaIdFromUrl(url: String): String {
        val match = SERIES_ID_REGEX.find(url)
        return match?.groups?.get(1)?.value.orEmpty()
    }

    private fun chapterIdFromUrl(url: String): String {
        val match = CHAPTER_ID_REGEX.find(url)
        return match?.groups?.get(1)?.value.orEmpty()
    }

    private fun extractStringList(value: Any?): List<String> {
        return when (value) {
            null, JSONObject.NULL -> emptyList()
            is JSONArray -> {
                val results = mutableListOf<String>()
                for (i in 0 until value.length()) {
                    val item = extractString(value.opt(i))
                    if (item != null) results.add(item)
                }
                results
            }
            else -> extractString(value)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun extractString(value: Any?): String? {
        return when (value) {
            null, JSONObject.NULL -> null
            is String -> value
            else -> null
        }
    }

    private fun asLong(value: Any?): Long? {
        return when (value) {
            null, JSONObject.NULL -> null
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun parseChapterNumber(name: String, urlPath: String): Float? {
        val fromUrl = CHAPTER_NUMBER_REGEX.find(urlPath)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        if (fromUrl != null && fromUrl > 0f) return fromUrl
        val fromName = CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        return fromName?.takeIf { it > 0f }
    }

    private fun sortChapters(chapters: List<SChapter>): List<SChapter> {
        return chapters.sortedWith { a, b ->
            val aNum = a.chapter_number
            val bNum = b.chapter_number
            val aHas = aNum > 0f
            val bHas = bNum > 0f
            when {
                aHas && bHas -> {
                    val numberCompare = bNum.compareTo(aNum)
                    if (numberCompare != 0) numberCompare else b.date_upload.compareTo(a.date_upload)
                }
                aHas -> -1
                bHas -> 1
                else -> b.date_upload.compareTo(a.date_upload)
            }
        }
    }

    private fun findDateText(link: Element): String? {
        val container = link.closest("li, div") ?: link
        val timeElement = container.selectFirst("time")
        val timeText = timeElement?.attr("datetime").takeIf { !it.isNullOrBlank() }
            ?: timeElement?.text()
        if (!timeText.isNullOrBlank()) return timeText.trim()
        val textElement = container.selectFirst("span.time, span.date, div.time, div.date")
        return textElement?.text()?.trim().takeIf { !it.isNullOrBlank() }
    }

    private fun parseDateText(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        parseRelativeDate(text)?.let { return it }
        for (pattern in HTML_DATE_PATTERNS) {
            val parsed = SimpleDateFormat(pattern, Locale.US).tryParse(text)
            if (parsed != 0L) return parsed
        }
        return 0L
    }

    private fun parseRelativeDate(text: String): Long? {
        val match = RELATIVE_DATE_REGEX.find(text.lowercase(Locale.US)) ?: return null
        val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2).orEmpty()
        val calendar = Calendar.getInstance()
        when {
            unit.startsWith("sec") -> calendar.add(Calendar.SECOND, -value)
            unit.startsWith("min") -> calendar.add(Calendar.MINUTE, -value)
            unit.startsWith("hour") -> calendar.add(Calendar.HOUR_OF_DAY, -value)
            unit.startsWith("day") -> calendar.add(Calendar.DATE, -value)
            unit.startsWith("week") -> calendar.add(Calendar.DATE, -value * 7)
            unit.startsWith("month") -> calendar.add(Calendar.MONTH, -value)
            unit.startsWith("year") -> calendar.add(Calendar.YEAR, -value)
            else -> return null
        }
        return calendar.timeInMillis
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun requireField(value: String?, label: String): String {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) {
            throw Exception("Missing $label")
        }
        return trimmed
    }

    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun normalizeImageUrl(url: String): String {
        return when {
            url.startsWith("https://k") -> "https://n" + url.removePrefix("https://k")
            url.startsWith("http://k") -> "http://n" + url.removePrefix("http://k")
            else -> url
        }
    }

    private fun getMirrorPref(): String =
        preferences.getString(prefKey(MIRROR_PREF_KEY), MIRROR_PREF_DEFAULT_VALUE) ?: MIRROR_PREF_DEFAULT_VALUE

    private fun getChapterListSource(): ChapterListSource {
        val key = prefKey(CHAPTER_LIST_PREF_KEY)
        val value = preferences.getString(key, null)
        if (value != null) {
            return ChapterListSource.fromPref(value)
        }
        val legacyKey = prefKey(ALT_CHAPTER_LIST_PREF_KEY)
        val legacy = preferences.getBoolean(legacyKey, ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE)
        return if (legacy) ChapterListSource.HTML else ChapterListSource.GRAPHQL
    }

    private fun isRemoveTitleVersion(): Boolean =
        preferences.getBoolean(prefKey(REMOVE_TITLE_VERSION_PREF), REMOVE_TITLE_VERSION_DEFAULT_VALUE)

    private fun customRemoveTitle(): String =
        preferences.getString(prefKey(REMOVE_TITLE_CUSTOM_PREF), REMOVE_TITLE_CUSTOM_DEFAULT_VALUE).orEmpty()

    private fun prefKey(base: String): String = "${base}_$lang"

    private fun String.cleanTitleIfNeeded(): String {
        var tempTitle = this
        customRemoveTitle().takeIf { it.isNotBlank() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(TITLE_REGEX, "")
        }
        return tempTitle.trim()
    }

    private fun absoluteUrl(url: String): String =
        if (url.startsWith("http")) url else "$baseUrl$url"

    private fun absoluteUrlOrNull(url: String?): String? =
        if (url.isNullOrBlank()) null else absoluteUrl(url)

    private fun extractPath(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http")) {
            return runCatching { trimmed.toHttpUrl().encodedPath }.getOrNull()
        }
        return trimmed.substringBefore('?').substringBefore('#')
    }

    private fun applyFilters(select: JSONObject, filters: FilterList?) {
        val includeGenres = mutableListOf<String>()
        val excludeGenres = mutableListOf<String>()
        var translatedLangs: List<String> = emptyList()
        var originalLangs: List<String> = emptyList()
        var originalStatus: String? = null
        var uploadStatus: String? = null
        var chapterCount: String? = null
        var ignoreLangs = false
        var ignoreGenres = false

        filters?.forEach { filter ->
            when (filter) {
                is IgnoreLanguageFilter -> ignoreLangs = filter.state
                is IgnoreGenreFilter -> ignoreGenres = filter.state
                is GenreGroupFilter -> filter.state.forEach { option ->
                    when (option.state) {
                        Filter.TriState.STATE_INCLUDE -> includeGenres.add(option.value)
                        Filter.TriState.STATE_EXCLUDE -> excludeGenres.add(option.value)
                        else -> Unit
                    }
                }
                is OriginalLanguageFilter -> originalLangs = filter.state.filter { it.state }.map { it.value }
                is TranslatedLanguageFilter -> translatedLangs = filter.state.filter { it.state }.map { it.value }
                is OriginalStatusFilter -> {
                    val value = ORIGINAL_STATUS_VALUES.getOrNull(filter.state) ?: ""
                    if (value.isNotBlank()) originalStatus = value
                }
                is UploadStatusFilter -> {
                    val value = UPLOAD_STATUS_VALUES.getOrNull(filter.state) ?: ""
                    if (value.isNotBlank()) uploadStatus = value
                }
                is ChapterCountFilter -> {
                    val value = CHAPTER_COUNT_VALUES.getOrNull(filter.state) ?: ""
                    if (value.isNotBlank()) chapterCount = value
                }
                else -> Unit
            }
        }

        val resolvedTranslated = if (translatedLangs.isNotEmpty()) {
            translatedLangs
        } else if (siteLang.isNotBlank()) {
            listOf(siteLang)
        } else {
            emptyList()
        }

        select.putArrayIfNotEmpty("incTLangs", resolvedTranslated)
        select.putArrayIfNotEmpty("incOLangs", originalLangs)
        select.putArrayIfNotEmpty("incGenres", includeGenres)
        select.putArrayIfNotEmpty("excGenres", excludeGenres)
        if (originalStatus != null) {
            select.put("origStatus", originalStatus)
        }
        if (uploadStatus != null) {
            select.put("siteStatus", uploadStatus)
        }
        if (chapterCount != null) {
            select.put("chapCount", chapterCount)
        }
        if (ignoreLangs) {
            select.put("ignoreGlobalULangs", true)
        }
        if (ignoreGenres) {
            select.put("ignoreGlobalGenres", true)
        }
    }

    private fun JSONObject.putArrayIfNotEmpty(key: String, values: List<String>) {
        if (values.isNotEmpty()) {
            put(key, JSONArray(values))
        }
    }

    private companion object {
        private const val DEFAULT_PAGE_SIZE = 36
        private const val CHAPTER_PAGE_SIZE = 100
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror"
        private val MIRROR_PREF_ENTRIES = arrayOf("bato.si", "bato.ing")
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES.map { "https://$it" }.toTypedArray()
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]

        private const val CHAPTER_LIST_PREF_KEY = "CHAPTER_LIST_SOURCE"
        private const val CHAPTER_LIST_PREF_TITLE = "Chapter list source"
        private val CHAPTER_LIST_PREF_ENTRIES = arrayOf("GraphQL (default)", "Qwik (legacy)", "HTML (legacy)")
        private val CHAPTER_LIST_PREF_ENTRY_VALUES = arrayOf("graphql", "qwik", "html")
        private const val CHAPTER_LIST_PREF_DEFAULT_VALUE = "graphql"

        private const val ALT_CHAPTER_LIST_PREF_KEY = "ALT_CHAPTER_LIST"
        private const val ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE = false

        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_VERSION_TITLE = "Remove version information from entry titles"
        private const val REMOVE_TITLE_VERSION_SUMMARY = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles " +
            "and helps identify duplicate entries in your library. " +
            "To update existing entries, remove them from your library (unfavorite) and refresh manually."
        private const val REMOVE_TITLE_VERSION_DEFAULT_VALUE = false
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val REMOVE_TITLE_CUSTOM_TITLE = "Custom regex to be removed from title"
        private const val REMOVE_TITLE_CUSTOM_DEFAULT_VALUE = ""

        private val TITLE_REGEX: Regex =
            Regex(
                "\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official",
                RegexOption.IGNORE_CASE,
            )

        private val SERIES_ID_REGEX = Regex("/title/(\\d+)")
        private val CHAPTER_ID_REGEX = Regex("/title/\\d+[^/]*/(\\d+)")
        private val CHAPTER_URL_REGEX = Regex("^/?title/\\d+[^/]*/\\d+[^/]*$")
        private val CHAPTER_NUMBER_REGEX = Regex(
            """(?:ch(?:apter)?)[\\s._-]*([0-9]+(?:\\.[0-9]+)?)""",
            RegexOption.IGNORE_CASE,
        )
        private const val ID_PREFIX = "ID:"
        private val RELATIVE_DATE_REGEX = Regex(
            """([0-9]+)\\s*(sec|secs|second|seconds|min|mins|minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)""",
            RegexOption.IGNORE_CASE,
        )
        private val HTML_DATE_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss",
        )

        private val BROWSE_QUERY = buildQuery(
            """
            query(%select: Comic_Browse_Select) {
                get_comic_browse(select: %select) {
                    paging {
                        page
                        pages
                        next
                        size
                        total
                    }
                    items {
                        data {
                            id
                            name
                            tranLang
                            urlPath
                            urlCover600
                            urlCover300
                            urlCoverOri
                        }
                    }
                }
            }
            """,
        )

        private val LATEST_QUERY = buildQuery(
            """
            query(%select: LatestReleases_Select) {
                get_latestReleases(select: %select) {
                    paging {
                        page
                        pages
                        next
                        size
                        total
                    }
                    items {
                        data {
                            id
                            name
                            tranLang
                            urlPath
                            urlCover600
                            urlCover300
                            urlCoverOri
                        }
                    }
                }
            }
            """,
        )

        private val DETAILS_QUERY = buildQuery(
            """
            query(%id: ID!) {
                get_comicNode(id: %id) {
                    data {
                        id
                        name
                        summary
                        authors
                        artists
                        genres
                        originalStatus
                        uploadStatus
                        tranLang
                        origLang
                        urlPath
                        urlCover600
                        urlCover300
                        urlCoverOri
                        chaps_normal
                    }
                }
            }
            """,
        )

        private val CHAPTERS_QUERY = buildQuery(
            """
            query(%id: ID!, %start: Int) {
                get_comic_chapterList(comicId: %id, start: %start) {
                    data {
                        id
                        dname
                        dateCreate
                        dateModify
                        urlPath
                    }
                }
            }
            """,
        )

        private val PAGES_QUERY = buildQuery(
            """
            query(%id: ID!) {
                get_chapterNode(id: %id) {
                    data {
                        imageFile {
                            urlList
                        }
                    }
                }
            }
            """,
        )
    }
}

private enum class ChapterListSource(val pref: String) {
    GRAPHQL("graphql"),
    QWIK("qwik"),
    HTML("html"),
    ;

    companion object {
        fun fromPref(value: String): ChapterListSource =
            values().firstOrNull { it.pref == value } ?: GRAPHQL
    }
}

private const val FILTER_GLOBAL_PREFS_HEADER = "Global preferences"
private const val FILTER_IGNORE_LANGUAGE_TITLE = "Ignore my general language preferences"
private const val FILTER_IGNORE_GENRE_TITLE = "Ignore my general genres blocking"
private const val FILTER_GENRES_TITLE = "Genres"
private const val FILTER_ORIGINAL_LANGUAGE_TITLE = "Original Work Language"
private const val FILTER_TRANSLATED_LANGUAGE_TITLE = "Translated Language"
private const val FILTER_ORIGINAL_STATUS_TITLE = "Original Work Status"
private const val FILTER_UPLOAD_STATUS_TITLE = "Bato Upload Status"
private const val FILTER_CHAPTER_COUNT_TITLE = "Number of chapters"

private val ORIGINAL_STATUS_OPTIONS = arrayOf(
    "Any" to "",
    "Pending" to "pending",
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
)
private val UPLOAD_STATUS_OPTIONS = ORIGINAL_STATUS_OPTIONS

private val ORIGINAL_STATUS_LABELS = ORIGINAL_STATUS_OPTIONS.map { it.first }.toTypedArray()
private val ORIGINAL_STATUS_VALUES = ORIGINAL_STATUS_OPTIONS.map { it.second }.toTypedArray()
private val UPLOAD_STATUS_LABELS = UPLOAD_STATUS_OPTIONS.map { it.first }.toTypedArray()
private val UPLOAD_STATUS_VALUES = UPLOAD_STATUS_OPTIONS.map { it.second }.toTypedArray()
private val CHAPTER_COUNT_OPTIONS = arrayOf(
    "Any" to "",
    "0" to "0",
    "1+" to "1",
    "10+" to "10",
    "20+" to "20",
    "30+" to "30",
    "40+" to "40",
    "50+" to "50",
    "60+" to "60",
    "70+" to "70",
    "80+" to "80",
    "90+" to "90",
    "100+" to "100",
    "200+" to "200",
    "300+" to "300",
    "1-9" to "1-9",
    "10-19" to "10-19",
    "20-29" to "20-29",
    "30-39" to "30-39",
    "40-49" to "40-49",
    "50-59" to "50-59",
    "60-69" to "60-69",
    "70-79" to "70-79",
    "80-89" to "80-89",
    "90-99" to "90-99",
    "100-199" to "100-199",
    "200-299" to "200-299",
)

private val CHAPTER_COUNT_LABELS = CHAPTER_COUNT_OPTIONS.map { it.first }.toTypedArray()
private val CHAPTER_COUNT_VALUES = CHAPTER_COUNT_OPTIONS.map { it.second }.toTypedArray()

private class IgnoreLanguageFilter : Filter.CheckBox(FILTER_IGNORE_LANGUAGE_TITLE)

private class IgnoreGenreFilter : Filter.CheckBox(FILTER_IGNORE_GENRE_TITLE)

private class GenreFilter(val value: String, name: String) : Filter.TriState(name)

private class GenreGroupFilter : Filter.Group<GenreFilter>(
    FILTER_GENRES_TITLE,
    getGenreList(),
)

private class LanguageFilterOption(val value: String, name: String) : Filter.CheckBox(name)

private class OriginalLanguageFilter : Filter.Group<LanguageFilterOption>(
    FILTER_ORIGINAL_LANGUAGE_TITLE,
    getOriginalLanguageList(),
)

private class TranslatedLanguageFilter : Filter.Group<LanguageFilterOption>(
    FILTER_TRANSLATED_LANGUAGE_TITLE,
    getTranslatedLanguageList(),
)

private class OriginalStatusFilter : Filter.Select<String>(
    FILTER_ORIGINAL_STATUS_TITLE,
    ORIGINAL_STATUS_LABELS,
)

private class UploadStatusFilter : Filter.Select<String>(
    FILTER_UPLOAD_STATUS_TITLE,
    UPLOAD_STATUS_LABELS,
)

private class ChapterCountFilter : Filter.Select<String>(
    FILTER_CHAPTER_COUNT_TITLE,
    CHAPTER_COUNT_LABELS,
)

private fun getOriginalLanguageList() = listOf(
    LanguageFilterOption("zh", "Chinese"),
    LanguageFilterOption("en", "English"),
    LanguageFilterOption("ja", "Japanese"),
    LanguageFilterOption("ko", "Korean"),
)

private fun getTranslatedLanguageList() = listOf(
    LanguageFilterOption("en", "English"),
    LanguageFilterOption("es", "Spanish"),
    LanguageFilterOption("es_419", "Spanish (LA)"),
)

private fun getGenreList() = listOf(
    GenreFilter("manga", "Manga"),
    GenreFilter("manhua", "Manhua"),
    GenreFilter("manhwa", "Manhwa"),
    GenreFilter("webtoon", "Webtoon"),
    GenreFilter("comic", "Comic"),
    GenreFilter("cartoon", "Cartoon"),
    GenreFilter("western", "Western"),
    GenreFilter("doujinshi", "Doujinshi"),
    GenreFilter("_4_koma", "4-Koma"),
    GenreFilter("oneshot", "Oneshot"),
    GenreFilter("artbook", "Artbook"),
    GenreFilter("imageset", "Imageset"),

    GenreFilter("shoujo", "Shoujo(G)"),
    GenreFilter("shounen", "Shounen(B)"),
    GenreFilter("josei", "Josei(W)"),
    GenreFilter("seinen", "Seinen(M)"),
    GenreFilter("yuri", "Yuri(GL)"),
    GenreFilter("yaoi", "Yaoi(BL)"),
    GenreFilter("bara", "Bara(ML)"),
    GenreFilter("kodomo", "Kodomo(Kid)"),
    GenreFilter("old_people", "Silver & Golden"),

    GenreFilter("gore", "Gore"),
    GenreFilter("bloody", "Bloody"),
    GenreFilter("violence", "Violence"),
    GenreFilter("ecchi", "Ecchi"),
    GenreFilter("adult", "Adult"),
    GenreFilter("mature", "Mature"),
    GenreFilter("smut", "Smut"),

    GenreFilter("action", "Action"),
    GenreFilter("adaptation", "Adaptation"),
    GenreFilter("adventure", "Adventure"),
    GenreFilter("age_gap", "Age Gap"),
    GenreFilter("aliens", "Aliens"),
    GenreFilter("animals", "Animals"),
    GenreFilter("anthology", "Anthology"),
    GenreFilter("beasts", "Beasts"),
    GenreFilter("bodyswap", "Bodyswap"),
    GenreFilter("cars", "Cars"),
    GenreFilter("cheating_infidelity", "Cheating/Infidelity"),
    GenreFilter("childhood_friends", "Childhood Friends"),
    GenreFilter("college_life", "College Life"),
    GenreFilter("comedy", "Comedy"),
    GenreFilter("contest_winning", "Contest Winning"),
    GenreFilter("cooking", "Cooking"),
    GenreFilter("crime", "Crime"),
    GenreFilter("crossdressing", "Crossdressing"),
    GenreFilter("delinquents", "Delinquents"),
    GenreFilter("dementia", "Dementia"),
    GenreFilter("demons", "Demons"),
    GenreFilter("drama", "Drama"),
    GenreFilter("dungeons", "Dungeons"),
    GenreFilter("emperor_daughte", "Emperor's Daughter"),
    GenreFilter("fantasy", "Fantasy"),
    GenreFilter("fan_colored", "Fan-Colored"),
    GenreFilter("fetish", "Fetish"),
    GenreFilter("full_color", "Full Color"),
    GenreFilter("game", "Game"),
    GenreFilter("gender_bender", "Gender Bender"),
    GenreFilter("genderswap", "Genderswap"),
    GenreFilter("ghosts", "Ghosts"),
    GenreFilter("gyaru", "Gyaru"),
    GenreFilter("harem", "Harem"),
    GenreFilter("harlequin", "Harlequin"),
    GenreFilter("historical", "Historical"),
    GenreFilter("horror", "Horror"),
    GenreFilter("incest", "Incest"),
    GenreFilter("isekai", "Isekai"),
    GenreFilter("kids", "Kids"),
    GenreFilter("loli", "Loli"),
    GenreFilter("magic", "Magic"),
    GenreFilter("magical_girls", "Magical Girls"),
    GenreFilter("martial_arts", "Martial Arts"),
    GenreFilter("mecha", "Mecha"),
    GenreFilter("medical", "Medical"),
    GenreFilter("military", "Military"),
    GenreFilter("monster_girls", "Monster Girls"),
    GenreFilter("monsters", "Monsters"),
    GenreFilter("music", "Music"),
    GenreFilter("mystery", "Mystery"),
    GenreFilter("netorare", "Netorare/NTR"),
    GenreFilter("ninja", "Ninja"),
    GenreFilter("office_workers", "Office Workers"),
    GenreFilter("omegaverse", "Omegaverse"),
    GenreFilter("parody", "Parody"),
    GenreFilter("philosophical", "Philosophical"),
    GenreFilter("police", "Police"),
    GenreFilter("post_apocalyptic", "Post-Apocalyptic"),
    GenreFilter("psychological", "Psychological"),
    GenreFilter("regression", "Regression"),
    GenreFilter("reincarnation", "Reincarnation"),
    GenreFilter("reverse_harem", "Reverse Harem"),
    GenreFilter("reverse_isekai", "Reverse Isekai"),
    GenreFilter("romance", "Romance"),
    GenreFilter("royal_family", "Royal Family"),
    GenreFilter("royalty", "Royalty"),
    GenreFilter("samurai", "Samurai"),
    GenreFilter("school_life", "School Life"),
    GenreFilter("sci_fi", "Sci-Fi"),
    GenreFilter("shota", "Shota"),
    GenreFilter("shoujo_ai", "Shoujo Ai"),
    GenreFilter("shounen_ai", "Shounen Ai"),
    GenreFilter("showbiz", "Showbiz"),
    GenreFilter("slice_of_life", "Slice of Life"),
    GenreFilter("sm_bdsm", "SM/BDSM/SUB-DOM"),
    GenreFilter("space", "Space"),
    GenreFilter("sports", "Sports"),
    GenreFilter("super_power", "Super Power"),
    GenreFilter("superhero", "Superhero"),
    GenreFilter("supernatural", "Supernatural"),
    GenreFilter("survival", "Survival"),
    GenreFilter("thriller", "Thriller"),
    GenreFilter("time_travel", "Time Travel"),
    GenreFilter("tower_climbing", "Tower Climbing"),
    GenreFilter("traditional_games", "Traditional Games"),
    GenreFilter("tragedy", "Tragedy"),
    GenreFilter("transmigration", "Transmigration"),
    GenreFilter("vampires", "Vampires"),
    GenreFilter("villainess", "Villainess"),
    GenreFilter("video_games", "Video Games"),
    GenreFilter("virtual_reality", "Virtual Reality"),
    GenreFilter("wuxia", "Wuxia"),
    GenreFilter("xianxia", "Xianxia"),
    GenreFilter("xuanhuan", "Xuanhuan"),
    GenreFilter("zombies", "Zombies"),
)
