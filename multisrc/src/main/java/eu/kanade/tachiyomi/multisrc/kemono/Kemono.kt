package eu.kanade.tachiyomi.multisrc.kemono

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.blackholeSink
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.TimeZone
import kotlin.math.min

open class Kemono(
    override val name: String,
    private val defaultUrl: String,
    override val lang: String = "all",
) : HttpSource(), ConfigurableSource {
    override val supportsLatest = true

    private val mirrorUrls get() = arrayOf(defaultUrl, defaultUrl.removeSuffix(".party") + ".su")

    override val client = network.client.newBuilder().rateLimit(2).build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl = preferences.getString(BASE_URL_PREF, defaultUrl)!!

    private val apiPath = "api/v1"

    private val imgCdnUrl = when (name) {
        "Kemono" -> baseUrl
        else -> defaultUrl
    }.replace("//", "//img.")

    private fun String.formatAvatarUrl(): String = removePrefix("https://").replaceBefore('/', imgCdnUrl)

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            fetchNewDesignListing(page, "/artists", compareByDescending { it.favorited })
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            fetchNewDesignListing(page, "/artists/updated", compareByDescending { it.updatedDate })
        }
    }

    private fun fetchNewDesignListing(
        page: Int,
        path: String,
        comparator: Comparator<KemonoCreatorDto>,
    ): MangasPage {
        val baseUrl = baseUrl
        return if (page == 1) {
            val document = client.newCall(GET(baseUrl + path, headers)).execute().asJsoup()
            val cardList = document.selectFirst(Evaluator.Class("card-list__items"))!!
            val creators = cardList.children().map {
                SManga.create().apply {
                    url = it.attr("href")
                    title = it.selectFirst(Evaluator.Class("user-card__name"))!!.ownText()
                    author = it.selectFirst(Evaluator.Class("user-card__service"))!!.ownText()
                    thumbnail_url = it.selectFirst(Evaluator.Tag("img"))!!.absUrl("src").formatAvatarUrl()
                    description = PROMPT
                    initialized = true
                }
            }.filterUnsupported()
            MangasPage(creators, true).also { cacheCreators() }
        } else {
            fetchCreatorsPage(page) { it.apply { sortWith(comparator) } }
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        if (query.isBlank()) throw Exception("Query is empty")
        fetchCreatorsPage(page) { all ->
            val result = all.filterTo(ArrayList()) { it.name.contains(query, ignoreCase = true) }
            if (result.isEmpty()) return@fetchCreatorsPage emptyList()
            if (result[0].favorited != -1) {
                result.sortByDescending { it.favorited }
            } else {
                result.sortByDescending { it.updatedDate }
            }
            result
        }
    }

    private fun fetchCreatorsPage(
        page: Int,
        block: (ArrayList<KemonoCreatorDto>) -> List<KemonoCreatorDto>,
    ): MangasPage {
        val imgCdnUrl = this.imgCdnUrl
        val response = client.newCall(GET("$baseUrl/$apiPath/creators", headers)).execute()
        val allCreators = block(response.parseAs())
        val count = allCreators.size
        val fromIndex = (page - 1) * NEW_PAGE_SIZE
        val toIndex = min(count, fromIndex + NEW_PAGE_SIZE)
        val creators = allCreators.subList(fromIndex, toIndex)
            .map { it.toSManga(imgCdnUrl) }
            .filterUnsupported()
        return MangasPage(creators, toIndex < count)
    }

    private fun cacheCreators() {
        val callback = object : Callback {
            override fun onResponse(call: Call, response: Response) =
                response.body.source().run {
                    readAll(blackholeSink())
                    close()
                }

            override fun onFailure(call: Call, e: IOException) = Unit
        }
        client.newCall(GET("$baseUrl/$apiPath/creators", headers)).enqueue(callback)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        manga.thumbnail_url = manga.thumbnail_url!!.formatAvatarUrl()
        return Observable.just(manga)
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        KemonoPostDto.dateFormat.timeZone = when (manga.author) {
            "Pixiv Fanbox", "Fantia" -> TimeZone.getTimeZone("GMT+09:00")
            else -> TimeZone.getTimeZone("GMT")
        }
        val maxPosts = preferences.getString(POST_PAGES_PREF, POST_PAGES_DEFAULT)!!
            .toInt().coerceAtMost(POST_PAGES_MAX) * POST_PAGE_SIZE
        var offset = 0
        var hasNextPage = true
        val result = ArrayList<SChapter>()
        while (offset < maxPosts && hasNextPage) {
            val request = GET("$baseUrl/$apiPath${manga.url}?limit=$POST_PAGE_SIZE&o=$offset", headers)
            val page: List<KemonoPostDto> = client.newCall(request).execute().parseAs()
            page.forEach { post -> if (post.images.isNotEmpty()) result.add(post.toSChapter()) }
            offset += POST_PAGE_SIZE
            hasNextPage = page.size == POST_PAGE_SIZE
        }
        result
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl/$apiPath${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val post: KemonoPostDto = response.parseAs()
        return post.images.mapIndexed { i, path -> Page(i, imageUrl = baseUrl + path) }
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!
        if (!preferences.getBoolean(USE_LOW_RES_IMG, false)) return GET(imageUrl, headers)
        val index = imageUrl.indexOf('/', startIndex = 8) // https://
        val url = buildString {
            append(imageUrl, 0, index)
            append("/thumbnail")
            append(imageUrl, index, imageUrl.length)
        }
        return GET(url, headers)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = POST_PAGES_PREF
            title = "Maximum posts to load"
            summary = "Loading more posts costs more time and network traffic.\nCurrently: %s"
            entryValues = (1..POST_PAGES_MAX).map { it.toString() }.toTypedArray()
            entries = (1..POST_PAGES_MAX).map {
                if (it == 1) "1 page ($POST_PAGE_SIZE posts)" else "$it pages (${it * POST_PAGE_SIZE} posts)"
            }.toTypedArray()
            setDefaultValue(POST_PAGES_DEFAULT)
        }.let { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Mirror URL"
            summary = "%s\nRequires app restart to take effect"
            entries = mirrorUrls
            entryValues = mirrorUrls
            setDefaultValue(defaultUrl)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_LOW_RES_IMG
            title = "Use low resolution images"
            summary = "Reduce load time significantly. When turning off, clear chapter cache to remove cached low resolution images."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val NEW_PAGE_SIZE = 50
        const val PROMPT = "You can change how many posts to load in the extension preferences."

        private const val POST_PAGE_SIZE = 50
        private const val POST_PAGES_PREF = "POST_PAGES"
        private const val POST_PAGES_DEFAULT = "1"
        private const val POST_PAGES_MAX = 50

        private fun List<SManga>.filterUnsupported() = filterNot { it.author == "Discord" }

        private const val BASE_URL_PREF = "BASE_URL"
        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
    }
}
