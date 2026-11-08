package eu.kanade.tachiyomi.multisrc.kemono

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.kemono.KemonoCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

abstract class Kemono :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        if (request.url.pathSegments.first() == "api") {
            chain.proceed(request.newBuilder().header("Accept", "text/css").build())
        } else {
            chain.proceed(request)
        }
    }
        .addInterceptor(::thumbnailFallbackInterceptor)
        .cache(
            Cache(
                directory = File(applicationContext.externalCacheDir, "network_cache_${name.lowercase()}"),
                maxSize = 50L * 1024 * 1024, // 50 MiB
            ),
        )
        .readTimeout(5.minutes)
        .rateLimit(1)

    // Full-size files redirect to the nX file servers, which are often unreachable;
    // the thumbnail server still works, so fall back to it. Once they fail to connect, skip them
    // for the rest of the session so every page doesn't wait for the connect timeout.
    private var fileServersUnreachable = false

    private fun thumbnailFallbackInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.pathSegments.first() != dataPath) return chain.proceed(request)

        if (!fileServersUnreachable) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful) return response
                response.close()
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                fileServersUnreachable = true
            }
        }

        val thumbnailUrl = request.url.newBuilder()
            .encodedPath("/thumbnail${request.url.encodedPath}")
            .build()
        return chain.proceed(request.newBuilder().url(thumbnailUrl).build())
    }

    private val preferences = getPreferences()

    private val apiPath = "api/v1"

    private val dataPath = "data"

    private val imgCdnUrl get() = baseUrl.replace("//", "//img.")

    override suspend fun getPopularManga(page: Int) = searchMangas(page, sortBy = "pop" to "desc")

    override suspend fun getLatestUpdates(page: Int) = searchMangas(page, sortBy = "lat" to "desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchMangas(page, query, filters)

    private suspend fun searchMangas(page: Int = 1, title: String = "", filters: FilterList? = null, sortBy: Pair<String, String> = "" to ""): MangasPage {
        var sort = sortBy
        val typeIncluded: MutableList<String> = mutableListOf()
        val typeExcluded: MutableList<String> = mutableListOf()
        var fav: Boolean? = null
        filters?.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    sort = filter.getValue() to if (filter.state!!.ascending) "asc" else "desc"
                }

                is TypeFilter -> {
                    filter.state.filter { state -> state.isIncluded() }.forEach { tri ->
                        typeIncluded.add(tri.value)
                    }

                    filter.state.filter { state -> state.isExcluded() }.forEach { tri ->
                        typeExcluded.add(tri.value)
                    }
                }

                is FavoritesFilter -> {
                    fav = when (filter.state[0].state) {
                        0 -> null
                        1 -> true
                        else -> false
                    }
                }

                else -> {}
            }
        }

        val mangas = run {
            val favorites = if (fav != null) {
                val response = client.get("$baseUrl/$apiPath/account/favorites", Fetch.XHR, ensureSuccess = false)

                if (response.isSuccessful) {
                    response.parseAs<List<KemonoFavoritesDto>>().filterNot { it.service.lowercase() == "discord" }
                } else {
                    response.close()
                    val message = if (response.code == 401) "You are not logged in" else "HTTP error ${response.code}"
                    throw Exception("Failed to fetch favorites: $message")
                }
            } else {
                emptyList()
            }

            val response = client.get(
                "$baseUrl/$apiPath/creators",
                Fetch.XHR,
                CacheControl.Builder().maxStale(30.minutes).build(),
            )
            val allCreators = response.parseAs<List<KemonoCreatorDto>>().filterNot { it.service.lowercase() == "discord" }
            allCreators.filter {
                val includeType = typeIncluded.isEmpty() || typeIncluded.contains(it.service.serviceName().lowercase())
                val excludeType = typeExcluded.isNotEmpty() && typeExcluded.contains(it.service.serviceName().lowercase())

                val regularSearch = it.name.contains(title, true)

                val isFavorited = when (fav) {
                    true -> favorites.any { f -> f.id == it.id.also { _ -> it.fav = f.faved_seq } }
                    false -> favorites.none { f -> f.id == it.id }
                    else -> true
                }

                includeType && !excludeType && isFavorited &&
                    regularSearch
            }
        }

        val sorted = when (sort.first) {
            "pop" -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.favorited }
                } else {
                    mangas.sortedBy { it.favorited }
                }
            }

            "tit" -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.name }
                } else {
                    mangas.sortedBy { it.name }
                }
            }

            "new" -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.id }
                } else {
                    mangas.sortedBy { it.id }
                }
            }

            "fav" -> {
                if (fav != true) throw Exception("Please check 'Favorites Only' Filter")
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.fav }
                } else {
                    mangas.sortedBy { it.fav }
                }
            }

            else -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.updatedDate }
                } else {
                    mangas.sortedBy { it.updatedDate }
                }
            }
        }
        val maxIndex = mangas.size
        val fromIndex = (page - 1) * PAGE_CREATORS_LIMIT
        val toIndex = min(maxIndex, fromIndex + PAGE_CREATORS_LIMIT)

        val final = sorted.subList(fromIndex, toIndex).map { it.toSManga(imgCdnUrl) }
        return MangasPage(final, toIndex != maxIndex)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[1] != "user") return null
        val (service, _, user) = segments
        return when {
            segments.size == 3 -> fetchCreator(service, user).toSManga(imgCdnUrl)
            segments.size == 5 && segments[3] == "post" -> {
                val manga = SManga.create().apply { this.url = "/$service/user/$user/post/${segments[4]}" }
                getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
            }
            else -> null
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val segments = getMangaUrl(manga).toHttpUrl().pathSegments
        val (service, _, user) = segments
        val zone = zone(service)
        if (segments.size == 3) {
            if (fetchDetails) manga.thumbnail_url = "$imgCdnUrl/icons/$service/$user"
            return SMangaUpdate(manga, if (fetchChapters) fetchChapterList(manga, zone, chapters) else chapters)
        }
        return coroutineScope {
            val creator = if (fetchDetails) async { fetchCreator(service, user) } else null
            val post = fetchPost(manga.url)
            SMangaUpdate(
                post.toSManga(creator?.await()?.name ?: manga.author, baseUrl, imgCdnUrl, zone),
                if (post.images.isNotEmpty()) listOf(post.toSChapter(zone).apply { name = "Post" }) else emptyList(),
            )
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url.replace("$apiPath/", "")}"

    private suspend fun fetchCreator(service: String, user: String) = client.get("$baseUrl/$apiPath/$service/user/$user/profile", Fetch.XHR).parseAs<KemonoCreatorDto>()

    private suspend fun fetchPost(path: String) = client.get("$baseUrl/$apiPath$path", Fetch.XHR).parseAs<KemonoPostDtoWrapped>().post

    private fun zone(service: String) = when (service) {
        "fanbox", "fantia" -> ZoneOffset.ofHours(9)
        else -> ZoneOffset.UTC
    }

    private suspend fun fetchChapterList(manga: SManga, zone: ZoneId, chapters: List<SChapter>): List<SChapter> {
        val pages = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!.toInt().coerceAtMost(POST_PAGES_MAX)
        val fetchedPages = manga.memo.getIntOrNull(FETCHED_PAGES_MEMO) ?: 0
        // Once the setting's pages are loaded, only the posts newer than the newest known one are; changing the setting
        // reloads its pages, which also picks up imported posts kemono lists under their original date
        val newOnly = fetchedPages == pages && chapters.isNotEmpty()
        val known = chapters.mapTo(HashSet()) { it.url }
        var offset = 0
        var hasNextPage = true
        val result = ArrayList<SChapter>()
        val maxPages = if (newOnly) POST_PAGES_MAX else pages
        while (hasNextPage && offset < maxPages * PAGE_POST_LIMIT) {
            val page: List<KemonoPostDto> = retry("$baseUrl/$apiPath${manga.url}/posts?o=$offset").parseAs()
            for (post in page) {
                val chapter = post.toSChapter(zone)
                if (newOnly && chapter.url in known) return result + chapters
                if (post.images.isNotEmpty()) result.add(chapter)
            }
            offset += PAGE_POST_LIMIT
            hasNextPage = page.size == PAGE_POST_LIMIT
        }
        if (!newOnly) manga.memo = JsonObject(manga.memo + (FETCHED_PAGES_MEMO to JsonPrimitive(pages)))
        // Known posts the loaded pages don't list
        val loaded = result.mapTo(HashSet()) { it.url }
        return result + chapters.filter { it.url !in loaded }
    }

    private suspend fun retry(url: String): Response {
        var code = 0
        repeat(5) {
            val response = client.get(url, Fetch.XHR, ensureSuccess = false)
            if (response.isSuccessful) return response
            response.close()
            code = response.code
            if (code == 429) {
                delay(10000)
            }
        }
        throw HttpException(code)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val postData: KemonoPostDtoWrapped = client.get("$baseUrl/$apiPath${chapter.url}", Fetch.XHR).parseAs()
        return postData.post.images.mapIndexed { i, path -> Page(i, imageUrl = "$baseUrl/$dataPath$path") }
    }

    override fun getImageRequest(page: Page): Request {
        if (!preferences.getBoolean(USE_LOW_RES_IMG, false)) return super.getImageRequest(page)

        val imageUrl = page.imageUrl!!.toHttpUrl()
        val url = imageUrl.newBuilder().encodedPath("/thumbnail${imageUrl.encodedPath}").build()
        return Request.Builder().url(url).headers(headers).build()
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

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_LOW_RES_IMG
            title = "Use low resolution images"
            summary = "Reduce load time significantly. When turning off, clear chapter cache to remove cached low resolution images."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // Filters

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(
            "Sort by",
            Filter.Sort.Selection(0, false),
            getSortsList,
        ),
        TypeFilter("Types", getTypes),
        FavoritesFilter(),
    )

    open val getTypes: List<String> = emptyList()

    open val getSortsList: List<Pair<String, String>> = listOf(
        Pair("Popularity", "pop"),
        Pair("Date Indexed", "new"),
        Pair("Date Updated", "lat"),
        Pair("Alphabetical Order", "tit"),
        Pair("Service", "serv"),
        Pair("Date Favorited", "fav"),
    )

    internal open class TypeFilter(name: String, vals: List<String>) :
        Filter.Group<TriFilter>(
            name,
            vals.map { TriFilter(it, it.lowercase()) },
        )

    internal class FavoritesFilter :
        Filter.Group<TriFilter>(
            "Favorites",
            listOf(TriFilter("Favorites Only", "fav")),
        )

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
        private const val POST_PAGES_MAX = 200

        private const val FETCHED_PAGES_MEMO = "fetchedPages"

        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
    }
}
