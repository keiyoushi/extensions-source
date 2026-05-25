package eu.kanade.tachiyomi.extension.en.mangadotnet

import android.app.Application
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.firstInstance
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
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.lang.UnsupportedOperationException
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

    override val client = network.cloudflareClient
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
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private fun HttpUrl.Builder.addAdultParam(): HttpUrl.Builder {
        when (adultModePref()) {
            "none" -> addQueryParameter("adult", "0")
            "1" -> addQueryParameter("adult", "1")
            "both" -> addQueryParameter("adult", "both")
        }
        return this
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/view-all/trending.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            excludedGenresPref().forEach { genre ->
                addQueryParameter("genre", "-$genre")
            }
            addQueryParameter("_routes", "pages/ViewAllPage")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.decodeRscAs<Data<ViewAllData>>().data
        updateGenres(data.allGenres)

        return MangasPage(
            data.data.mangaList.map { it.toSManga(baseUrl) },
            data.data.hasNextPage(),
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/view-all/latest-updates.data".toHttpUrl().newBuilder().apply {
            addAdultParam()
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            excludedGenresPref().forEach { genre ->
                addQueryParameter("genre", "-$genre")
            }
            addQueryParameter("_routes", "pages/ViewAllPage")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

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
            val url = query.toHttpUrl()
            if (
                (url.host == baseUrl.toHttpUrl().host) &&
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
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        if (filter.sort == "relevance" && query.isBlank()) {
                            addQueryParameter("sortBy", "latest")
                        } else {
                            addQueryParameter("sortBy", filter.sort)
                        }
                        addQueryParameter("sortOrder", if (filter.ascending) "asc" else "desc")
                    }
                    is StatusFilter -> {
                        filter.selected?.also { selected ->
                            addQueryParameter("status", selected)
                        }
                    }
                    is TypeFilter -> {
                        filter.checked.forEach { origin ->
                            addQueryParameter("origin", origin)
                        }
                    }
                    is GenreFilter -> {
                        filter.included.forEach { genre ->
                            addQueryParameter("genre", genre)
                        }
                        filter.excluded.forEach { genre ->
                            addQueryParameter("genre", "-$genre")
                        }
                    }
                    is AuthorFilter -> {
                        if (filter.state.isNotBlank()) {
                            addQueryParameter("author", filter.state.trim())
                        }
                    }
                    is ArtistFilter -> {
                        if (filter.state.isNotBlank()) {
                            addQueryParameter("artist", filter.state.trim())
                        }
                    }
                    else -> throw IllegalStateException("Unknown filter: ${filter::class.simpleName}")
                }
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
            AuthorFilter(),
            ArtistFilter(),
        )

        val genres = getGenreLists()
        val genreList = if (adultModePref().let { it == "1" || it == "both" }) genres.adult else genres.normal
        if (genreList != null) {
            filters.add(GenreFilter(genreList, excludedGenresPref()))
        } else {
            filters.add(Filter.Separator())
            filters.add(Filter.Header("Press 'reset' to load genres"))
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.decodeRscAs<Data<MangaList>>().data
        updateGenres(data.allGenres)

        return MangasPage(data.mangaList.map { it.toSManga(baseUrl) }, data.hasNextPage())
    }

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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = runBlocking {
        coroutineScope {
            val mode = chapterModePref()
            val chapters = async {
                if (mode != "volumes") {
                    val response = client.newCall(chapterListRequest(manga)).await()
                    response.parseAs<List<Chapter>>()
                        .map { chapter ->
                            SChapter.create().apply {
                                url = ChapterUrl(chapter.id.toString(), chapter.source, false).toJsonString()
                                name = buildString {
                                    val number = chapter.number?.toString()?.substringBefore(".0") ?: "0"
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
                                name = "Volume ${(volume.volume ?: 0f).toString().substringBefore(".0")}"
                                chapter_number = -2f
                                scanlator = (volume.group ?: volume.scanlator)?.takeIf { it.isNotBlank() }
                                date_upload = dateFormat.tryParse(volume.date?.substringBefore("+"))
                            }
                        }
                } else {
                    emptyList()
                }
            }

            val allChapters = buildList {
                addAll(chapters.await())
                addAll(volumes.await())
            }

            val hasScanlator = allChapters.any { it.scanlator != null }
            if (hasScanlator) {
                allChapters.forEach { it.scanlator = it.scanlator ?: "\u200B" }
            }

            Observable.just(allChapters)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/manga/${manga.url}/chapters/list?lang=en", headers)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        val url = "$baseUrl/api/".toHttpUrl().newBuilder().apply {
            if (chapterUrl.source == "user") {
                addPathSegment("uploads")
            } else {
                addPathSegment("chapters")
            }
            addPathSegment(chapterUrl.id)
            addPathSegment("images")
        }.build()

        return GET(url, headers)
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
        }.toString()
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
                        response.closeQuietly()
                        if (!response.isSuccessful) {
                            Log.e(name, "Failed to count views: HTTP Error ${response.code}")
                        }
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    private fun adultModePref(): String = preferences.getString(NSFW_MODE, "none")!!

    private fun chapterModePref() = preferences.getString(CHAPTER_MODE, "chapters")!!

    private fun excludedGenresPref(): Set<String> {
        val mode = adultModePref()
        return if (mode == "1" || mode == "both") {
            preferences.getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet())!!
        } else {
            preferences.getStringSet(EXCLUDE_GENRE_PREF, emptySet())!!
        }
    }

    private val genreCacheNormalFile: File by lazy {
        Injekt.get<Application>().cacheDir.resolve("source_$id/genres_normal.json")
    }

    private val genreCacheAdultFile: File by lazy {
        Injekt.get<Application>().cacheDir.resolve("source_$id/genres_adult.json")
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
            return GenreLists(
                normal = normal ?: adult,
                adult = adult ?: normal,
            )
        }
    }

    private fun updateGenres(newGenres: List<String>) {
        if (newGenres.isEmpty()) return
        if (!genresLock.tryLock()) return
        try {
            val isAdult = adultModePref().let { it == "1" || it == "both" }
            val file = if (isAdult) genreCacheAdultFile else genreCacheNormalFile
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 60_000) return
            val currentGenres = runCatching {
                if (file.exists()) file.readText().parseAs<List<String>>() else null
            }.getOrNull()
            if (newGenres.toSet() != currentGenres?.toSet()) {
                file.parentFile?.mkdirs()
                file.writeText(newGenres.toJsonString())
            }
        } finally {
            genresLock.unlock()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mode = adultModePref()
        val genres = getGenreLists()

        val nsfwPref = ListPreference(screen.context).apply {
            key = NSFW_MODE
            title = "NSFW (18+) Content"
            entries = arrayOf("No 18+", "18+ Only", "Both 18+ & No 18+")
            entryValues = arrayOf("none", "1", "both")
            setDefaultValue("none")
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = CHAPTER_MODE
            title = "Chapter List Mode"
            entries = arrayOf("Chapters only", "Volumes only", "Chapters + Volumes")
            entryValues = arrayOf("chapters", "volumes", "both")
            setDefaultValue("chapters")
            summary = "%s\nNote: Most titles don't have volumes"
        }.also(screen::addPreference)

        val excludedNormal = preferences.getStringSet(EXCLUDE_GENRE_PREF, emptySet())!!
        val normalEntries = genres.normal ?: excludedNormal.toList()
        val normalGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_PREF
            title = "Genre Blacklist"
            summary = "Exclude genres when browsing without 18+ content."
            this.entries = normalEntries.toTypedArray()
            entryValues = normalEntries.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled((genres.normal != null || excludedNormal.isNotEmpty()) && mode == "none")
        }.also(screen::addPreference)

        val excludedAdult = preferences.getStringSet(EXCLUDE_GENRE_ADULT_PREF, emptySet())!!
        val adultEntries = genres.adult ?: excludedAdult.toList()
        val adultGenrePref = MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_GENRE_ADULT_PREF
            title = "Genre Blacklist (Adult Mode)"
            summary = "Exclude genres when browsing with 18+ content."
            this.entries = adultEntries.toTypedArray()
            entryValues = adultEntries.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled((genres.adult != null || excludedAdult.isNotEmpty()) && (mode == "1" || mode == "both"))
        }.also(screen::addPreference)

        nsfwPref.setOnPreferenceChangeListener { _, newValue ->
            val newMode = newValue as String
            normalGenrePref.setEnabled((genres.normal != null || excludedNormal.isNotEmpty()) && newMode == "none")
            adultGenrePref.setEnabled((genres.adult != null || excludedAdult.isNotEmpty()) && (newMode == "1" || newMode == "both"))
            true
        }
    }

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

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val NSFW_MODE = "pref_nsfw_mode"
private const val CHAPTER_MODE = "pref_chapter_mode"
private const val EXCLUDE_GENRE_PREF = "pref_exclude_genre"
private const val EXCLUDE_GENRE_ADULT_PREF = "pref_exclude_genre_adult"
