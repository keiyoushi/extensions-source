package eu.kanade.tachiyomi.extension.en.mangadotnet

import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Mangadotnet :
    HttpSource(),
    ConfigurableSource {
    override val name = "Mangadotnet"
    override val lang = "en"
    override val baseUrl = "https://mangadot.net"
    override val supportsLatest = true

    override val client = network.client.newBuilder().build()

    // ============================== Setup ===============================
    private val preferences = getPreferences {
        val oldChapterKey = "pref_fetch_volume"
        if (contains(oldChapterKey)) {
            val fetchVolumes = getBoolean(oldChapterKey, false)
            edit()
                .putString(CHAPTER_MODE, if (fetchVolumes) "both" else "chapters")
                .remove(oldChapterKey)
                .apply()
        }

        if (contains(NSFW_MODE)) {
            when (val value = all[NSFW_MODE]) {
                is Boolean -> edit()
                    .putString(NSFW_MODE, if (value) "both" else "none")
                    .apply()
                is String -> when (value) {
                    "0" -> edit().putString(NSFW_MODE, "none").apply()
                    "1" -> edit().putString(NSFW_MODE, "1").apply()
                }
            }
        }

        if (all[BROWSE_TYPE_PREF] is String) {
            val value = all[BROWSE_TYPE_PREF] as String
            if (value.isNotEmpty()) {
                edit().putStringSet(BROWSE_TYPE_PREF, setOf("JP", "KR", "CN", "ONESHOT") - value).apply()
            } else {
                edit().remove(BROWSE_TYPE_PREF).apply()
            }
        }

        val demographics = setOf("Josei", "Seinen", "Shoujo", "Shounen")
        val normalExcluded = getStringSet(EXCLUDE_GENRE_PREF, emptySet()) ?: emptySet()
        val adultExcluded = getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet()) ?: emptySet()
        val migratedDemos = (normalExcluded + adultExcluded).intersect(demographics)

        if (migratedDemos.isNotEmpty()) {
            val existingDemos = getStringSet(EXCLUDE_DEMOGRAPHIC_PREF, emptySet()) ?: emptySet()
            edit()
                .putStringSet(EXCLUDE_DEMOGRAPHIC_PREF, existingDemos + migratedDemos)
                .putStringSet(EXCLUDE_GENRE_PREF, normalExcluded - demographics)
                .putStringSet(EXCLUDE_GENRE_ADULT_PREF, adultExcluded - demographics)
                .apply()
        }

        if (getString(POPULAR_MODE_PREF, null) == "trending") {
            edit().putString(POPULAR_MODE_PREF, "most-tracked").apply()
        }
    }

    private val officialPriorityPatterns = listOf(
        "manga plus", "mangaplus", "viz media", "viz manga", "webtoon",
        "tapas", "mangadex", "k manga", "kmanga", "mangaup", "manga up",
        "comikey", "shonen jump", "shounen jump", "shonenjump", "official",
    ).map { Regex("\\b${Regex.escape(it)}\\b") }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val demographicNames = setOf("Josei", "Seinen", "Shoujo", "Shounen")

    private fun getGenreList(isAdult: Boolean): List<String>? {
        val list = getGenreLists().let { if (isAdult) it.adult else it.normal }

        return list?.filter { it !in demographicNames }
            ?.sortedBy { it.lowercase(Locale.ROOT) }
    }

    private fun HttpUrl.Builder.addAdultParam(): HttpUrl.Builder {
        when (adultModePref()) {
            "none" -> addQueryParameter("adult", "0")
            "1" -> addQueryParameter("adult", "1")
            "both" -> addQueryParameter("adult", "both")
        }
        return this
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/view-all/${popularModePref()}.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            excludedGenresPref().forEach { genre ->
                addQueryParameter("genre", "-$genre")
            }
            excludedDemographicsPref().forEach { demo ->
                addQueryParameter("genre", "-$demo")
            }
            addQueryParameter("_routes", "pages/ViewAllPage")
            includedTypes().forEach { origin ->
                addQueryParameter("origin", origin)
            }
            browseStatusPref()?.also { addQueryParameter("status", it) }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.decodeRscAs<Data<ViewAllData>>().data
        updateGenres(data.allGenres.ifEmpty { data.data.allGenres }, adultModePref() != "none")

        return MangasPage(
            data.data.mangaList.orEmpty().map { it.toSManga(baseUrl) },
            data.data.hasNextPage(),
        )
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/view-all/${latestModePref()}.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            excludedGenresPref().forEach { genre ->
                addQueryParameter("genre", "-$genre")
            }
            excludedDemographicsPref().forEach { demo ->
                addQueryParameter("genre", "-$demo")
            }
            addQueryParameter("_routes", "pages/ViewAllPage")
            includedTypes().forEach { origin ->
                addQueryParameter("origin", origin)
            }
            browseStatusPref()?.also { addQueryParameter("status", it) }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("\u200B\u200B")) {
            val name = query.removePrefix("\u200B\u200B")
            val newFilters = filters.apply { firstInstance<ArtistFilter>().state = name }
            return super.fetchSearchManga(page, "", FilterList(newFilters))
        }
        if (query.startsWith("\u200B")) {
            val name = query.removePrefix("\u200B")
            val newFilters = filters.apply { firstInstance<AuthorFilter>().state = name }
            return super.fetchSearchManga(page, "", FilterList(newFilters))
        }
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (
                url != null &&
                (url.host == baseUrl.toHttpUrl().host) &&
                url.pathSize > 1 &&
                (url.pathSegments[0] in listOf("manga", "chapter"))
            ) {
                return if (url.pathSegments[0] == "manga") {
                    val manga = SManga.create().apply {
                        this.url = url.pathSegments[1]
                    }

                    fetchMangaDetails(manga)
                } else {
                    val chapter = SChapter.create().apply {
                        this.url = ChapterUrl(
                            id = url.pathSegments[1],
                            source = url.queryParameter("source") ?: "user",
                            isVolume = url.queryParameter("mode") == "volume",
                        ).toJsonString()
                    }

                    client.newCall(pageListRequest(chapter))
                        .asObservableSuccess()
                        .switchMap {
                            val manga = SManga.create().apply {
                                this.url = it.parseAs<Images>().manga.id.toString()
                            }

                            fetchMangaDetails(manga)
                        }
                }.map {
                    MangasPage(listOf(it), false)
                }
            }

            throw Exception("Unsupported url")
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("page", page.toString())

            filters.firstInstanceOrNull<SortFilter>()?.also { filter ->
                if (filter.sort == "" && query.isBlank()) {
                    addQueryParameter("sortBy", "latest")
                } else {
                    addQueryParameter("sortBy", filter.sort)
                }
                addQueryParameter("sortOrder", if (filter.ascending) "asc" else "desc")
            }

            filters.firstInstanceOrNull<StatusFilter>()?.selected?.also {
                addQueryParameter("status", it)
            }

            filters.firstInstanceOrNull<TypeFilter>()?.checked?.also { checked ->
                if (checked.isNotEmpty() && checked.toSet() != allOrigins) {
                    checked.forEach { origin ->
                        addQueryParameter("origin", origin)
                    }
                }
            }

            filters.firstInstanceOrNull<DemographicFilter>()?.also { filter ->
                filter.included.forEach { demo ->
                    addQueryParameter("genre", demo)
                }
                filter.excluded.forEach { demo ->
                    addQueryParameter("genre", "-$demo")
                }
            }

            filters.firstInstanceOrNull<GenreFilter>()?.also { filter ->
                filter.included.forEach { genre ->
                    addQueryParameter("genre", genre)
                }
                filter.excluded.forEach { genre ->
                    addQueryParameter("genre", "-$genre")
                }
            }

            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.also {
                addQueryParameter("author", it.trim())
            }

            filters.firstInstanceOrNull<ArtistFilter>()?.state?.takeIf { it.isNotBlank() }?.also {
                addQueryParameter("artist", it.trim())
            }

            addQueryParameter("_routes", "pages/SearchPage")
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            DemographicFilter(excludedDemographicsPref()),
            AuthorFilter(),
            ArtistFilter(),
        )

        val isAdult = adultModePref().let { it == "1" || it == "both" }
        val genreList = getGenreList(isAdult)

        if (genreList != null) {
            filters.add(4, GenreFilter(genreList, excludedGenresPref()))
        } else {
            filters.add(4, Filter.Separator())
            filters.add(5, Filter.Header("Press 'reset' to load genres"))
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.decodeRscAs<Data<MangaList>>().data
        updateGenres(data.allGenres, adultModePref() != "none")

        val hideAdultCovers = adultModePref() == "none"

        return MangasPage(data.mangaList.orEmpty().map { it.toSManga(baseUrl, hideAdultCovers) }, data.hasNextPage())
    }

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}.data?_routes=pages/MangaDetailPage", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.decodeRscAs<Data<MangaData>>().data
        return data.mangaData.manga.toSManga(baseUrl)
    }

    override val disableRelatedMangasBySearch = true

    override fun relatedMangaListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val data = response.decodeRscAs<Data<RelatedData>>().data

        return buildList {
            data.relationsData?.relations?.values?.forEach(::addAll)
            addAll(data.suggestions)
        }.map { it.toSManga(baseUrl) }
    }

    // ============================= Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = runBlocking {
        coroutineScope {
            val mode = chapterModePref()
            val deduplicate = preferences.getBoolean(DEDUPLICATE_CHAPTERS, false)

            val chapters = async {
                if (mode != "volumes") {
                    fetchChaptersList(manga)
                } else {
                    emptyList()
                }
            }
            val volumes = async {
                if (mode != "chapters") {
                    val response = client.newCall(GET("$baseUrl/api/manga/${manga.url}/volumes", headers)).await()
                    response.parseAs<List<Volume>>()
                        .map { volume ->
                            SChapter.create().apply {
                                url = ChapterUrl(volume.id.toString(), volume.source, true).toJsonString()
                                name = "Volume ${(volume.volume ?: 0f).toString().removeSuffix(".0")}"
                                chapter_number = 0f
                                scanlator = (volume.group ?: volume.scanlator)?.takeIf { it.isNotBlank() }
                                date_upload = dateFormat.tryParse(volume.date?.substringBefore("+"))
                            }
                        }
                } else {
                    emptyList()
                }
            }

            val chaptersResult = chapters.await()
            val volumesResult = volumes.await()

            val allChapters = if (mode == "volumes" && volumesResult.isEmpty()) {
                fetchChaptersList(manga)
            } else {
                chaptersResult + volumesResult
            }

            val finalChapters = if (deduplicate && allChapters.isNotEmpty()) {
                val prefPatterns = preferences.getString(PREFERRED_SCANLATORS, "")
                    ?.split(",")
                    ?.map { it.trim().lowercase() }
                    ?.filter { it.isNotBlank() }
                    ?.map { Regex("\\b${Regex.escape(it)}\\b") }
                    ?: emptyList()

                val prefPatternsLower = prefPatterns.map { it.pattern.lowercase() }.toSet()
                val fullPriorityPatterns = prefPatterns + officialPriorityPatterns.filter { pattern ->
                    pattern.pattern.lowercase() !in prefPatternsLower
                }

                val dedupedChapters = mutableListOf<SChapter>()
                val grouped = allChapters.groupBy { it.chapter_number }

                for ((chapterNum, chapterGroup) in grouped) {
                    if (chapterNum <= 0f || chapterGroup.size == 1) {
                        dedupedChapters.addAll(chapterGroup)
                        continue
                    }

                    var selectedChapter: SChapter? = null

                    for (pattern in fullPriorityPatterns) {
                        val match = chapterGroup.find { ch ->
                            ch.scanlator?.lowercase()?.let { s -> pattern.containsMatchIn(s) } == true
                        }
                        if (match != null) {
                            selectedChapter = match
                            break
                        }
                    }

                    if (selectedChapter == null) {
                        selectedChapter = chapterGroup.first()
                    }

                    dedupedChapters.add(selectedChapter)
                }
                dedupedChapters
            } else {
                allChapters
            }

            val hasScanlator = finalChapters.any { it.scanlator != null }
            if (hasScanlator) {
                finalChapters.forEach { it.scanlator = it.scanlator ?: "\u200B" }
            }

            Observable.just(finalChapters)
        }
    }

    private suspend fun fetchChaptersList(manga: SManga): List<SChapter> {
        val response = client.newCall(chapterListRequest(manga)).await()
        return response.parseAs<List<Chapter>>()
            .map { chapter ->
                SChapter.create().apply {
                    url = ChapterUrl(chapter.id.toString(), chapter.source, false).toJsonString()
                    name = buildString {
                        val number = chapter.number?.toString()?.removeSuffix(".0") ?: "0"
                        val name = chapter.name ?: ""
                        if (!name.contains(number)) append("Chapter ", number, ": ")
                        append(name.trim())
                    }
                    chapter_number = chapter.number ?: 0f
                    scanlator = (chapter.group ?: chapter.scanlator)?.takeIf { it.isNotBlank() }
                    date_upload = dateFormat.tryParse(chapter.date?.substringBefore("+"))
                }
            }
            .asReversed()
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/manga/${manga.url}/chapters/list?lang=en", headers)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()
        val segment = if (chapterUrl.source == "user") "uploads" else "chapters"

        return GET("$baseUrl/api/$segment/${chapterUrl.id}/images", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("chapter")
            addPathSegment(chapterUrl.id)
            if (chapterUrl.isVolume || chapterUrl.source == "user") {
                addQueryParameter("source", "user")
            }
            if (chapterUrl.isVolume) {
                addQueryParameter("mode", "volume")
            }
        }.build().toString()
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Images>()

        countViews(data.manga.id)

        return data.images.mapIndexed { index, image ->
            Page(
                index = index,
                imageUrl = if (image.url.startsWith("/")) {
                    baseUrl + image.url
                } else if (image.url.startsWith("http")) {
                    image.url
                } else {
                    null
                },
            )
        }.filter { it.imageUrl != null }
    }

    private fun countViews(mangaId: Int) {
        val request = POST("$baseUrl/api/manga/$mangaId/view", headers)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                Log.e(name, "Failed to count views: HTTP Error ${response.code}")
                            }
                        }
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    // =========================== Preferences =============================
    private fun adultModePref(): String = preferences.getString(NSFW_MODE, "none")!!

    private fun chapterModePref() = preferences.getString(CHAPTER_MODE, "chapters")!!

    private fun popularModePref() = preferences.getString(POPULAR_MODE_PREF, "most-tracked")!!

    private fun latestModePref() = preferences.getString(LATEST_MODE_PREF, "latest-updates")!!

    private fun excludedDemographicsPref(): Set<String> = preferences.getStringSet(EXCLUDE_DEMOGRAPHIC_PREF, emptySet())!!

    private fun excludedGenresPref(): Set<String> {
        val mode = adultModePref()
        return if (mode == "1" || mode == "both") {
            preferences.getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet())!!
        } else {
            preferences.getStringSet(EXCLUDE_GENRE_PREF, emptySet())!!
        }
    }

    private val allOrigins = setOf("JP", "KR", "CN", "ONESHOT")

    private fun excludedTypesPref(): Set<String> = preferences.getStringSet(BROWSE_TYPE_PREF, emptySet())!!

    private fun includedTypes(): Set<String> {
        val included = allOrigins - excludedTypesPref()
        return if (included.isEmpty() || included == allOrigins) emptySet() else included
    }

    private fun browseStatusPref(): String? = preferences.getString(BROWSE_STATUS_PREF, "")
        ?.takeIf { it != "" }

    // =========================== Genre Cache =============================
    private val genreCacheNormalFile: File by lazy {
        applicationContext.cacheDir.resolve("source_$id/genres_normal.json")
    }

    private val genreCacheAdultFile: File by lazy {
        applicationContext.cacheDir.resolve("source_$id/genres_adult.json")
    }

    private val genresLock = ReentrantLock()

    private data class GenreLists(
        val normal: List<String>? = null,
        val adult: List<String>? = null,
    )

    private fun getGenreLists(): GenreLists {
        genresLock.withLock {
            val normal = runCatching { genreCacheNormalFile.readText().parseAs<List<String>>() }.getOrNull()
            val adult = runCatching { genreCacheAdultFile.readText().parseAs<List<String>>() }.getOrNull()
            return GenreLists(normal = normal, adult = adult)
        }
    }

    private fun updateGenres(newGenres: List<String>, isAdult: Boolean) {
        if (newGenres.isEmpty()) return
        if (!genresLock.tryLock()) return
        try {
            val genres = newGenres.filter { it !in demographicNames }
            val file = if (isAdult) genreCacheAdultFile else genreCacheNormalFile

            val currentGenres = runCatching {
                if (file.exists()) file.readText().parseAs<List<String>>() else null
            }.getOrNull()

            if (genres.toSet() != currentGenres?.toSet()) {
                file.parentFile?.mkdirs()
                val json = genres.toJsonString()
                file.writeText(json)

                if (!isAdult) {
                    val otherFile = genreCacheAdultFile
                    if (!otherFile.exists()) {
                        otherFile.writeText(json)
                    }
                }
            }
        } finally {
            genresLock.unlock()
        }
    }

    // ============================= Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mode = adultModePref()

        val excludedNormal = preferences.getStringSet(EXCLUDE_GENRE_PREF, emptySet())!!
        val excludedAdult = preferences.getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet())!!

        val nsfwPref = ListPreference(screen.context).apply {
            key = NSFW_MODE
            title = "NSFW (18+) Content"
            entries = arrayOf("No 18+", "18+ Only", "Both 18+ & No 18+")
            entryValues = arrayOf("none", "1", "both")
            setDefaultValue("none")
            summary = "%s"
        }
        screen.addPreference(nsfwPref)

        val popularModePref = ListPreference(screen.context).apply {
            key = POPULAR_MODE_PREF
            title = "Popular Mode"
            entries = arrayOf("Most Tracked", "Top Rated")
            entryValues = arrayOf("most-tracked", "top-rated")
            setDefaultValue("most-tracked")
            summary = "%s"
        }
        screen.addPreference(popularModePref)

        val latestModePref = ListPreference(screen.context).apply {
            key = LATEST_MODE_PREF
            title = "Latest Mode"
            entries = arrayOf("Latest Updates", "Recently Added")
            entryValues = arrayOf("latest-updates", "recently-added")
            setDefaultValue("latest-updates")
            summary = "%s"
        }
        screen.addPreference(latestModePref)

        val browseTypePref = MultiSelectListPreference(screen.context).apply {
            key = BROWSE_TYPE_PREF
            title = "Type Blacklist"
            entries = arrayOf("Manga", "Manhwa", "Manhua", "One Shot")
            entryValues = arrayOf("JP", "KR", "CN", "ONESHOT")
            setDefaultValue(emptySet<String>())
            summary = "Exclude types from Popular & Latest."
        }
        screen.addPreference(browseTypePref)

        val browseStatusPref = ListPreference(screen.context).apply {
            key = BROWSE_STATUS_PREF
            title = "Status Filter"
            entries = arrayOf("Any Status", "Ongoing", "Completed", "Hiatus")
            entryValues = arrayOf("", "Ongoing", "Completed", "Hiatus")
            setDefaultValue("")
            summary = "Applies to Popular & Latest"
        }
        screen.addPreference(browseStatusPref)

        val chapterModePref = ListPreference(screen.context).apply {
            key = CHAPTER_MODE
            title = "Chapter List Mode"
            entries = arrayOf("Chapters only", "Volumes only", "Chapters + Volumes")
            entryValues = arrayOf("chapters", "volumes", "both")
            setDefaultValue("chapters")
            summary = "%s\nNote: Most titles don't have volumes. 'Volumes only' falls back to chapters if none are found."
        }
        screen.addPreference(chapterModePref)

        val dedupeSwitch = SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS
            title = "Deduplicate Chapters"
            summary = "Keep only one version of each chapter, preferring selected scanlators."
            setDefaultValue(false)
        }
        screen.addPreference(dedupeSwitch)

        val priorityPref = EditTextPreference(screen.context).apply {
            key = PREFERRED_SCANLATORS
            title = "Scanlator Priority"
            summary = "Comma-separated, in order of preference. First match wins. Supports unofficial only if scanlator name matches website.\nDefaults to official scrapers: Manga Plus, VIZ Media, Webtoon, Tapas, MangaDex, K Manga, MangaUP, Comikey, Shonen Jump"
            setDefaultValue("VIZ Media, MANGA Plus, MangaPlus, Official, Webtoon, Tapas, MangaDex, K Manga, MangaUP, Comikey, Shonen Jump")
            setEnabled(preferences.getBoolean(DEDUPLICATE_CHAPTERS, false))
        }
        screen.addPreference(priorityPref)

        dedupeSwitch.setOnPreferenceChangeListener { _, newValue ->
            priorityPref.setEnabled(newValue as Boolean)
            true
        }

        val demographicPref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_DEMOGRAPHIC_PREF
            title = "Demographic Blacklist"
            summary = "Exclude demographics when browsing."
            entries = demographicNames.toTypedArray()
            entryValues = demographicNames.toTypedArray()
            setDefaultValue(emptySet<String>())
        }
        screen.addPreference(demographicPref)

        val normalGenres = getGenreList(false) ?: excludedNormal.filter { it !in demographicNames }.sortedBy { it.lowercase(Locale.ROOT) }
        val normalGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_PREF
            title = "Genre Blacklist"
            summary = "Exclude genres when browsing without 18+ content."
            entries = normalGenres.toTypedArray()
            entryValues = normalGenres.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(normalGenres.isNotEmpty() && mode == "none")
        }
        screen.addPreference(normalGenrePref)

        val adultGenres = getGenreList(true) ?: excludedAdult.filter { it !in demographicNames }.sortedBy { it.lowercase(Locale.ROOT) }
        val adultGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_ADULT_PREF
            title = "Genre Blacklist (Adult Mode)"
            summary = "Exclude genres when browsing with 18+ content."
            entries = adultGenres.toTypedArray()
            entryValues = adultGenres.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(adultGenres.isNotEmpty() && (mode == "1" || mode == "both"))
        }
        screen.addPreference(adultGenrePref)

        nsfwPref.setOnPreferenceChangeListener { _, newValue ->
            val newMode = newValue as String
            normalGenrePref.setEnabled(normalGenres.isNotEmpty() && newMode == "none")
            adultGenrePref.setEnabled(adultGenres.isNotEmpty() && (newMode == "1" || newMode == "both"))
            true
        }
    }

    // ========================= RSC Decoder ===============================
    private inline fun <reified T> Response.decodeRscAs(): T {
        val flat = parseAs<JsonArray>()
        val decoded = decodeRsc(flat)
            ?: throw IllegalStateException("Failed to decode RSC response")
        val routes = request.url.queryParameter("_routes")
        return if (routes != null) {
            decoded.jsonObject[routes]!!.parseAs()
        } else {
            decoded.parseAs()
        }
    }

    private fun decodeRsc(flat: JsonArray): JsonElement? {
        val cache = arrayOfNulls<Any>(flat.size)
        val nil = Any()
        fun resolve(i: Int): JsonElement? {
            if (i < 0) return null
            cache[i]?.let { return if (it === nil) null else it as JsonElement }
            val result = when (val el = flat[i]) {
                is JsonNull -> null
                is JsonPrimitive -> if (el.isString) JsonPrimitive(el.content) else el
                is JsonArray -> JsonArray(el.map { resolve((it as JsonPrimitive).int) ?: JsonNull })
                is JsonObject -> JsonObject(
                    el.entries.associate { (k, v) ->
                        (flat[k.removePrefix("_").toInt()] as JsonPrimitive).content to
                            (resolve((v as JsonPrimitive).int) ?: JsonNull)
                    },
                )
            }
            cache[i] = result ?: nil
            return result
        }
        return resolve(0)
    }

    // ========================== Unsupported ==============================
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val NSFW_MODE = "pref_nsfw_mode"
private const val CHAPTER_MODE = "pref_chapter_mode"
private const val POPULAR_MODE_PREF = "pref_popular_mode"
private const val LATEST_MODE_PREF = "pref_latest_mode"
private const val EXCLUDE_GENRE_PREF = "pref_exclude_genre"
private const val EXCLUDE_GENRE_ADULT_PREF = "pref_exclude_genre_adult"
private const val BROWSE_TYPE_PREF = "pref_browse_type"
private const val BROWSE_STATUS_PREF = "pref_browse_status"
private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
private const val PREFERRED_SCANLATORS = "pref_preferred_scanlators"
private const val EXCLUDE_DEMOGRAPHIC_PREF = "pref_exclude_demographic"
