package eu.kanade.tachiyomi.extension.en.mangadotnet

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
    private val preferences = getPreferences()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/view-all/trending.data".toHttpUrl().newBuilder().apply {
            if (adultModePref()) {
                addQueryParameter("adult", "1")
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
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
            if (adultModePref()) {
                addQueryParameter("adult", "1")
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            addQueryParameter("_routes", "pages/ViewAllPage")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
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
                            source = url.queryParameter("source") ?: "scraper",
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
            if (adultModePref()) {
                addQueryParameter("adult", "1")
            }
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
                        if (filter.ascending) {
                            addQueryParameter("sortOrder", "asc")
                        }
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
                    else -> throw IllegalStateException("Unknown filter: ${filter::class.simpleName}")
                }
            }
            addQueryParameter("_routes", "pages/SearchPage")
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(getGenres()),
    )

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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = runBlocking {
        coroutineScope {
            val chapters = async {
                val response = client.newCall(chapterListRequest(manga)).await()
                response.parseAs<List<Chapter>>()
                    .map { chapter ->
                        SChapter.create().apply {
                            url = ChapterUrl(chapter.id, chapter.source, false).toJsonString()
                            name = buildString {
                                val number = (chapter.number ?: "0").toFloat().toString().substringBefore(".0")
                                val name = chapter.name ?: ""
                                if (!name.contains(number)) append("Chapter ", number, ": ")
                                append(name.trim())
                            }
                            chapter_number = (chapter.number ?: "0").toFloat()
                            scanlator = (chapter.group ?: chapter.scanlator)?.takeIf { it.isNotBlank() }
                            date_upload = dateFormat.tryParse(chapter.date)
                        }
                    }
                    .asReversed()
            }
            val volumes = async {
                if (fetchVolumesPref()) {
                    val response = client.newCall(GET("$baseUrl/api/manga/${manga.url}/volumes", headers)).await()
                    response.parseAs<List<Volume>>()
                        .map { volume ->
                            SChapter.create().apply {
                                url = ChapterUrl(volume.id.toString(), "user", true).toJsonString()
                                name = "Volume ${(volume.volume ?: 0f).toString().substringBefore(".0")}"
                                chapter_number = -2f
                                scanlator = (volume.group ?: volume.scanlator)?.takeIf { it.isNotBlank() }
                                date_upload = dateFormat.tryParse(volume.date)
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

    private fun adultModePref() = preferences.getBoolean(NSFW_MODE, false)

    private fun fetchVolumesPref() = preferences.getBoolean(VOLUME_FETCH, false)

    private val genreCacheFile: File by lazy {
        Injekt.get<Application>().cacheDir.resolve("source_$id/genres.json")
    }

    private val genresLock = ReentrantLock()

    private fun getGenres(): List<String> {
        genresLock.withLock {
            if (genreCacheFile.exists()) {
                return genreCacheFile.readText().parseAs<List<String>>()
            }
            return this::class.java
                .getResourceAsStream("/assets/genres.json")!!
                .bufferedReader().use { it.readText() }
                .parseAs<List<String>>()
        }
    }

    private fun updateGenres(newGenres: List<String>) {
        if (newGenres.isEmpty()) return
        if (!genresLock.tryLock()) return
        try {
            if (genreCacheFile.exists() && System.currentTimeMillis() - genreCacheFile.lastModified() < 60_000) return
            val currentGenres = getGenres()
            if (newGenres.toSet() != currentGenres.toSet()) {
                genreCacheFile.parentFile?.mkdirs()
                genreCacheFile.writeText(newGenres.toJsonString())
            }
        } finally {
            genresLock.unlock()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = NSFW_MODE
            title = "NSFW (18+) Content"
            setDefaultValue(false)
        }.also(screen::addPreference)
        SwitchPreferenceCompat(screen.context).apply {
            key = VOLUME_FETCH
            title = "Fetch Volumes"
            summary = "Note: Most titles on the site don't have volumes"
            setDefaultValue(false)
        }.also(screen::addPreference)
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
private const val VOLUME_FETCH = "pref_fetch_volume"
