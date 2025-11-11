package eu.kanade.tachiyomi.multisrc.iken

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

abstract class Iken(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
    val apiUrl: String = baseUrl,
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var genres = emptyList<Pair<String, String>>()
    protected val titleCache by lazy {
        val response = client.newCall(GET("$apiUrl/api/query?perPage=9999", headers)).execute()
        val data = response.parseAs<SearchResponse>()

        data.posts
            .filterNot { it.isNovel }
            .also { posts ->
                genres = posts.flatMap {
                    it.genres.map { genre ->
                        genre.name to genre.id.toString()
                    }
                }.distinct()
            }
            .associateBy { it.slug }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)

    protected open val popularMangaSelector = "aside a:has(img), .splide:has(.card) li a:has(img)"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector).mapNotNull {
            titleCache[it.absUrl("href").substringAfter("series/")]?.toSManga()
        }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            if (apiUrl.startsWith("https://api.", true)) {
                addQueryParameter("tag", "latestUpdate")
                addQueryParameter("isNovel", "false")
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("searchTerm", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()
        val page = response.request.url.queryParameter("page")!!.toInt()

        val entries = data.posts
            .filterNot { it.isNovel }
            .map { it.toSManga() }

        val hasNextPage = data.totalCount > (page * perPage)

        return MangasPage(entries, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        StatusFilter(),
        TypeFilter(),
        GenreFilter(genres),
        Filter.Header("Open popular mangas if genre filter is empty"),
    )

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBeforeLast("#")

        return "$baseUrl/series/$slug"
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val slug = manga.url.substringBeforeLast("#")
        val update = titleCache[slug]?.toSManga() ?: manga

        return Observable.just(update)
    }

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/series/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val userId = userIdRegex.find(response.body.string())?.groupValues?.get(1) ?: ""

        val id = response.request.url.fragment!!
        val chapterUrl = "$apiUrl/api/chapters?postId=$id&skip=0&take=900&order=desc&userid=$userId"
        val chapterResponse = client.newCall(GET(chapterUrl, headers)).execute()

        val data = chapterResponse.parseAs<Post<ChapterListResponse>>()

        assert(!data.post.isNovel) { "Novels are unsupported" }

        return data.post.chapters
            .filter { it.isPublic() && (it.isAccessible() || (preferences.getBoolean(showLockedChapterPrefKey, false) && it.isLocked())) }
            .map { it.toSChapter(data.post.slug) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        return document.getNextJson("images").parseAs<List<PageParseDto>>().mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url)
        }
    }

    @Serializable
    class PageParseDto(
        val url: String,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = showLockedChapterPrefKey
            title = "Show inaccessible chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    protected fun Document.getNextJson(key: String): String {
        val data = selectFirst("script:containsData($key)")
            ?.data()
            ?: throw Exception("Unable to retrieve NEXT data")

        val keyIndex = data.indexOf(key)
        val start = data.indexOf('[', keyIndex)

        var depth = 1
        var i = start + 1

        while (i < data.length && depth > 0) {
            when (data[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }

        return "\"${data.substring(start, i)}\"".parseAs<String>()
    }
}

private const val perPage = 18
private const val showLockedChapterPrefKey = "pref_show_locked_chapters"
private val userIdRegex = Regex(""""user\\":\{\\"id\\":\\"([^"']+)\\"""")
