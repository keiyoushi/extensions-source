package eu.kanade.tachiyomi.extension.en.mangadotnet

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours

@Source
abstract class Mangadotnet :
    KeiSource(),
    ConfigurableSource {

    // =============================== Setup ===============================
    private val preferences: SharedPreferences = getPreferences {
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
    }

    private val officialPriorityPatterns = listOf(
        "manga plus", "mangaplus", "viz media", "viz manga", "webtoon",
        "tapas", "mangadex", "k manga", "kmanga", "mangaup", "manga up",
        "comikey", "shonen jump", "shounen jump", "shonenjump", "official",
    ).map { Regex("\\b${Regex.escape(it)}\\b") }

    private val demographicNames = setOf("Josei", "Seinen", "Shoujo", "Shounen")

    private fun HttpUrl.Builder.addAdultParam(): HttpUrl.Builder {
        when (adultModePref()) {
            "none" -> addQueryParameter("adult", "0")
            "1" -> addQueryParameter("adult", "1")
            "both" -> addQueryParameter("adult", "both")
        }
        return this
    }

    override val supportsRelatedMangas = true

    // ========================= Popular & Latest ==========================
    private suspend fun getViewAllPage(mode: String, page: Int): MangasPage {
        val url = "$baseUrl/view-all/$mode.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (page > 1) addQueryParameter("page", page.toString())
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

        val data = client.get(url).use { it.decodeRscAs<Data<ViewAllData>>().data }

        return MangasPage(
            data.data.mangaList.orEmpty().map { it.toSManga(baseUrl) },
            data.data.hasNextPage(),
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getViewAllPage("most-tracked", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getViewAllPage("latest-updates", page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.startsWith("\u200B\u200B")) {
            val name = query.removePrefix("\u200B\u200B")
            val newFilters = filters.apply { firstInstance<ArtistFilter>().state = name }
            return getSearchMangaList(page, "", FilterList(newFilters))
        }
        if (query.startsWith("\u200B")) {
            val name = query.removePrefix("\u200B")
            val newFilters = filters.apply { firstInstance<AuthorFilter>().state = name }
            return getSearchMangaList(page, "", FilterList(newFilters))
        }

        val browseMode = filters.firstInstanceOrNull<BrowseFilter>()?.selected?.takeIf { it.isNotBlank() }
        if (browseMode != null && query.isBlank()) {
            return if (browseMode == "bookmarks") {
                if (!isLoggedIn()) {
                    throw Exception("Login through WebView to view bookmarks")
                }
                val url = "$baseUrl/bookmark.data".toHttpUrl().newBuilder().apply {
                    if (page > 1) addQueryParameter("page", page.toString())
                    addQueryParameter("_routes", "pages/BookmarksPage")
                }.build()
                client.get(url).use { response -> parseBookmarksResponse(response) }
            } else {
                getViewAllPage(browseMode, page)
            }
        }

        val url = "$baseUrl/search.data".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "56")

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

            filters.firstInstanceOrNull<VolumesFilter>()?.selected?.takeIf { it.isNotBlank() }?.also {
                addQueryParameter("volumes", it)
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

            filters.findTagFilters().forEach { filter ->
                filter.included.forEach { tag ->
                    addQueryParameter("tag", tag)
                }
                filter.excluded.forEach { tag ->
                    addQueryParameter("tag", "-$tag")
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

        val data = client.get(url).use { it.decodeRscAs<Data<MangaList>>().data }

        val hideAdultCovers = adultModePref() == "none"
        return MangasPage(data.mangaList.orEmpty().map { it.toSManga(baseUrl, hideAdultCovers) }, data.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSize <= 1) return null

        val mangaUrl = when (url.pathSegments[0]) {
            "manga" -> {
                if (url.pathSegments.size < 2) return null
                url.pathSegments[1]
            }
            "chapter", "volume" -> {
                if (url.pathSegments.size < 2) return null
                val chapterUrl = ChapterUrl(
                    id = url.pathSegments[1],
                    source = if (url.pathSegments[0] == "volume") "user" else (url.queryParameter("source") ?: "user"),
                    isVolume = url.pathSegments[0] == "volume",
                )
                val segment = if (chapterUrl.source == "user") "uploads" else "chapters"
                val apiUrl = "$baseUrl/api/$segment/${chapterUrl.id}/images".toHttpUrl()
                client.get(apiUrl).use { it.parseAs<Images>().manga.id.toString() }
            }
            else -> return null
        }

        val tmpManga = SManga.create().apply { this.url = mangaUrl }
        return fetchMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching get() = true

    @Serializable
    private data class FilterDataDto(
        val genres: List<String>? = null,
        val tags: List<String>? = null,
    )

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val searchUrl = "$baseUrl/search.data?page=1&_routes=pages/SearchPage".toHttpUrl()

        val searchData = client.get(searchUrl).use { it.decodeRscAs<Data<MangaList>>().data }

        val genres = searchData.allGenres.filter { it !in demographicNames }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
        val tags = searchData.allTags.asSequence()
            .flatMap { it.tags }
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
            .toList()

        FilterDataDto(genres, tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val dto = data?.parseAs<FilterDataDto>()

        val filters = mutableListOf(
            BrowseFilter(),
            SortFilter(),
            StatusFilter(),
            VolumesFilter(),
            TypeFilter(),
            DemographicFilter(excludedDemographicsPref()),
        )

        val genreList = dto?.genres?.sortedBy { it.lowercase(Locale.ROOT) }
        val tagList = dto?.tags?.sortedBy { it.lowercase(Locale.ROOT) }

        if (genreList != null) {
            filters.add(GenreFilter(genreList, excludedGenresPref()))
        }

        if (tagList != null) {
            val tagFilters = mutableListOf<TagFilter>()

            val otherTags = tagList.filter { it.first().lowercaseChar() !in 'a'..'z' }
            if (otherTags.isNotEmpty()) {
                tagFilters.add(TagFilter("0-9 / Misc", otherTags))
            }

            val chunks = listOf(
                "A-C" to 'a'..'c',
                "D-H" to 'd'..'h',
                "I-L" to 'i'..'l',
                "M-O" to 'm'..'o',
                "P-S" to 'p'..'s',
                "T-V" to 't'..'v',
                "W-Z" to 'w'..'z',
            )

            chunks.forEach { (name, range) ->
                val chunkTags = tagList.filter { it.first().lowercaseChar() in range }
                if (chunkTags.isNotEmpty()) {
                    tagFilters.add(TagFilter(name, chunkTags))
                }
            }

            if (tagFilters.isNotEmpty()) {
                filters.add(TagsGroupFilter(tagFilters.toList()))
            }
        }

        filters.add(AuthorFilter())
        filters.add(ArtistFilter())

        return FilterList(filters)
    }

    private fun FilterList.findTagFilters(): List<TagFilter> = flatMap { filter ->
        when (filter) {
            is TagFilter -> listOf(filter)
            is Filter.Group<*> -> filter.state.filterIsInstance<TagFilter>()
            else -> emptyList()
        }
    }

    private suspend fun fetchForYouItems(): List<BrowseManga> = runCatching {
        if (!isLoggedIn()) return emptyList()
        val forYouUrl = "$baseUrl/api/manga/for-you?limit=100".toHttpUrl().newBuilder().apply {
            addAdultParam()
        }.build()
        val cache = CacheControl.Builder().maxAge(6.hours).build()
        client.get(forYouUrl, cacheControl = cache).use { response ->
            response.parseAs<ForYouResponse>().items
        }
    }.getOrElse { emptyList() }

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) {
                val url = "$baseUrl/manga/${manga.url}.data?_routes=pages/MangaDetailPage".toHttpUrl()
                val data = client.get(url).use { it.decodeRscAs<Data<MangaData>>().data }
                data.mangaData.manga.toSManga(baseUrl, showTagsPref())
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) fetchChapterListInternal(manga) else chapters
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = coroutineScope {
        val url = "$baseUrl/manga/${manga.url}.data?_routes=pages/MangaDetailPage".toHttpUrl()
        val dataDeferred = async { client.get(url).use { it.decodeRscAs<Data<RelatedData>>().data } }
        val forYouDeferred = async { fetchForYouItems() }

        val data = dataDeferred.await()
        val forYouItems = forYouDeferred.await()

        buildList {
            data.relationsData?.relations?.values?.forEach(::addAll)
            addAll(data.suggestions)
            addAll(forYouItems)
        }.map { it.toSManga(baseUrl) }
    }

    // ============================= Chapters ==============================
    private suspend fun fetchChapterListInternal(manga: SManga): List<SChapter> = coroutineScope {
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
                val volumesUrl = "$baseUrl/api/manga/${manga.url}/volumes?lang=en".toHttpUrl()
                client.get(volumesUrl).use { response ->
                    response.parseAs<List<Volume>>()
                        .filter { it.language == null || it.language == "en" }
                        .map { volume ->
                            SChapter.create().apply {
                                url = ChapterUrl(volume.id.toString(), volume.source, true).toJsonString()
                                name = "Volume ${(volume.volume ?: 0f).toString().removeSuffix(".0")}"
                                chapter_number = 0f
                                scanlator = (volume.group ?: volume.scanlator)?.takeIf { it.isNotBlank() }
                                date_upload = dateFormat.tryParse(volume.date?.substringBefore("+"))
                            }
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

        finalChapters
    }

    private suspend fun fetchChaptersList(manga: SManga): List<SChapter> {
        val chaptersUrl = "$baseUrl/api/manga/${manga.url}/chapters/list?lang=en".toHttpUrl()
        return client.get(chaptersUrl).use { response ->
            response.parseAs<List<Chapter>>()
                .filter { it.language == null || it.language == "en" }
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
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // =============================== Pages ===============================
    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()
        if (chapterUrl.isVolume) {
            return "$baseUrl/volume/${chapterUrl.id}"
        }
        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("chapter")
            addPathSegment(chapterUrl.id)
            if (chapterUrl.source == "user") {
                addQueryParameter("source", "user")
            }
        }.build().toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()
        val segment = if (chapterUrl.source == "user") "uploads" else "chapters"
        val apiUrl = "$baseUrl/api/$segment/${chapterUrl.id}/images".toHttpUrl()

        val data = client.get(apiUrl).use { it.parseAs<Images>() }

        countViews(data.manga.id)

        val chapterPageUrl = getChapterUrl(chapter)
        return data.images.mapIndexed { index, image ->
            Page(
                index = index,
                url = chapterPageUrl,
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
        val url = "$baseUrl/api/manga/$mangaId/view"
        val request = POST(url, headers, FormBody.Builder().build())
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(name, "Failed to count views: ${response.code}")
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(name, "Failed to count views", e)
            }
        })
    }

    override fun imageRequest(page: Page): Request {
        val referer = page.url.substringBeforeLast("#")
        return GET(page.imageUrl!!, headers.newBuilder().set("Referer", referer).build())
    }

    // ============================ Preferences ============================
    private fun adultModePref(): String = preferences.getString(NSFW_MODE, "none")!!

    private fun chapterModePref() = preferences.getString(CHAPTER_MODE, "chapters")!!

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

    private fun showTagsPref() = preferences.getBoolean(SHOW_TAGS_PREF, true)

    // ============================= Bookmarks =============================
    private fun parseBookmarksResponse(response: Response): MangasPage = try {
        val flat = response.parseAs<JsonArray>()
        val decoded = decodeRsc(flat)
            ?: throw Exception("Login through WebView to view bookmarks")
        val routeContent = decoded.jsonObject["pages/BookmarksPage"]
            ?: throw Exception("Login through WebView to view bookmarks")

        val container = findRscObjectContaining(routeContent, "entries")
        val hideAdultCovers = adultModePref() == "none"

        val data = container?.parseAs<BookmarksData>() ?: BookmarksData()
        val entries = data.entries

        if (entries.isEmpty() && !isLoggedIn()) {
            throw Exception("Login through WebView to view bookmarks")
        }

        MangasPage(
            entries.map { it.toSManga(baseUrl, hideAdultCovers) },
            data.hasNextPage(),
        )
    } catch (e: Exception) {
        if (e.message == "Login through WebView to view bookmarks") throw e
        throw Exception("Login through WebView to view bookmarks")
    }

    private fun findRscObjectContaining(element: JsonElement, fieldName: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                if (fieldName in element) return element
                for (value in element.values) {
                    findRscObjectContaining(value, fieldName)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findRscObjectContaining(item, fieldName)?.let { return it }
                }
            }
            else -> {}
        }
        return null
    }

    private fun isLoggedIn(): Boolean {
        val cookies = android.webkit.CookieManager.getInstance().getCookie(baseUrl) ?: return false
        return cookies.contains("ory_kratos_session=")
    }

    // ============================= Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mode = adultModePref()

        val nsfwPref = ListPreference(screen.context).apply {
            key = NSFW_MODE
            title = "NSFW (18+) Content"
            entries = arrayOf("No 18+", "18+ Only", "Both 18+ & No 18+")
            entryValues = arrayOf("none", "1", "both")
            setDefaultValue("none")
            summary = "%s"
        }
        screen.addPreference(nsfwPref)

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
            summary = "Applies to Popular & Latest."
        }
        screen.addPreference(browseStatusPref)

        val showTagsPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_TAGS_PREF
            title = "Show Tags In Details"
            summary = "Show tags after genres in manga details."
            setDefaultValue(true)
        }
        screen.addPreference(showTagsPref)

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
            summary = "Comma-separated, in order of preference. First match wins. Supports unofficial only if scanlator name matches website.\nDefaults to official scrapers: Manga Plus, VIZ Media, Webtoon, Tapas, MangaDex, K Manga, Manga UP, Comikey, Shonen Jump"
            setDefaultValue("VIZ Media, MANGA Plus, MangaPlus, Official, Webtoon, Tapas, MangaDex, K Manga, Manga UP, Comikey, Shonen Jump")
            setEnabled(preferences.getBoolean(DEDUPLICATE_CHAPTERS, false))
        }
        screen.addPreference(priorityPref)

        dedupeSwitch.setOnPreferenceChangeListener { _, newValue ->
            priorityPref.setEnabled(newValue as Boolean)
            true
        }

        val sortedDemographics = demographicNames.sortedBy { it.lowercase(Locale.ROOT) }.toTypedArray()
        val demographicPref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_DEMOGRAPHIC_PREF
            title = "Demographic Blacklist"
            summary = "Exclude demographics when browsing."
            entries = sortedDemographics
            entryValues = sortedDemographics
            setDefaultValue(emptySet<String>())
        }
        screen.addPreference(demographicPref)

        val filters = getFilterList()
        val genresFromFilters = filters.firstInstanceOrNull<GenreFilter>()?.state?.map { it.name }

        val excludedNormal = preferences.getStringSet(EXCLUDE_GENRE_PREF, emptySet()) ?: emptySet()
        val excludedAdult = preferences.getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet()) ?: emptySet()

        val genres = (genresFromFilters ?: (excludedNormal + excludedAdult).filter { it !in demographicNames })
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }

        val normalGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_PREF
            title = "Genre Blacklist"
            summary = "Exclude genres when browsing without 18+ content."
            entries = genres.toTypedArray()
            entryValues = genres.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(genres.isNotEmpty() && mode == "none")
        }
        screen.addPreference(normalGenrePref)

        val adultGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_ADULT_PREF
            title = "Genre Blacklist (Adult Mode)"
            summary = "Exclude genres when browsing with 18+ content."
            entries = genres.toTypedArray()
            entryValues = genres.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(genres.isNotEmpty() && (mode == "1" || mode == "both"))
        }
        screen.addPreference(adultGenrePref)

        nsfwPref.setOnPreferenceChangeListener { _, newValue ->
            val newMode = newValue as String
            normalGenrePref.setEnabled(genres.isNotEmpty() && newMode == "none")
            adultGenrePref.setEnabled(genres.isNotEmpty() && (newMode == "1" || newMode == "both"))
            true
        }
    }

    // ============================ RSC Decoder ============================
    private inline fun <reified T> Response.decodeRscAs(): T {
        val flat = parseAs<JsonArray>()
        val decoded = decodeRsc(flat)
            ?: throw IllegalStateException("Failed to decode RSC response")
        val routes = request.url.queryParameter("_routes")
        return if (routes != null) {
            val routeElement = decoded.jsonObject[routes]
                ?: throw IllegalStateException("Route '$routes' not found in RSC response")
            routeElement.parseAs()
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
                is JsonArray -> JsonArray(
                    el.map {
                        resolve((it as JsonPrimitive).int) ?: JsonNull
                    },
                )
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
}

private const val NSFW_MODE = "pref_nsfw_mode"
private const val CHAPTER_MODE = "pref_chapter_mode"
private const val EXCLUDE_GENRE_PREF = "pref_exclude_genre"
private const val EXCLUDE_GENRE_ADULT_PREF = "pref_exclude_genre_adult"
private const val BROWSE_TYPE_PREF = "pref_browse_type"
private const val BROWSE_STATUS_PREF = "pref_browse_status"
private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
private const val PREFERRED_SCANLATORS = "pref_preferred_scanlators"
private const val EXCLUDE_DEMOGRAPHIC_PREF = "pref_exclude_demographic"
private const val SHOW_TAGS_PREF = "pref_show_tags"
