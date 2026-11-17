package eu.kanade.tachiyomi.extension.all.pawchive

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.pawchive.PawchiveCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
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
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Pawchive :
    KeiSource(),
    ConfigurableSource {
    private val preferences = getPreferences()

    private val apiPath = "api/v1"

    private val imgUrl = baseUrl.replace("//", "//img.")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()

            val modifiedRequest = if (request.url.pathSegments.firstOrNull() == "api") {
                request.newBuilder()
                    .header("Accept", "text/css")
                    .build()
            } else {
                request
            }

            var response = chain.proceed(modifiedRequest)

            if (response.code == 404 && request.url.pathSegments.firstOrNull() == "data") {
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
    }

    private val apiClient = client.newBuilder()
        .rateLimit(2)
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", getLatestUpdatesFilterList())

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

                    apiClient.get(url, Fetch.XHR, ensureSuccess = false).use { response ->
                        if (response.code == 401) throw Exception("Log in via WebView to use Favorites")
                        if (!response.isSuccessful) throw HttpException(response.code)
                        response.parseAs<List<PawchiveFavoritesDto>>()
                    }
                } else {
                    emptyList()
                }
            }

            val creatorsDeferred = async(Dispatchers.IO) {
                val url = "$baseUrl/$apiPath/creators"
                val cacheControl = CacheControl.Builder().maxStale(30.minutes).build()

                apiClient.get(url, Fetch.XHR, cacheControl).parseAs<List<PawchiveCreatorDto>>()
            }

            val favorites = favoritesDeferred.await()
            val allCreators = creatorsDeferred.await()

            allCreators.filter { creator ->
                val sName = creator.service.serviceName().lowercase()
                val includeType = typeIncluded.isEmpty() || typeIncluded.contains(sName)
                val excludeType = typeExcluded.isNotEmpty() && typeExcluded.contains(sName)
                val regularSearch = creator.name.contains(query, true)

                val favEntry = favorites.find { f -> f.id == creator.id }
                creator.fav = favEntry?.favedSeq ?: 0L

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
            "serv" -> if (sort.second == "desc") mangas.sortedByDescending { it.service } else mangas.sortedBy { it.service }
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
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments
        if (segments.size < 3 || segments[0] !in services || segments[1] != "user" || segments[2].isBlank()) return null

        val profileUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(apiPath)
            .addPathSegment(segments[0])
            .addPathSegment("user")
            .addPathSegment(segments[2])
            .addPathSegment("profile")
            .build()

        return apiClient.get(profileUrl, Fetch.XHR, ensureSuccess = false).use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw HttpException(response.code)
            response.parseAs<PawchiveCreatorDto>().toSManga(baseUrl)
        }
    }

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

                val page: List<PawchivePostDto> = apiClient.get(url, Fetch.XHR).parseAs()

                page.forEach { post ->
                    if (post.images.isNotEmpty()) {
                        result.add(post.toSChapter(manga.author, datePref))
                    }
                }

                offset += PAGE_POST_LIMIT
                hasNextPage = page.size == PAGE_POST_LIMIT
            }

            // Unique numbers stop "Skip duplicate chapters" merging posts
            result.forEachIndexed { index, chapter ->
                chapter.chapter_number = (result.size - index).toFloat()
            }
            result
        } else {
            chapters
        }

        return SMangaUpdate(manga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/$apiPath${chapter.url}"

        val postData = apiClient.get(url, Fetch.XHR).parseAs<PawchivePostDto>()
        val useLowRes = preferences.getBoolean(USE_LOW_RES_IMG, false)

        return postData.images.mapIndexed { i, url ->
            Page(i, imageUrl = if (useLowRes) url.toThumbnailUrl() else url)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getDefaultFilterList()

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
        private val services = setOf("patreon", "fanbox")

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
