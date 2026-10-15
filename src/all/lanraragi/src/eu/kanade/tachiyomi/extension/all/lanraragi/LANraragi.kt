package eu.kanade.tachiyomi.extension.all.lanraragi

import android.content.SharedPreferences
import android.net.Uri
import android.text.InputType
import android.util.Base64
import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL
import kotlin.math.max

@Source
class LANraragi(
    override val lang: String,
    override val id: Long,
) : KeiSource(),
    ConfigurableSource,
    UnmeteredSource {
    override val baseUrl by lazy { getPrefBaseUrl() }

    override val name by lazy {
        val label = getPrefCustomLabel().ifBlank { instanceNumber.toString() }
        "LANraragi ($label)"
    }

    // Distinguish the fixed factory instances (LANraragi (1), LANraragi (2), ...) by position,
    // inferred from the source id, when the user hasn't set a custom label.
    private val instanceNumber: Int
        get() = (INSTANCE_IDS.indexOf(id) + 1).coerceAtLeast(1)

    private val apiKey by lazy { getPrefAPIKey() }

    private val latestNamespacePref by lazy { getPrefLatestNS() }

    private val latestSortOrderPref by lazy { getPrefLatestSortOrder() }

    private val randomPageSizePref by lazy { getPrefRandomPageSize() }

    private var randomArchiveID: String = ""

    override fun OkHttpClient.Builder.configureClient() = dns(Dns.SYSTEM)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) throw IOException("If the server is in No-Fun Mode make sure the extension's API Key is correct.")
            response
        }

    override fun Headers.Builder.configureHeaders() = apply {
        if (apiKey.isNotEmpty()) {
            val apiKey64 = Base64.encodeToString(apiKey.toByteArray(), Base64.NO_WRAP)
            add("Authorization", "Bearer $apiKey64")
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = mutableListOf<Filter<*>>()
        val prefNewOnly = preferences.getBoolean(NEW_ONLY_KEY, NEW_ONLY_DEFAULT)

        if (prefNewOnly) filters.add(NewArchivesOnly(true))

        if (latestNamespacePref.isNotBlank()) {
            filters.add(SortByNamespace(latestNamespacePref))
        }

        filters.add(SortSelect(sortOrders.filter { it.first == latestSortOrderPref }.toTypedArray()))

        return getSearchMangaList(page, "", FilterList(filters))
    }

    private var lastResultCount: Int = 0
    private var lastRecordsFiltered: Int = 0
    private var maxResultCount: Int = 0
    private var totalRecords: Int = 0

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val uri = getApiUriBuilder("/api/search")
        var startPageOffset = 0
        val isRandom = filters.firstInstanceOrNull<SortSelect>()?.toUriPart() == "random"

        if (page == 1) {
            lastResultCount = 0
        }

        filters.forEach { filter ->
            when (filter) {
                is StartingPage -> {
                    startPageOffset = filter.state.toIntOrNull() ?: 1

                    // Exception for API wrapping around and user input of 0
                    if (startPageOffset > 0) {
                        startPageOffset -= 1
                    }
                }

                is NewArchivesOnly -> if (filter.state) uri.appendQueryParameter("newonly", "true")

                is UntaggedArchivesOnly -> if (filter.state) uri.appendQueryParameter("untaggedonly", "true")

                is HideCompleted -> if (filter.state) uri.appendQueryParameter("hidecompleted", "true")

                is GroupByTanks -> uri.appendQueryParameter("groupby_tanks", filter.state.toString())

                is SortByNamespace -> if (!isRandom && filter.state.isNotEmpty()) uri.appendQueryParameter("sortby", filter.state.trim())

                is CategorySelect -> if (filter.state > 0) uri.appendQueryParameter("category", filter.toUriPart())

                is SortSelect -> {
                    if (filter.toUriPart() == "random") {
                        uri.appendPath("random")
                        uri.appendQueryParameter("count", randomPageSizePref)
                    } else {
                        uri.appendQueryParameter("order", filter.toUriPart())
                    }
                }

                else -> {}
            }
        }

        val currentStart = (page - 1 + startPageOffset) * lastResultCount
        uri.appendQueryParameter("start", currentStart.toString())

        if (query.isNotEmpty()) {
            uri.appendQueryParameter("filter", query)
        }

        val searchUrl = uri.toString().toHttpUrl()
        val jsonResult = client.get(searchUrl, headers, CacheControl.FORCE_NETWORK).parseAs<ArchiveSearchResult>()
        val archives = arrayListOf<SManga>()

        lastResultCount = jsonResult.data.size
        maxResultCount = max(lastResultCount, maxResultCount)
        lastRecordsFiltered = jsonResult.recordsFiltered ?: -2
        totalRecords = jsonResult.recordsTotal

        var hasNext = isRandom || currentStart + lastResultCount < lastRecordsFiltered

        // Random has no paging; applying the filter again gives a new batch
        if (isRandom && page > 1 && !preferences.getBoolean(REDUPE_KEY, REDUPE_DEFAULT)) {
            hasNext = false
        }

        if (page == 1 && lastResultCount > 1 && preferences.getBoolean(RANDOM_ENTRY_SHOW_KEY, RANDOM_ENTRY_SHOW_DEFAULT)) {
            val randQuery = searchUrl.query.toString()
            randomArchiveID = getRandomID(randQuery)

            archives.add(
                SManga.create().apply {
                    url = "/api/search/random?count=1&$randQuery"
                    title = "Random"
                    description = "Refresh for a random archive."
                    thumbnail_url = getThumbnailUri("0".repeat(40))
                },
            )
        }

        jsonResult.data.forEach {
            archives.add(archiveToSManga(it, isRandom && page > 1))
        }

        return MangasPage(archives, hasNext)
    }

    private fun archiveToSManga(archive: Archive, isRandom: Boolean = false) = SManga.create().apply {
        url = "/reader?id=${archive.arcid}"
        if (isRandom && preferences.getBoolean(REDUPE_KEY, REDUPE_DEFAULT)) url += "&ts" + System.currentTimeMillis()
        title = archive.title
        description = if (archive.summary.isNullOrBlank()) archive.title else archive.summary
        thumbnail_url = getThumbnailUri(archive.arcid)
        genre = archive.tags?.replace(",", ", ")
        artist = getNSTag(archive.tags, "artist")?.joinToString()
        author = getNSTag(archive.tags, "group")?.joinToString() ?: artist
        status = SManga.COMPLETED
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = if (manga.url.startsWith("/api/search/random")) randomArchiveID else getIDFromURL(manga.url)

        val response = client.get(apiTypeByID(id).toString())

        val (archive, archives) = if (!id.startsWith("TANK_")) {
            val archive = response.parseAs<Archive>()
            archive to listOf(archive)
        } else {
            val tank = response.parseAs<Tankoubon>()

            // The separators are not the default ", " to merge properly when combining across multiple archives: ",tag:x" vs ", tag:x"
            val tags = tank.result?.full_data?.joinToString(",") { it.tags!! }?.split(",")?.sorted()?.joinToString(",")

            Archive(
                arcid = tank.result!!.id,
                isnew = false,
                tags = tags,
                summary = tank.result.summary,
                title = tank.result.name!!,
                toc = emptyList(),
                pagecount = 0,
            ) to tank.result.full_data
        }

        val updatedManga = archiveToSManga(archive).apply { url = manga.url }

        if (manga.url.startsWith("/api/search/random")) {
            randomArchiveID = getRandomID(Uri.parse(manga.url).encodedQuery.toString())
            updatedManga.description = "Refresh for a new random entry.\n\n${updatedManga.description}"
        }

        return SMangaUpdate(updatedManga, parseChapters(archives))
    }

    override fun getMangaUrl(manga: SManga): String {
        val defaultUrl = "$baseUrl/reader?id=${getIDFromURL(manga.thumbnail_url!!)}"
        val namespace = preferences.getString(URL_TAG_PREFIX_KEY, URL_TAG_PREFIX_DEFAULT)

        if (namespace.isNullOrEmpty()) {
            return defaultUrl
        }

        val tag = manga.genre?.split(", ")?.find { it.startsWith(namespace) }
        return tag?.substringAfter(namespace) ?: defaultUrl
    }

    private suspend fun parseChapters(archives: List<Archive>?): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // Legacy extension-exclusive behavior to remove isnew on single archives when viewing
        val prefClearNew = preferences.getBoolean(CLEAR_NEW_KEY, CLEAR_NEW_DEFAULT)
        if (prefClearNew && archives?.size == 1 && archives[0].isnew) {
            val clearNew = Request.Builder()
                .url("$baseUrl/api/archives/${archives[0].arcid}/isnew")
                .headers(headers)
                .delete()
                .build()

            client.newCall(clearNew).await().close()
        }

        var baseChapter = 0F

        // Supports single, single+ToC, tank, tank+ToC...
        archives?.forEach { arc ->
            baseChapter += 1F

            val baseFiles = getApiUriBuilder("/api/archives/${arc.arcid}/files").build().toString()
            val date = 1000 * (getNSTag(arc.tags, "date_added")?.first()?.toLong() ?: 0)

            if (arc.toc?.isEmpty() == false) {
                var lastStart = 0

                val toc = buildList {
                    if (arc.toc.first().page > 1) add(ArchiveTOCEntry(arc.title, 1)) // Starting gap filler
                    addAll(arc.toc)
                }

                toc.forEachIndexed { i, entry ->
                    val nextPage = if (i + 1 < toc.size) toc[i + 1].page - 1 else arc.pagecount

                    chapters.add(
                        SChapter.create().apply {
                            url = "$baseFiles#$lastStart-$nextPage"
                            chapter_number = baseChapter + "0.${i + 1}".toFloat()
                            name = "$chapter_number - ${entry.name}"
                            date_upload = date
                        },
                    )

                    lastStart = nextPage
                }
            } else {
                chapters.add(
                    SChapter.create().apply {
                        url = baseFiles
                        chapter_number = baseChapter
                        name = if (archives.size == 1) "Chapter" else "${chapter_number.toInt()} - ${arc.title}"
                        date_upload = date
                    },
                )
            }
        }
        chapters.reverse() // For tanks orders them in "latest first"

        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = chapter.url.toHttpUrl()
        val archivePage = client.get(url).parseAs<ArchivePage>()
        val range = url.fragment?.split("-")?.map { it.toInt() } ?: listOf(0, archivePage.pages.size)

        return archivePage.pages.subList(range.first(), range.last()).mapIndexed { index, url ->
            var newUrl = url
            val subPath = URL(baseUrl).path
            if (!subPath.isNullOrEmpty()) {
                newUrl = newUrl.replaceFirst(subPath, "")
            }
            val uri = Uri.parse("${baseUrl}${newUrl.trimStart('.')}")
            Page(index, uri.toString(), uri.toString(), uri)
        }
    }

    private class CategorySelect(categories: Array<Pair<String, String>>) : UriPartFilter("Category", categories)
    private class SortSelect(sortOrders: Array<Pair<String, String>>) : UriPartFilter("Sort order", sortOrders)
    private class NewArchivesOnly(overrideState: Boolean = false) : Filter.CheckBox("New Archives only", overrideState)
    private class UntaggedArchivesOnly : Filter.CheckBox("Untagged Archives only", false)
    private class HideCompleted : Filter.CheckBox("Hide Completed", false)
    private class GroupByTanks : Filter.CheckBox("Group by Tankoubon", true)
    private class StartingPage(stats: String) : Filter.Text("Starting page$stats", "")
    private class SortByNamespace(defaultText: String = "") : Filter.Text("Sort by (namespace)", defaultText)

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/categories")
        .parseAs<List<Category>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategorySelect(getCategoryPairs(data?.parseAs<List<Category>>().orEmpty())),
        SortSelect(sortOrders),
        NewArchivesOnly(),
        UntaggedArchivesOnly(),
        HideCompleted(),
        GroupByTanks(),
        StartingPage(startingPageStats()),
        SortByNamespace(),
    )

    private val sortOrders = arrayOf(Pair("asc", "Ascending"), Pair("desc", "Descending"), Pair("random", "Random"))

    // Preferences
    internal val preferences: SharedPreferences by getPreferencesLazy()

    private fun getPrefBaseUrl(): String = preferences.getString(HOSTNAME_KEY, HOSTNAME_DEFAULT)!!
    private fun getPrefAPIKey(): String = preferences.getString(APIKEY_KEY, "")!!
    private fun getPrefLatestNS(): String = preferences.getString(SORT_BY_NS_KEY, SORT_BY_NS_DEFAULT)!!
    private fun getPrefLatestSortOrder(): String = preferences.getString(SORT_ORDER_KEY, SORT_ORDER_DEFAULT)!!
    private fun getPrefRandomPageSize(): String = preferences.getString(RANDOM_SIZE_KEY, RANDOM_SIZE_DEFAULT)!!
    private fun getPrefCustomLabel(): String = preferences.getString(CUSTOM_LABEL_KEY, "")!!

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val randomPageSize = ListPreference(screen.context).apply {
            key = RANDOM_SIZE_KEY
            title = "Random Sort - Pagination amount"
            entries = arrayOf("25", "50", "100", "250", "1000")
            entryValues = entries
            setDefaultValue(RANDOM_SIZE_DEFAULT)
            summary = "Request %s entries at a time in Random sort order. Lower may be more responsive while higher may be less disruptive.\n\n\"Ignore dedupe\" must be enabled for infinite/doomscrolling."

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart app to apply new setting.", Toast.LENGTH_LONG).show()
                true
            }
        }

        val latestSortOrder = ListPreference(screen.context).apply {
            key = SORT_ORDER_KEY
            title = "Latest - Default Sort Order"
            entries = sortOrders.map { it.second }.toTypedArray()
            entryValues = sortOrders.map { it.first }.toTypedArray()
            setDefaultValue(SORT_ORDER_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart app to apply new setting.", Toast.LENGTH_LONG).show()
                true
            }
        }

        screen.addPreference(screen.editTextPreference(HOSTNAME_KEY, "Hostname", HOSTNAME_DEFAULT, baseUrl, refreshSummary = true))
        screen.addPreference(screen.editTextPreference(APIKEY_KEY, "API Key", "", "Required if No-Fun Mode is enabled.", true))
        screen.addPreference(screen.editTextPreference(CUSTOM_LABEL_KEY, "Custom Label", "", "Show the given label for the source instead of the default."))
        screen.addPreference(screen.checkBoxPreference(RANDOM_ENTRY_SHOW_KEY, "Show Random entry", RANDOM_ENTRY_SHOW_DEFAULT, "The first item of a search will be a special entry for a single random archive. Pull down to \"refresh\" and get a new one.\n\nLibrary functionality is limited. It is recommend to start a search within this entry to get to the original."))
        screen.addPreference(screen.checkBoxPreference(CLEAR_NEW_KEY, "Clear New status", CLEAR_NEW_DEFAULT, "Clear an entry's New status when its details are viewed."))
        screen.addPreference(screen.checkBoxPreference(NEW_ONLY_KEY, "Latest - New Only", NEW_ONLY_DEFAULT))
        screen.addPreference(screen.editTextPreference(SORT_BY_NS_KEY, "Latest - Sort by Namespace", SORT_BY_NS_DEFAULT, "Sort by the given namespace for Latest, such as date_added or lastread."))
        screen.addPreference(latestSortOrder)
        screen.addPreference(randomPageSize)
        screen.addPreference(screen.checkBoxPreference(REDUPE_KEY, "Random Sort - Ignore dedupe", REDUPE_DEFAULT, "If enabled, ignores app's enforced deduping at the cost of spamming its database and library functionality. If disabled, there will only be one request of the pagination amount."))
        screen.addPreference(screen.editTextPreference(URL_TAG_PREFIX_KEY, "Set tag prefix to get WebView URL", URL_TAG_PREFIX_DEFAULT, "Example: 'source:' will try to get the URL from the first tag starting with 'source:' and it will open it in the WebView. Leave empty for the default behavior."))
    }

    private fun androidx.preference.PreferenceScreen.checkBoxPreference(key: String, title: String, default: Boolean, summary: String = ""): androidx.preference.CheckBoxPreference = androidx.preference.CheckBoxPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        setDefaultValue(default)

        setOnPreferenceChangeListener { _, newValue ->
            preferences.edit().putBoolean(this.key, newValue as Boolean).commit()
        }
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(key: String, title: String, default: String, summary: String, isPassword: Boolean = false, refreshSummary: Boolean = false): androidx.preference.EditTextPreference = androidx.preference.EditTextPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.setDefaultValue(default)
        setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }

        if (isPassword) {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        setOnPreferenceChangeListener { _, newValue ->
            try {
                val newString = newValue.toString()
                val res = preferences.edit().putString(this.key, newString).commit()

                if (refreshSummary) {
                    this.apply {
                        this.summary = newValue as String
                    }
                }

                Toast.makeText(context, "Restart app to apply new setting.", Toast.LENGTH_LONG).show()
                res
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Helper
    private fun apiTypeByID(id: String): Uri = getApiUriBuilder(
        if (id.startsWith("TANK_")) {
            "/api/tankoubons/$id/full"
        } else {
            "/api/archives/$id/metadata"
        },
    ).build()

    private suspend fun getRandomID(query: String): String {
        val searchRandom = client.get("$baseUrl/api/search/random?count=1&$query", headers, CacheControl.FORCE_NETWORK)
        val result = searchRandom.parseAs<ArchiveSearchResult>() // Intermittent empty data[] on parse, but not from manual API testing
        return result.data.firstOrNull()?.arcid ?: randomArchiveID
    }

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private fun getCategoryPairs(categories: List<Category>): Array<Pair<String, String>> {
        // Empty pair to disable. Sort by pinned status then name for convenience.

        val pin = "📌 "

        return listOf(Pair("", ""))
            .plus(
                categories
                    .sortedWith(compareByDescending<Category> { it.pinned }.thenBy { it.name })
                    .map {
                        val pinned = if (it.pinned == 1) pin else ""
                        Pair(it.id, "$pinned${it.name}")
                    },
            )
            .toTypedArray()
    }

    private fun startingPageStats(): String = if (maxResultCount > 0 && totalRecords > 0) " ($maxResultCount / $lastRecordsFiltered items)" else ""

    private fun getApiUriBuilder(path: String): Uri.Builder = Uri.parse("$baseUrl$path").buildUpon()

    private fun getThumbnailUri(id: String): String {
        val type = if (id.startsWith("TANK_")) "tankoubons" else "archives"
        val uri = getApiUriBuilder("/api/$type/$id/thumbnail")

        return uri.toString()
    }

    private fun getIDFromURL(url: String): String = REGEX_ID_FROM_URL.find(url)?.groupValues?.get(1) ?: ""

    private fun getNSTag(tags: String?, tag: String): List<String>? = tags?.split(',')
        ?.filter { it.startsWith("$tag:") }
        ?.map { it.split(":", limit = 2).last() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }

    companion object {

        private val REGEX_ID_FROM_URL = Regex("""(?:/reader\?id=)?(TANK_[0-9]{10}|\w{40})(?:/thumbnail)?""")

        private const val HOSTNAME_DEFAULT = "http://127.0.0.1:3000"
        private const val HOSTNAME_KEY = "hostname"
        private const val APIKEY_KEY = "apiKey"
        private const val CUSTOM_LABEL_KEY = "customLabel"

        // Order must match the source { } blocks in build.gradle.kts (used to label factory instances).
        private val INSTANCE_IDS = listOf(4482480338677079857L, 6188058704030343819L)

        // mihonapp/mihon#2176
        private const val REDUPE_KEY = "redupePref"
        private const val REDUPE_DEFAULT = false
        private const val NEW_ONLY_DEFAULT = true
        private const val NEW_ONLY_KEY = "latestNewOnly"
        private const val SORT_BY_NS_DEFAULT = "date_added"
        private const val SORT_BY_NS_KEY = "latestNamespacePref"
        private const val SORT_ORDER_DEFAULT = "desc"
        private const val SORT_ORDER_KEY = "latestSortOrder"
        private const val RANDOM_ENTRY_SHOW_KEY = "showRandomEntry"
        private const val RANDOM_ENTRY_SHOW_DEFAULT = true
        private const val RANDOM_SIZE_DEFAULT = "100"
        private const val RANDOM_SIZE_KEY = "randomPageSize"
        private const val CLEAR_NEW_KEY = "clearNew"
        private const val CLEAR_NEW_DEFAULT = true
        private const val URL_TAG_PREFIX_KEY = "urlTagPrefix"
        private const val URL_TAG_PREFIX_DEFAULT = ""
    }
}
