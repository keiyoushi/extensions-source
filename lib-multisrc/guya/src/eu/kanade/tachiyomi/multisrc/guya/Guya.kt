package eu.kanade.tachiyomi.multisrc.guya

import android.content.SharedPreferences
import android.os.Build
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.select.Evaluator
import rx.Observable
import rx.schedulers.Schedulers

abstract class Guya(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ConfigurableSource, HttpSource() {

    override val supportsLatest = true

    private val scanlatorCacheUrl by lazy { "$baseUrl/api/get_all_groups/" }

    override fun headersBuilder() = Headers.Builder().add(
        "User-Agent",
        "(Android ${Build.VERSION.RELEASE}; " +
            "${Build.MANUFACTURER} ${Build.MODEL}) " +
            "Tachiyomi/${AppInfo.getVersionName()} ${Build.ID}",
    )

    private val scanlators: ScanlatorStore = ScanlatorStore()

    // Preferences configuration
    private val preferences: SharedPreferences by getPreferencesLazy()

    // Request builder for the "browse" page of the manga
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/get_all_series/", headers)
    }

    // Gets the response object from the request
    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body.string()
        return parseManga(JSONObject(res))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return popularMangaRequest(page)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val payload = JSONObject(response.body.string())
        val mangas = sortedMapOf<Long, SManga>()

        for (series in payload.keys()) {
            val json = payload.getJSONObject(series)
            val timestamp = json.getLong("last_updated")
            mangas[timestamp] = parseMangaFromJson(json, series)
        }

        return MangasPage(mangas.values.reversed(), false)
    }

    // Overridden to use our overload
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response, manga)
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/series/${manga.url}/", headers)
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val res = response.body.string()
        return parseMangaFromJson(JSONObject(res), manga.title)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/reader/series/${manga.url}/"
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }

    // Gets the chapter list based on the series being viewed
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api/series/${manga.url}/", headers)
    }

    // Called after the request
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        return parseChapterList(response.body.string(), manga)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/read/manga/${chapter.url.replace('.', '-')}/1/"
    }

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, chapter)
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/series/${chapter.url.split("/")[0]}/", headers)
    }

    private fun pageListParse(response: Response, chapter: SChapter): List<Page> {
        val res = response.body.string()

        val json = JSONObject(res)
        val chapterNum = chapter.name.split(" - ")[0]
        val pages = json.getJSONObject("chapters")
            .getJSONObject(chapterNum)
            .getJSONObject("groups")
        val metadata = JSONObject()

        metadata.put("chapter", chapterNum)
        metadata.put("scanlator", scanlators.getKeyFromValue(chapter.scanlator ?: ""))
        metadata.put("slug", json.getString("slug"))
        metadata.put(
            "folder",
            json.getJSONObject("chapters")
                .getJSONObject(chapterNum)
                .getString("folder"),
        )

        return parsePageFromJson(pages, metadata)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(SLUG_PREFIX) -> {
                val slug = query.removePrefix(SLUG_PREFIX)
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response ->
                        searchMangaParseWithSlug(response, slug)
                    }
            }
            else -> {
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { response ->
                        searchMangaParse(response, query)
                    }
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/get_all_series/", headers)
    }

    protected open fun searchMangaParseWithSlug(response: Response, slug: String): MangasPage {
        val results = JSONObject(response.body.string())
        val truncatedJSON = JSONObject()

        for (mangaTitle in results.keys()) {
            val mangaDetails = results.getJSONObject(mangaTitle)

            if (mangaDetails.get("slug") == slug) {
                truncatedJSON.put(mangaTitle, mangaDetails)
            }
        }

        return parseManga(truncatedJSON)
    }

    protected open fun searchMangaParse(response: Response, query: String): MangasPage {
        val res = response.body.string()
        val json = JSONObject(res)
        val truncatedJSON = JSONObject()

        for (candidate in json.keys()) {
            if (candidate.contains(query, ignoreCase = true)) {
                truncatedJSON.put(candidate, json.get(candidate))
            }
        }

        return parseManga(truncatedJSON)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val scanlatorKeys = scanlators.keys().toTypedArray()

        val preference = ListPreference(screen.context).apply {
            key = "preferred_scanlator"
            title = "Preferred scanlator"
            entries = Array(scanlatorKeys.size) { scanlators.getValueFromKey(scanlatorKeys[it]) }
            entryValues = scanlatorKeys
            summary = "Current: %s\n\n" +
                "This setting sets the scanlation group to prioritize " +
                "on chapter refresh/update. It will get the next available if " +
                "your preferred scanlator isn't an option (yet)."

            setDefaultValue("1")

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue.toString()
                preferences.edit().putString(scanlatorPreference, selected).commit()
            }
        }

        screen.addPreference(preference)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String, manga: SManga): List<SChapter> {
        val sortKey = "preferred_sort"
        val response = JSONObject(payload)
        val chapters = response.getJSONObject("chapters")
        val mapping = response.getJSONObject("groups")

        val chapterList = mutableListOf<SChapter>()

        val iter = chapters.keys()

        while (iter.hasNext()) {
            val chapterNum = iter.next()
            val chapterObj = chapters.getJSONObject(chapterNum)
            when {
                chapterObj.has(sortKey) -> {
                    chapterList.add(
                        parseChapterFromJson(
                            chapterObj,
                            chapterNum,
                            chapterObj.getJSONArray(sortKey),
                            response.getString("slug"),
                        ),
                    )
                }
                response.has(sortKey) -> {
                    chapterList.add(
                        parseChapterFromJson(
                            chapterObj,
                            chapterNum,
                            response.getJSONArray(sortKey),
                            response.getString("slug"),
                        ),
                    )
                }
                else -> {
                    val groups = chapterObj.getJSONObject("groups")
                    val groupsIter = groups.keys()

                    while (groupsIter.hasNext()) {
                        val chapter = SChapter.create()
                        val groupNum = groupsIter.next()

                        chapter.scanlator = mapping.getString(groupNum)
                        if (chapterObj.has("release_date")) {
                            chapter.date_upload =
                                chapterObj.getJSONObject("release_date").getLong(groupNum) * 1000
                        }
                        chapter.name = chapterNum + " - " + chapterObj.getString("title")
                        chapter.chapter_number = chapterNum.toFloat()
                        chapter.url = "${manga.url}/$chapterNum"
                        chapterList.add(chapter)
                    }
                }
            }
        }

        return chapterList.reversed()
    }

    // Helper function to get all the listings
    private fun parseManga(payload: JSONObject): MangasPage {
        val mangas = mutableListOf<SManga>()

        for (series in payload.keys()) {
            val json = payload.getJSONObject(series)
            mangas += parseMangaFromJson(json, series)
        }

        return MangasPage(mangas, false)
    }

    // Takes a json of the manga to parse
    private fun parseMangaFromJson(json: JSONObject, title: String = ""): SManga {
        val manga = SManga.create()
        manga.title = title.ifEmpty { json.getString("title") }
        manga.artist = json.optString("artist")
        manga.author = json.optString("author")
        manga.description = json.optString("description").let {
            if ('<' !in it) return@let it // no HTML
            Jsoup.parseBodyFragment(it).body().run {
                select(Evaluator.Tag("a")).remove()
                text()
            }
        }
        manga.url = json.getString("slug")

        val cover = json.optString("cover")
        manga.thumbnail_url = when {
            cover.startsWith("http") -> cover
            cover.isNotEmpty() -> "$baseUrl/$cover"
            else -> null
        }

        return manga
    }

    private fun parseChapterFromJson(json: JSONObject, num: String, sort: JSONArray, slug: String): SChapter {
        val chapter = SChapter.create()

        // Get the scanlator info based on group ranking; do it first since we need it later
        val firstGroupId = getBestScanlator(json.getJSONObject("groups"), sort)
        chapter.scanlator = scanlators.getValueFromKey(firstGroupId)
        chapter.date_upload = json.getJSONObject("release_date").getLong(firstGroupId) * 1000
        chapter.name = num + " - " + json.getString("title")
        chapter.chapter_number = num.toFloat()
        chapter.url = "$slug/$num"

        return chapter
    }

    private fun parsePageFromJson(json: JSONObject, metadata: JSONObject): List<Page> {
        val pages = json.getJSONArray(metadata.getString("scanlator"))
        return List(pages.length()) {
            Page(
                it + 1,
                "",
                pageBuilder(
                    metadata.getString("slug"),
                    metadata.getString("folder"),
                    pages[it].toString(),
                    metadata.getString("scanlator"),
                ),
            )
        }
    }

    private fun getBestScanlator(json: JSONObject, sort: JSONArray): String {
        val preferred = preferences.getString(scanlatorPreference, null)

        if (preferred != null && json.has(preferred)) {
            return preferred
        } else {
            for (i in 0 until sort.length()) {
                if (json.has(sort.get(i).toString())) {
                    return sort.get(i).toString()
                }
            }
            // If all fails, fall-back to the next available key
            return json.keys().next()
        }
    }

    private fun pageBuilder(slug: String, folder: String, filename: String, groupId: String): String {
        return "$baseUrl/media/manga/$slug/chapters/$folder/$groupId/$filename"
    }

    inner class ScanlatorStore {
        private val scanlatorMap = mutableMapOf<String, String>()
        private val totalRetries = 10
        private var retryCount = 0

        init {
            update(false)
        }

        fun getKeyFromValue(value: String): String {
            update()
            // Fall back to value as key if endpoint fails
            return scanlatorMap.keys.firstOrNull {
                scanlatorMap[it].equals(value)
            } ?: value
        }

        fun getValueFromKey(key: String): String {
            update()
            // Fallback to key as value if endpoint fails
            return if (!scanlatorMap[key].isNullOrEmpty()) {
                scanlatorMap[key].toString()
            } else {
                key
            }
        }

        fun keys(): MutableSet<String> {
            update()
            return scanlatorMap.keys
        }

        private fun onResponse(response: Response) {
            if (!response.isSuccessful) {
                retryCount++
            } else {
                val json = JSONObject(response.body.string())
                for (scanId in json.keys()) {
                    scanlatorMap[scanId] = json.getString(scanId)
                }
            }
        }

        private fun onError(error: Throwable) {
            error.printStackTrace()
        }

        private fun update(blocking: Boolean = true) {
            if (scanlatorMap.isEmpty() && retryCount < totalRetries) {
                try {
                    val call = client.newCall(GET(scanlatorCacheUrl, headers))
                        .asObservable()

                    if (blocking) {
                        call.toBlocking()
                            .subscribe(::onResponse, ::onError)
                    } else {
                        call.subscribeOn(Schedulers.io())
                            .subscribe(::onResponse, ::onError)
                    }
                } catch (e: Exception) {
                    // Prevent the extension from failing to load
                }
            }
        }
    }

    // ----------------- Things we aren't supporting -----------------

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("Unused")
    }

    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException("Unused")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val SLUG_PREFIX = "slug:"
        private const val scanlatorPreference = "SCANLATOR_PREFERENCE"
    }
}
