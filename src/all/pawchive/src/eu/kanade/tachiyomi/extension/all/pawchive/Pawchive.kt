package eu.kanade.tachiyomi.extension.all.pawchive

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.pawchive.PawchiveCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await // Imported for non-blocking network calls
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import kotlin.io.use
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Pawchive :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val apiPath = "api/v1"

    private val dataPath = "data"

    private val fileUrl = baseUrl.replace("//", "//file.")

    private val imgUrl = baseUrl.replace("//", "//img.")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()

            // 1. Existing API fix
            val modifiedRequest = if (request.url.pathSegments.firstOrNull() == "api") {
                request.newBuilder().header("Accept", "text/css").build()
            } else {
                request
            }

            var response = chain.proceed(modifiedRequest)

            // 2. New Fallback Logic for Images
            if (response.code == 404 && request.url.toString().startsWith(fileUrl)) {
                if (preferences.getBoolean(FALLBACK_LOW_RES_IMG, true)) {
                    response.close() // Close the failed response to prevent memory leaks

                    val fallbackRequest = request.newBuilder()
                        .url(request.url.toString().toThumbnailUrl())
                        .build()
                    response = chain.proceed(fallbackRequest)
                }
            }

            response
        }
            cache(
                Cache(
                    directory = File(applicationContext.externalCacheDir, "network_cache_${name.lowercase()}"),
                    maxSize = 50L * 1024 * 1024, // 50 MiB
                ),
            )
            rateLimit(1)
    }

    private val creatorsClient = client.newBuilder()
        .readTimeout(5.minutes)
        .build()

    private fun String.toThumbnailUrl(): String {
        val pathIndex = this.indexOf('/', 8) // Skips the `https://`
        return buildString {
            append(imgUrl)
            append("/thumbnail")
            append(this@toThumbnailUrl.substring(pathIndex))
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = searchMangas(page, sortBy = "pop" to "desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchMangas(page, sortBy = "lat" to "desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchMangas(page, query, filters)

    private suspend fun searchMangas(page: Int = 1, title: String = "", filters: FilterList? = null, sortBy: Pair<String, String> = "" to ""): MangasPage {
        var sort = sortBy
        val typeIncluded = mutableListOf<String>()
        val typeExcluded = mutableListOf<String>()
        var fav: Boolean? = null

        filters?.forEach { filter ->
            when (filter) {
                is SortFilter -> sort = filter.getValue() to if (filter.state!!.ascending) "asc" else "desc"
                is TypeFilter -> {
                    filter.state.forEach { tri ->
                        if (tri.isIncluded()) typeIncluded.add(tri.value)
                        if (tri.isExcluded()) typeExcluded.add(tri.value)
                    }
                }
                is FavoritesFilter -> {
                    fav = when (filter.state[0].state) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                }
                else -> {}
            }
        }

        val mangas = coroutineScope {
            // 1. Start fetching favorites in the background
            val favoritesDeferred = async {
                if (fav != null) {
                    client.get("$baseUrl/$apiPath/account/favorites").use { response ->
                        if (response.isSuccessful) {
                            response.parseAs<List<PawchiveFavoritesDto>>()
                        } else {
                            val message = if (response.code == 401) "You are not logged in" else "HTTP error ${response.code}"
                            throw Exception("Failed to fetch favorites: $message")
                        }
                    }
                } else {
                    emptyList()
                }
            }

            // 2. Start fetching creators in the background simultaneously
            val creatorsDeferred = async {
                val request = GET(
                    "$baseUrl/$apiPath/creators",
                    headers,
                    CacheControl.Builder().maxStale(30.minutes).build(),
                )
                creatorsClient.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP error ${response.code}")
                    }
                    response.parseAs<List<PawchiveCreatorDto>>()
                }
            }

            // 3. Wait for both background tasks to finish
            val favorites = favoritesDeferred.await()
            val allCreators = creatorsDeferred.await()

            // 4. Perform the filter mapping as before
            allCreators.filter { creator ->
                val sName = creator.service.serviceName().lowercase()
                val includeType = typeIncluded.isEmpty() || typeIncluded.contains(sName)
                val excludeType = typeExcluded.isNotEmpty() && typeExcluded.contains(sName)
                val regularSearch = creator.name.contains(title, true)

                val favEntry = favorites.find { f -> f.id == creator.id }
                creator.fav = favEntry?.faved_seq ?: 0L

                val isFavorited = when (fav) {
                    true -> favEntry != null
                    false -> favEntry == null
                    else -> true
                }

                includeType && !excludeType && isFavorited && regularSearch
            }
        }

        val sorted = when (sort.first) {
            "pop" -> if (sort.second == "desc") mangas.sortedByDescending { it.favorited } else mangas.sortedBy { it.favorited }
            "tit" -> if (sort.second == "desc") mangas.sortedByDescending { it.name.lowercase() } else mangas.sortedBy { it.name.lowercase() }
            "new" -> if (sort.second == "desc") mangas.sortedByDescending { it.id.toLongOrNull() ?: 0L } else mangas.sortedBy { it.id.toLongOrNull() ?: 0L }
            "fav" -> {
                if (fav != true) throw Exception("Please check 'Favorites Only' Filter")
                if (sort.second == "desc") mangas.sortedByDescending { it.fav } else mangas.sortedBy { it.fav }
            }
            else -> if (sort.second == "desc") mangas.sortedByDescending { it.updatedDate } else mangas.sortedBy { it.updatedDate }
        }

        val maxIndex = sorted.size
        val fromIndex = (page - 1) * PAGE_CREATORS_LIMIT
        val toIndex = min(maxIndex, fromIndex + PAGE_CREATORS_LIMIT)
        val final = sorted.subList(fromIndex, toIndex).map { it.toSManga(baseUrl) }

        return MangasPage(final, toIndex != maxIndex)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    // RESOLVES: getMangaByUrl(url)
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.lastOrNull() ?: return null
        val request = GET("$baseUrl/$apiPath/creators", headers, CacheControl.Builder().maxStale(30.minutes).build())
        val response = creatorsClient.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val allCreators = response.parseAs<List<PawchiveCreatorDto>>()
        val creator = allCreators.find { it.id == id } ?: return null
        return creator.toSManga(baseUrl)
    }

    // RESOLVES: fetchRelatedMangaList(manga)
    override val supportsRelatedMangas get() = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // We aren't fetching new details in this snippet, so we pass the existing manga back
        val updatedManga = manga

        val updatedChapters = if (fetchChapters) {
            val prefMaxPost = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!
                .toInt().coerceAtMost(POST_PAGES_MAX) * PAGE_POST_LIMIT

            val datePref = preferences.getString(POST_DATE_PREF, POST_DATE_PUBLISHED)!!

            var offset = 0
            var hasNextPage = true
            val result = ArrayList<SChapter>()

            while (offset < prefMaxPost && hasNextPage) {
                val url = "$baseUrl/$apiPath${manga.url}/posts?o=$offset"

                // Using the client.get().use {} pattern from your example
                client.get(url).use { response ->
                    val page: List<PawchivePostDto> = response.parseAs()

                    page.forEach { post ->
                        if (post.images.isNotEmpty()) {
                            result.add(post.toSChapter(manga.author, datePref))
                        }
                    }

                    offset += PAGE_POST_LIMIT
                    hasNextPage = page.size == PAGE_POST_LIMIT
                }
            }
            result
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // 4. Pages/Images
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/$apiPath${chapter.url}"

        // 1. Fetch and parse the page list
        client.get(url).use { response ->
            val postData: PawchivePostDto = response.parseAs()

            // 2. Grab the preference ONCE before mapping to save performance
            val useLowRes = preferences.getBoolean(USE_LOW_RES_IMG, false)

            // 3. Map the images and apply the low-res logic immediately
            return postData.images.mapIndexed { i, path ->
                val originalUrl = "$fileUrl/$dataPath$path"

                // This replaces your old `imageRequest` logic
                val finalImageUrl = if (useLowRes) originalUrl.toThumbnailUrl() else originalUrl

                Page(i, imageUrl = finalImageUrl)
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = POST_PAGES_PREF
            title = "Maximum posts to load"
            summary = "Loading more posts costs more time and network traffic.\nCurrently: %s"
            entryValues = Array(POST_PAGES_MAX) { (it + 1).toString() }
            entries = Array(POST_PAGES_MAX) { "${(it + 1)} pages (${(it + 1) * PAGE_POST_LIMIT} posts)" }
            setDefaultValue(POST_PAGES_DEFAULT)
        }.let { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = POST_DATE_PREF
            title = "Preferred Post Date"
            summary = "Choose which date to use for chapters.\nCurrently: %s"
            entries = arrayOf("Added Date", "Published Date", "Edited Date")
            entryValues = arrayOf(POST_DATE_ADDED, POST_DATE_PUBLISHED, POST_DATE_EDITED)
            setDefaultValue(POST_DATE_PUBLISHED)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_LOW_RES_IMG
            title = "Use low resolution images"
            summary = "Reduce load time significantly. When turning off, clear chapter cache to remove cached low resolution images."
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = FALLBACK_LOW_RES_IMG
            title = "Fallback to low resolution images"
            summary =
                "If a full size image fails to load (e.g. 404 Not Found), automatically try loading the low resolution thumbnail instead."
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
        TypeFilter("Types", getTypes),
        FavoritesFilter(),
    )

    open val getTypes = listOf("Patreon", "Pixiv Fanbox")

    open val getSortsList: List<Pair<String, String>> = listOf(
        Pair("Popularity", "pop"),
        Pair("Date Indexed", "new"),
        Pair("Date Updated", "lat"),
        Pair("Alphabetical Order", "tit"),
        Pair("Service", "serv"),
        Pair("Date Favorited", "fav"),
    )

    internal open class TypeFilter(name: String, vals: List<String>) : Filter.Group<TriFilter>(name, vals.map { TriFilter(it, it.lowercase()) })
    internal class FavoritesFilter : Filter.Group<TriFilter>("Favorites", listOf(TriFilter("Favorites Only", "fav")))
    internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)
    internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) : Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
        fun getValue() = vals[state!!.index].second
    }

    companion object {
        private const val PAGE_POST_LIMIT = 50
        private const val PAGE_CREATORS_LIMIT = 50
        const val PROMPT = "You can change how many posts to load in the extension preferences."

        private const val POST_PAGES_PREF = "POST_PAGES"
        private const val POST_PAGES_DEFAULT = "1"
        private const val POST_PAGES_MAX = 75

        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
        private const val FALLBACK_LOW_RES_IMG = "FALLBACK_LOW_RES_IMG"

        private const val POST_DATE_PREF = "POST_DATE_PREF"
        private const val POST_DATE_ADDED = "added"
        private const val POST_DATE_PUBLISHED = "published"
        private const val POST_DATE_EDITED = "edited"
    }
}
