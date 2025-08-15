package eu.kanade.tachiyomi.multisrc.kemono

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.kemono.KemonoCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.lang.Thread.sleep
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.min

open class Kemono(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "all",
) : HttpSource(), ConfigurableSource {
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.pathSegments.first() == "api") {
                chain.proceed(request.newBuilder().header("Accept", "text/css").build())
            } else {
                chain.proceed(request)
            }
        }
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .cache(
            Cache(
                directory = File(Injekt.get<Application>().externalCacheDir, "network_cache_${name.lowercase()}"),
                maxSize = 50L * 1024 * 1024, // 50 MiB
            ),
        )
        .build()

    private val creatorsClient = client.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val preferences = getPreferences()

    private val apiPath = "api/v1"

    private val dataPath = "data"

    private val imgCdnUrl = baseUrl.replace("//", "//img.")

    private fun String.formatAvatarUrl(): String = removePrefix("https://").replaceBefore('/', imgCdnUrl)

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            searchMangas(page, sortBy = "pop" to "desc")
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            searchMangas(page, sortBy = "lat" to "desc")
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        searchMangas(page, query, filters)
    }

    private fun searchMangas(page: Int = 1, title: String = "", filters: FilterList? = null, sortBy: Pair<String, String> = "" to ""): MangasPage {
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
                val response = client.newCall(GET("$baseUrl/$apiPath/account/favorites", headers)).execute()

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

            val request = GET(
                "$baseUrl/$apiPath/creators",
                headers,
                CacheControl.Builder().maxStale(30, TimeUnit.MINUTES).build(),
            )
            val response = creatorsClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        manga.thumbnail_url = manga.thumbnail_url!!.formatAvatarUrl()
        return Observable.just(manga)
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url.replace("$apiPath/", "")}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        KemonoPostDto.dateFormat.timeZone = when (manga.author) {
            "Pixiv Fanbox", "Fantia" -> TimeZone.getTimeZone("GMT+09:00")
            else -> TimeZone.getTimeZone("GMT")
        }
        val prefMaxPost = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!
            .toInt().coerceAtMost(POST_PAGES_MAX) * PAGE_POST_LIMIT
        var offset = 0
        var hasNextPage = true
        val result = ArrayList<SChapter>()
        while (offset < prefMaxPost && hasNextPage) {
            val request = GET("$baseUrl/$apiPath${manga.url}/posts?o=$offset", headers)
            val page: List<KemonoPostDto> = retry(request).parseAs()
            page.forEach { post -> if (post.images.isNotEmpty()) result.add(post.toSChapter()) }
            offset += PAGE_POST_LIMIT
            hasNextPage = page.size == PAGE_POST_LIMIT
        }
        result
    }

    private fun retry(request: Request): Response {
        var code = 0
        repeat(5) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) return response
            response.close()
            code = response.code
            if (code == 429) {
                sleep(10000)
            }
        }
        throw Exception("HTTP error $code")
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl/$apiPath${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val postData: KemonoPostDtoWrapped = response.parseAs()
        return postData.post.images.mapIndexed { i, path -> Page(i, imageUrl = "$baseUrl/$dataPath$path") }
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!

        if (!preferences.getBoolean(USE_LOW_RES_IMG, false)) return GET(imageUrl, headers)

        val index = imageUrl.indexOf('/', 8)
        val url = buildString {
            append(imageUrl, 0, index)
            append("/thumbnail")
            append(imageUrl.substring(index))
        }
        return GET(url, headers)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

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

    override fun getFilterList(): FilterList =
        FilterList(
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

    internal class FavoritesFilter() :
        Filter.Group<TriFilter>(
            "Favorites",
            listOf(TriFilter("Favorites Only", "fav")),
        )

    internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

    internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
        Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
        fun getValue() = vals[state!!.index].second
    }

    companion object {
        private const val PAGE_POST_LIMIT = 50
        private const val PAGE_CREATORS_LIMIT = 50
        const val PROMPT = "You can change how many posts to load in the extension preferences."

        private const val POST_PAGES_PREF = "POST_PAGES"
        private const val POST_PAGES_DEFAULT = "1"
        private const val POST_PAGES_MAX = 75

        // private const val BASE_URL_PREF = "BASE_URL"
        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
    }
}
