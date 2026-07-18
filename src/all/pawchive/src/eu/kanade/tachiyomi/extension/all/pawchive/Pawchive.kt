package eu.kanade.tachiyomi.extension.all.pawchive

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.pawchive.PawchiveCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.io.use
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

            val modifiedRequest = if (request.url.pathSegments.firstOrNull() == "api") {
                request.newBuilder().header("Accept", "text/css").build()
            } else {
                request
            }

            var response = chain.proceed(modifiedRequest)

            if (response.code == 404 && request.url.toString().startsWith(fileUrl)) {
                if (preferences.getBoolean(FALLBACK_LOW_RES_IMG, true)) {
                    response.close()

                    val fallbackRequest = request.newBuilder()
                        .url(request.url.toString().toThumbnailUrl())
                        .build()
                    response = chain.proceed(fallbackRequest)
                }
            }

            response
        }
        connectTimeout(15.seconds)
        readTimeout(30.seconds)
        writeTimeout(15.seconds)
    }

    private val apiClient = client.newBuilder()
        .rateLimit(2)
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", getLatestUpdatesFilterList())

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var sort: Pair<String, String> = "" to ""
        val typeIncluded = mutableListOf<String>()
        val typeExcluded = mutableListOf<String>()
        var fav: Boolean? = null

        filters.forEach { filter ->
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

        if (sort.first.isEmpty()) {
            sort = "pop" to "desc"
        }

        val mangas = coroutineScope {
            val favoritesDeferred = async(Dispatchers.IO) {
                if (fav != null) {
                    val url = "$baseUrl/$apiPath/account/favorites"

                    apiClient.get(url, ensureSuccess = false).use { response ->
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

            val creatorsDeferred = async(Dispatchers.IO) {
                val url = "$baseUrl/$apiPath/creators"
                val cacheControl = CacheControl.Builder().maxStale(30.minutes).build()

                apiClient.get(url, cacheControl).use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP error ${response.code}")
                    }
                    response.parseAs<List<PawchiveCreatorDto>>()
                }
            }

            val favorites = favoritesDeferred.await()
            val allCreators = creatorsDeferred.await()

            allCreators.filter { creator ->
                val sName = creator.service.serviceName().lowercase()
                val includeType = typeIncluded.isEmpty() || typeIncluded.contains(sName)
                val excludeType = typeExcluded.isNotEmpty() && typeExcluded.contains(sName)
                val regularSearch = creator.name.contains(query, true)

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

        val startIndex = (page - 1) * PAGE_CREATORS_LIMIT
        val endIndex = minOf(startIndex + PAGE_CREATORS_LIMIT, sorted.size)

        if (startIndex >= sorted.size) {
            return MangasPage(emptyList(), false)
        }

        val pageItems = sorted.subList(startIndex, endIndex)
        val final = pageItems.map { it.toSManga(baseUrl) }
        val hasNextPage = endIndex < sorted.size

        return MangasPage(final, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }

        if (url.pathSegments.firstOrNull() != "creators" || url.pathSegments.size < 2) {
            return null
        }

        val id = url.pathSegments[1]
        if (id.isBlank()) {
            return null
        }

        val cache = CacheControl.Builder().maxStale(30.minutes).build()
        val url = "$baseUrl/$apiPath/creators"

        return apiClient.get(url, cache, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val allCreators = response.parseAs<List<PawchiveCreatorDto>>()
            val creator = allCreators.find { it.id == id } ?: return null
            creator.toSManga(baseUrl)
        }
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedChapters = if (fetchChapters) {
            val prefMaxPost = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!
                .toInt().coerceAtMost(POST_PAGES_MAX) * PAGE_POST_LIMIT

            val datePref = preferences.getString(POST_DATE_PREF, POST_DATE_PUBLISHED)!!

            var offset = 0
            var hasNextPage = true
            val result = ArrayList<SChapter>()

            while (offset < prefMaxPost && hasNextPage) {
                val url = "$baseUrl/$apiPath${manga.url}/posts?o=$offset"

                val page: List<PawchivePostDto> = apiClient.get(url).parseAs()

                page.forEach { post ->
                    if (post.images.isNotEmpty()) {
                        result.add(post.toSChapter(manga.author, datePref))
                    }
                }

                offset += PAGE_POST_LIMIT
                hasNextPage = page.size == PAGE_POST_LIMIT
            }
            result
        } else {
            chapters
        }

        return SMangaUpdate(manga, updatedChapters)
    }

    override val supportsRelatedMangas get() = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/$apiPath${chapter.url}"

        apiClient.get(url).use { response ->
            val postData: PawchivePostDto = response.parseAs()

            val useLowRes = preferences.getBoolean(USE_LOW_RES_IMG, false)

            return postData.images.mapIndexed { i, path ->
                val originalUrl = "$fileUrl/$dataPath$path"

                val finalImageUrl = if (useLowRes) originalUrl.toThumbnailUrl() else originalUrl

                Page(i, imageUrl = finalImageUrl)
            }
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = getDefaultFilterList()

    // ============================= Utilities =============================

    private fun String.toThumbnailUrl(): String {
        val pathIndex = this.indexOf('/', 8) // Skips the `https://`
        return buildString {
            append(imgUrl)
            append("/thumbnail")
            append(this@toThumbnailUrl.substring(pathIndex))
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
