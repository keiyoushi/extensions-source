package eu.kanade.tachiyomi.extension.zh.wnacg

import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class WNACG :
    HttpSource(),
    ConfigurableSource {

    override val name = "紳士漫畫"
    override val lang = "zh"
    override val supportsLatest = true

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl = when (System.getenv("CI")) {
        "true" -> getCiBaseUrl()
        else -> preferences.baseUrl
    }

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(updateUrlInterceptor)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")
        .set("Referer", baseUrl)
        .set("Sec-Fetch-Mode", "no-cors")
        .set("Sec-Fetch-Site", "cross-site")

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/albums-favorite_ranking-page-$page-type-week.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".gallary_item").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("span.thispage + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/albums-index-page-$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".gallary_item").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("span.thispage + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            val tagFilter = filters.firstInstanceOrNull<TagFilter>()
            if (tagFilter != null && tagFilter.state.isNotEmpty()) {
                return GET("$baseUrl/albums-index-page-$page-tag-${tagFilter.state}.html", headers)
            }
            val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
            if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
                return GET("$baseUrl/" + categoryFilter.toUriPart().format(page), headers)
            }
            return popularMangaRequest(page)
        }
        val url = "$baseUrl/search/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", "create_time_DESC")
            .addQueryParameter("q", query)
            .addQueryParameter("p", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Manga details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2")!!.text()
            artist = document.selectFirst("div.uwuinfo p")?.text()
            author = document.selectFirst("div.uwuinfo p")?.text()
            genre = document.select("a.tagshow").eachText().joinToString(", ").ifEmpty { null }
            thumbnail_url = "http:" + document.selectFirst("div.uwthumb img")!!.attr("src")
            description = document.selectFirst("div.asTBcell p")?.html()?.replace("<br>", "\n")
            status = SManga.COMPLETED
        }
    }

    // Chapter list

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Ch. 1"
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url.replace("-index-", "-gallery-"), headers)

    override fun pageListParse(response: Response): List<Page> = pageImageRegex.findAll(response.body.string()).mapIndexedTo(ArrayList()) { index, match ->
        Page(index, imageUrl = "http:" + match.value)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：分类和标签均不支持搜索"),
        CategoryFilter(),
        Filter.Separator(),
        Filter.Header("注意：仅支持 1 个标签，不支持分类"),
        TagFilter(),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context, preferences, updateUrlInterceptor.isUpdated)
            .forEach(screen::addPreference)
    }

    // Helpers

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(".title > a")!!
        url = link.attr("href")
        title = link.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src").replaceBefore(':', "http")
    }

    companion object {
        private val pageImageRegex = Regex("""//\S*(jpeg|jpg|png|webp|gif)""")
    }
}
