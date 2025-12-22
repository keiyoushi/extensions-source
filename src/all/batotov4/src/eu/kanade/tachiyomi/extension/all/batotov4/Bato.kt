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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Bato(
    private val sourceLang: String,
    private val siteLang: String,
) : ConfigurableSource, HttpSource() {

    override val name = "Bato.to (v4)"
    override val baseUrl: String get() = getMirrorPref()
    override val lang = sourceLang
    override val supportsLatest = true
    private val encodedSiteLang = siteLang
        .takeIf { it.isNotBlank() }
        ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

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
                "To update existing entries, remove them from your library (unfavorite) and refresh manually."
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

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Global preferences"),
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
        GET(withLang("$baseUrl/comics?page=$page"), headers)

    override fun popularMangaParse(response: Response): MangasPage =
        parseMangaList(response.asJsoup(), pageFromResponse(response))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            urlBuilder.addQueryParameter("word", trimmedQuery)
        }
        urlBuilder.addQueryParameter("page", page.toString())
        applyFilters(urlBuilder, filters)
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        parseMangaList(response.asJsoup(), pageFromResponse(response))

    override fun latestUpdatesRequest(page: Int): Request =
        GET(withLang("$baseUrl/v4x-latest?page=$page"), headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseMangaList(response.asJsoup(), pageFromResponse(response))

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(absoluteUrl(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga =
        parseMangaDetails(response.asJsoup())

    override fun chapterListRequest(manga: SManga): Request =
        GET(absoluteUrl(manga.url), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        if (getAltChapterListPref()) {
            val alt = parseAltChapterList(document)
            if (alt.isNotEmpty()) return alt
        }
        return parseChapterList(document)
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(absoluteUrl(chapter.url), headers)

    override fun pageListParse(response: Response): List<Page> =
        parsePageList(response.asJsoup())

    override fun imageUrlParse(response: Response): String = ""

    private fun parseMangaList(document: Document, page: Int): MangasPage {
        val objs = parseQwikObjs(document) ?: return MangasPage(emptyList(), false)
        val cache = mutableMapOf<Int, Any?>()
        val mangaList = mutableListOf<SManga>()
        var rawCount = 0

        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            if (!obj.has("urlPath") || !obj.has("name")) continue

            val resolved = resolveQwikObject(obj, objs, cache)
            val urlPath = asString(resolved.opt("urlPath")) ?: continue
            if (!urlPath.startsWith("/title/")) continue
            rawCount++
            if (!obj.has("urlCover600") && !obj.has("urlCover300") && !obj.has("urlCover")) continue
            val title = (asString(resolved.opt("name")) ?: continue).cleanTitleIfNeeded()
            val cover = firstNonBlank(
                asString(resolved.opt("urlCover600")),
                asString(resolved.opt("urlCover300")),
                asString(resolved.opt("urlCover")),
            )

            mangaList.add(
                SManga.create().apply {
                    this.title = title
                    url = urlPath
                    thumbnail_url = absoluteUrlOrNull(cover)
                },
            )
        }

        val pagingInfo = findPagingInfo(objs, cache, page)
        val pageSize = findPageSize(objs, cache, page)
        val deduped = mangaList.distinctBy { it.url }
        val hasNextPage = when {
            pagingInfo?.next != null -> pagingInfo.next > 0
            pagingInfo?.page != null && pagingInfo.pages != null -> pagingInfo.page < pagingInfo.pages
            pageSize != null -> rawCount >= pageSize
            else -> deduped.isNotEmpty()
        }
        return MangasPage(deduped, hasNextPage)
    }

    private fun parseMangaDetails(document: Document): SManga {
        val details = SManga.create()
        val objs = parseQwikObjs(document)
        val cache = mutableMapOf<Int, Any?>()

        val resolved = if (objs != null) findComicDetails(objs, cache) else null
        if (resolved != null) {
            val originalTitle = asString(resolved.opt("name")) ?: ""
            details.title = originalTitle.cleanTitleIfNeeded()
            val cover = firstNonBlank(
                asString(resolved.opt("urlCover600")),
                asString(resolved.opt("urlCover300")),
                asString(resolved.opt("urlCover")),
            )
            details.thumbnail_url = absoluteUrlOrNull(cover)
            details.description = firstNonBlank(
                asString(resolved.opt("summary")),
                asString(resolved.opt("description")),
            )
            val genres = extractStringList(resolved.opt("genres")).joinToString()
            if (genres.isNotBlank()) details.genre = genres
            val authors = extractStringList(resolved.opt("authors")).joinToString()
            if (authors.isNotBlank()) details.author = authors
            val artists = extractStringList(resolved.opt("artists")).joinToString()
            if (artists.isNotBlank()) details.artist = artists
            val statusText = firstNonBlank(
                asString(resolved.opt("statusName")),
                asString(resolved.opt("status")),
            )
            details.status = parseStatus(statusText)
        }

        val ogTitle = document.selectFirst("meta[property=og:title]")?.attr("content")
        val ogImage = document.selectFirst("meta[property=og:image]")?.attr("content")
        val ogDescription = document.selectFirst("meta[property=og:description]")?.attr("content")
        if (details.title.isBlank()) {
            details.title = (ogTitle ?: document.selectFirst("title")?.text()?.substringBefore("|")?.trim().orEmpty())
                .cleanTitleIfNeeded()
        }
        if (details.thumbnail_url.isNullOrBlank()) {
            details.thumbnail_url = ogImage
        }
        if (details.description.isNullOrBlank()) {
            details.description = ogDescription
        }
        val originalTitle = asString(resolved?.opt("name"))?.trim().orEmpty()
        if (originalTitle.isNotBlank() && originalTitle != details.title) {
            details.description = listOfNotNull(originalTitle, details.description)
                .joinToString("\n\n")
        }
        details.initialized = true
        return details
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val objs = parseQwikObjs(document) ?: return emptyList()
        val cache = mutableMapOf<Int, Any?>()
        val chapters = mutableListOf<SChapter>()

        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            if (!obj.has("urlPath") || !obj.has("dname")) continue

            val resolved = resolveQwikObject(obj, objs, cache)
            val urlPath = asString(resolved.opt("urlPath")) ?: continue
            if (!urlPath.contains("/title/")) continue
            val name = asString(resolved.opt("dname")) ?: continue
            val dateCreate = asLong(resolved.opt("dateCreate")) ?: 0L

            chapters.add(
                SChapter.create().apply {
                    url = urlPath
                    this.name = name
                    date_upload = dateCreate
                },
            )
        }

        return chapters
            .distinctBy { it.url }
            .sortedByDescending { it.date_upload }
    }

    private fun parsePageList(document: Document): List<Page> {
        val objs = parseQwikObjs(document) ?: return emptyList()
        val urls = mutableListOf<String>()

        for (i in 0 until objs.length()) {
            val value = objs.opt(i) as? String ?: continue
            if (!isPageImageUrl(value)) continue
            urls.add(normalizeImageUrl(absoluteUrl(value)))
        }

        return urls.distinct().mapIndexed { i, url -> Page(i, "", url) }
    }

    private fun parseAltChapterList(document: Document): List<SChapter> {
        val seriesId = SERIES_ID_REGEX.find(document.location())?.groups?.get(1)?.value
        val chapters = mutableListOf<SChapter>()
        val seen = mutableSetOf<String>()

        for (link in document.select("a[href*=\"/title/\"]")) {
            val rawUrl = link.attr("href")
            val path = extractPath(rawUrl) ?: continue
            val segments = path.trim('/').split('/')
            if (segments.size < 3 || segments[0] != "title") continue
            if (seriesId != null && segments[1] != seriesId) continue
            val slug = segments[2]
            if (slug.isBlank()) continue

            val chapterUrl = "/title/${segments[1]}/$slug"
            if (!seen.add(chapterUrl)) continue

            val name = firstNonBlank(
                link.attr("title").trim(),
                link.attr("aria-label").trim(),
                link.text().trim(),
            ) ?: continue

            chapters.add(
                SChapter.create().apply {
                    url = chapterUrl
                    this.name = name
                },
            )
        }

        return chapters
    }

    private fun parseQwikObjs(document: Document): JSONArray? {
        val scripts = document.select("script[type=qwik/json]")
        for (script in scripts) {
            val jsonText = script.html().trim()
            if (jsonText.isEmpty()) continue
            val root = try {
                JSONObject(jsonText)
            } catch (e: Exception) {
                continue
            }
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

    private fun findComicDetails(objs: JSONArray, cache: MutableMap<Int, Any?>): JSONObject? {
        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            if (!obj.has("urlPath") || !obj.has("name")) continue

            val resolved = resolveQwikObject(obj, objs, cache)
            val urlPath = asString(resolved.opt("urlPath")) ?: continue
            if (!urlPath.startsWith("/title/")) continue
            if (resolved.has("urlCover600") || resolved.has("urlCover300") || resolved.has("urlCover")) {
                return resolved
            }
        }
        return null
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
            is JSONObject -> asString(value.opt("name"))
            else -> null
        }
    }

    private fun asString(value: Any?): String? {
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

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun findPageSize(objs: JSONArray, cache: MutableMap<Int, Any?>, page: Int): Int? {
        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            if (!obj.has("page") || !obj.has("size")) continue
            val resolved = resolveQwikObject(obj, objs, cache)
            val size = asLong(resolved.opt("size"))?.toInt()
            val pageValue = asLong(resolved.opt("page"))?.toInt()
            if (size != null && (pageValue == null || pageValue == page)) {
                return size
            }
        }
        return null
    }

    private fun findPagingInfo(objs: JSONArray, cache: MutableMap<Int, Any?>, page: Int): PagingInfo? {
        for (i in 0 until objs.length()) {
            val obj = objs.optJSONObject(i) ?: continue
            val resolved = resolveQwikObject(obj, objs, cache)
            val pagingObj = when {
                resolved.has("paging") -> resolved.optJSONObject("paging")
                resolved.has("pages") || resolved.has("total") -> resolved
                else -> null
            } ?: continue

            val pageValue = asLong(pagingObj.opt("page"))?.toInt()
            val pages = asLong(pagingObj.opt("pages"))?.toInt()
            val next = asLong(pagingObj.opt("next"))?.toInt()
            if (pageValue != null && pages != null && pageValue == page) {
                return PagingInfo(pageValue, pages, next)
            }
            if (pageValue == null && pages != null) {
                return PagingInfo(pageValue, pages, next)
            }
        }
        return null
    }

    private fun parseStatus(status: String?): Int {
        val normalized = status.orEmpty()
        return when {
            normalized.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
            normalized.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
            normalized.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
            normalized.contains("Cancelled", ignoreCase = true) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun pageFromResponse(response: Response): Int {
        return response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
    }

    private fun isPageImageUrl(url: String): Boolean {
        val marker = "/media/"
        val idx = url.indexOf(marker)
        if (idx < 0) return false
        val after = url.substring(idx + marker.length)
        return after.startsWith("mbch/") || after.firstOrNull()?.isDigit() == true
    }

    private fun withLang(url: String): String {
        val langValue = encodedSiteLang ?: return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}lang=$langValue"
    }

    private fun absoluteUrl(url: String): String =
        if (url.startsWith("http")) url else "$baseUrl$url"

    private fun normalizeImageUrl(url: String): String {
        return when {
            url.startsWith("https://k") -> "https://n" + url.removePrefix("https://k")
            url.startsWith("http://k") -> "http://n" + url.removePrefix("http://k")
            else -> url
        }
    }

    private fun getMirrorPref(): String =
        preferences.getString("${MIRROR_PREF_KEY}_$lang", MIRROR_PREF_DEFAULT_VALUE) ?: MIRROR_PREF_DEFAULT_VALUE

    private fun getAltChapterListPref(): Boolean =
        preferences.getBoolean("${ALT_CHAPTER_LIST_PREF_KEY}_$lang", ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE)

    private fun isRemoveTitleVersion(): Boolean =
        preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)

    private fun customRemoveTitle(): String =
        preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "").orEmpty()

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

    private fun applyFilters(urlBuilder: okhttp3.HttpUrl.Builder, filters: FilterList) {
        val includeGenres = mutableListOf<String>()
        val excludeGenres = mutableListOf<String>()
        var translatedLangs: List<String> = emptyList()
        var originalLangs: List<String> = emptyList()
        var originalStatus: String? = null
        var uploadStatus: String? = null
        var chapterCount: String? = null
        var ignoreLangs = false
        var ignoreGenres = false

        filters.forEach { filter ->
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

        val langParam = when {
            translatedLangs.isNotEmpty() -> translatedLangs.joinToString(",")
            siteLang.isNotBlank() -> siteLang
            else -> null
        }
        if (langParam != null) {
            urlBuilder.addQueryParameter("lang", langParam)
        }
        if (originalLangs.isNotEmpty()) {
            urlBuilder.addQueryParameter("orig", originalLangs.joinToString(","))
        }
        if (includeGenres.isNotEmpty() || excludeGenres.isNotEmpty()) {
            val value = buildString {
                append(includeGenres.joinToString(","))
                if (excludeGenres.isNotEmpty()) {
                    append("|")
                    append(excludeGenres.joinToString(","))
                }
            }
            urlBuilder.addQueryParameter("genres", value)
        }
        if (originalStatus != null) {
            urlBuilder.addQueryParameter("status", originalStatus)
        }
        if (uploadStatus != null) {
            urlBuilder.addQueryParameter("upload", uploadStatus)
        }
        if (chapterCount != null) {
            urlBuilder.addQueryParameter("chapters", chapterCount)
        }
        if (ignoreLangs) {
            urlBuilder.addQueryParameter("ignoreGlobalULangs", "1")
        }
        if (ignoreGenres) {
            urlBuilder.addQueryParameter("ignoreGlobalGenres", "1")
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 36
        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror"
        private val MIRROR_PREF_ENTRIES = arrayOf("bato.si", "bato.ing")
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES.map { "https://$it" }.toTypedArray()
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]

        private const val ALT_CHAPTER_LIST_PREF_KEY = "ALT_CHAPTER_LIST"
        private const val ALT_CHAPTER_LIST_PREF_TITLE = "Alternative Chapter List"
        private const val ALT_CHAPTER_LIST_PREF_SUMMARY = "If checked, uses the HTML chapter list instead of Qwik data"
        private const val ALT_CHAPTER_LIST_PREF_DEFAULT_VALUE = false

        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"

        private val TITLE_REGEX: Regex =
            Regex(
                "\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official",
                RegexOption.IGNORE_CASE,
            )

        private val SERIES_ID_REGEX = Regex("/title/(\\d+)")

        val ORIGINAL_STATUS_VALUES = arrayOf(
            "",
            "pending",
            "ongoing",
            "completed",
            "hiatus",
            "cancelled",
        )

        val UPLOAD_STATUS_VALUES = arrayOf(
            "",
            "pending",
            "ongoing",
            "completed",
            "hiatus",
            "cancelled",
        )

        val CHAPTER_COUNT_VALUES = arrayOf(
            "",
            "0",
            "1",
            "10",
            "20",
            "30",
            "40",
            "50",
            "60",
            "70",
            "80",
            "90",
            "100",
            "200",
            "300",
            "1-9",
            "10-19",
            "20-29",
            "30-39",
            "40-49",
            "50-59",
            "60-69",
            "70-79",
            "80-89",
            "90-99",
            "100-199",
            "200-299",
        )
    }
}

private data class PagingInfo(val page: Int?, val pages: Int?, val next: Int?)

private class IgnoreLanguageFilter : Filter.CheckBox("Ignore my general language preferences")

private class IgnoreGenreFilter : Filter.CheckBox("Ignore my general genres blocking")

private class GenreFilter(val value: String, name: String) : Filter.TriState(name)

private class GenreGroupFilter : Filter.Group<GenreFilter>(
    "Genres",
    getGenreList(),
)

private class LanguageFilterOption(val value: String, name: String) : Filter.CheckBox(name)

private class OriginalLanguageFilter : Filter.Group<LanguageFilterOption>(
    "Original Work Language",
    getOriginalLanguageList(),
)

private class TranslatedLanguageFilter : Filter.Group<LanguageFilterOption>(
    "Translated Language",
    getTranslatedLanguageList(),
)

private class OriginalStatusFilter : Filter.Select<String>(
    "Original Work Status",
    arrayOf("Any", "Pending", "Ongoing", "Completed", "Hiatus", "Cancelled"),
)

private class UploadStatusFilter : Filter.Select<String>(
    "Bato Upload Status",
    arrayOf("Any", "Pending", "Ongoing", "Completed", "Hiatus", "Cancelled"),
)

private class ChapterCountFilter : Filter.Select<String>(
    "Number of chapters",
    arrayOf(
        "Any",
        "0",
        "1+",
        "10+",
        "20+",
        "30+",
        "40+",
        "50+",
        "60+",
        "70+",
        "80+",
        "90+",
        "100+",
        "200+",
        "300+",
        "1-9",
        "10-19",
        "20-29",
        "30-39",
        "40-49",
        "50-59",
        "60-69",
        "70-79",
        "80-89",
        "90-99",
        "100-199",
        "200-299",
    ),
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
