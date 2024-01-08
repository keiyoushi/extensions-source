package eu.kanade.tachiyomi.extension.zh.wnacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

// URL can be found at https://www.wnacglink.top/
class wnacg : ParsedHttpSource(), ConfigurableSource {
    override val name = "紳士漫畫"
    override val lang = "zh"
    override val supportsLatest = false

    private val preferences = getSharedPreferences(id)

    override val baseUrl = when (System.getenv("CI")) {
        "true" -> getCiBaseUrl()
        else -> preferences.baseUrl
    }

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override val client = network.client.newBuilder()
        .addInterceptor(updateUrlInterceptor)
        .build()

    override fun popularMangaSelector() = ".gallary_item"
    override fun latestUpdatesSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = popularMangaSelector()
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = "span.thispage + a"
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/albums-index-page-$page.html", headers)
    }

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            filters.forEach { filter ->
                if (filter is CategoryFilter) {
                    return GET("$baseUrl/" + filter.toUriPart().format(page), headers)
                }
            }
            return popularMangaRequest(page)
        }
        val builder = "$baseUrl/search/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", "create_time_DESC")
            .addQueryParameter("q", query)
            .addQueryParameter("p", page.toString())
        return GET(builder.toString(), headers)
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("referer", baseUrl)
        .set("sec-fetch-mode", "no-cors")
        .set("sec-fetch-site", "cross-site")

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val link = element.selectFirst(".title > a")!!
        val manga = SManga.create()
        manga.url = link.attr("href")
        manga.title = link.text()
        manga.thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            .replaceBefore(':', "http")
        // maybe the local cache cause the old source (url) can not be update. but the image can be update on detailpage.
        // ps. new machine can be load img normal.

        return manga
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Ch. 1"
        }
        return Observable.just(listOf(chapter))
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h2")?.text() ?: "Unknown"
        manga.artist = document.selectFirst("div.uwuinfo p")?.text() ?: "Unknown"
        manga.author = document.selectFirst("div.uwuinfo p")?.text() ?: "Unknown"
        manga.thumbnail_url =
            "http:" + document.selectFirst("div.uwthumb img")!!.attr("src")
        manga.description =
            document.selectFirst("div.asTBcell p")?.html()?.replace("<br>", "\n")

        manga.status = SManga.COMPLETED
        return manga
    }

    override fun pageListRequest(chapter: SChapter) =
        GET(baseUrl + chapter.url.replace("-index-", "-gallery-"), headers)

    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun pageListParse(response: Response): List<Page> {
        val regex = """//\S*(jpg|png)""".toRegex()
        val galleryaid =
            response.body.string()
        return regex.findAll(galleryaid).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = "http:" + match.value)
        }
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    // >>> Filters >>>

    override fun getFilterList() = FilterList(
        Filter.Header("注意：分类不支持搜索"),
        CategoryFilter(),
    )

    private class CategoryFilter : UriPartFilter(
        "分类",
        arrayOf(
            Pair("更新", "albums-index-page-%d.html"),
            Pair("同人志-汉化", "albums-index-page-%d-cate-1.html"),
            Pair("同人志-日语", "albums-index-page-%d-cate-12.html"),
            Pair("同人志-CG书籍", "albums-index-page-%d-cate-2.html"),
            Pair("同人志-Cosplay", "albums-index-page-%d-cate-3.html"),
            Pair("单行本-汉化", "albums-index-page-%d-cate-9.html"),
            Pair("单行本-日语", "albums-index-page-%d-cate-13.html"),
            Pair("杂志&短篇-汉语", "albums-index-page-%d-cate-10.html"),
            Pair("杂志&短篇-日语", "albums-index-page-%d-cate-14.html"),
            Pair("韩漫-汉化", "albums-index-page-%d-cate-20.html"),
            Pair("韩漫-生肉", "albums-index-page-%d-cate-21.html"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // <<< Filters <<<

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context, preferences, updateUrlInterceptor.isUpdated).forEach(screen::addPreference)
    }
}
