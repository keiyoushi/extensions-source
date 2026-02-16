package eu.kanade.tachiyomi.extension.ja.jumprookie

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class JumpRookie : HttpSource() {
    override val name = "Jump Rookie!"
    override val baseUrl = "https://rookie.shonenjump.com"
    override val lang = "ja"
    override val supportsLatest = true

    private var nextPageKey: String? = null

    // Requires desktop UA to load entries, mobile version has different selectors.
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) nextPageKey = null
        return searchMangaRequest(page, "", FilterList())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val nextUrlHeader = response.header("Tky-Link-Rel-Next")
        nextPageKey = (baseUrl + nextUrlHeader).toHttpUrlOrNull()?.queryParameter("key")
        val document = response.asJsoup()
        val mangas = document.select("section.series-contents").map(::mangaFromElement)
        return MangasPage(mangas, nextPageKey != null)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/categories/general/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".series-box-list > li").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".button-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstanceOrNull<GenreFilter>()
        if (page == 1) nextPageKey = null
        val url = "$baseUrl/api/media/series_list".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "popular")
            if (filter?.value?.isNotEmpty() == true) {
                addQueryParameter("category", filter.value)
            }
            if (page > 1 && nextPageKey != null) {
                addQueryParameter("key", nextPageKey)
            }
            fragment("filters")
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.fragment == "filters") {
            return popularMangaParse(response)
        }

        val document = response.asJsoup()
        val mangas = document.select("#search-series .series-box-list > li").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".series-title")!!.text()
        thumbnail_url = element.selectFirst(".cover-image")?.absUrl("src")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".series-title")!!.text()
            author = document.selectFirst(".user-name")?.text()
            description = document.selectFirst(".series-description")?.text()
            genre = document.select(".series-category").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".cover-image")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#episode-list > li").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.episode-content")!!.absUrl("href"))
                name = it.selectFirst(".episode-title")!!.text()
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".js-page-image").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun getFilterList() = FilterList(
        GenreFilter(),
    )

    private class GenreFilter :
        SelectFilter(
            "Genres",
            arrayOf(
                Pair("TOP", ""),
                Pair("バトル", "1"),
                Pair("ファンタジー", "2"),
                Pair("学園・スポーツ", "3"),
                Pair("ラブコメ", "4"),
                Pair("コメディ・ギャグ", "6"),
                Pair("ミステリー・ホラー", "7"),
                Pair("その他", "8"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
