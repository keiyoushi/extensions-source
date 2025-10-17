package eu.kanade.tachiyomi.extension.ja.takecomic

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

class TakeComic : ComiciViewer(
    "TakeComic",
    "https://takecomic.jp",
    "ja",
) {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/list/up/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.series-list-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.series-list-item-link")!!.attr("href"))
                title = element.selectFirst("div.series-list-item-h span")!!.text()
                thumbnail_url = element.selectFirst("img.series-list-item-img")?.attr("src")?.let { baseUrl.toHttpUrlOrNull()?.newBuilder(it)?.build()?.queryParameter("url") }
            }
        }
        val hasNextPage = document.selectFirst("a.g-pager-link.mode-active + a.g-pager-link") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val browseFilter = filterList.firstInstance<BrowseFilter>()
        val path = getFilterOptions()[browseFilter.state].second
        val url = "$baseUrl$path/$page"

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()

        if (url.contains("/series/list/") || url.contains("/category/")) {
            return latestUpdatesParse(response)
        }

        return super.searchMangaParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<ApiResponse>().series.summary.let { details ->
            SManga.create().apply {
                title = details.name
                author = details.author.joinToString { it.name }
                artist = author
                description = try {
                    details.description.parseAs<List<DescriptionNode>>()
                        .joinToString("\n") { node -> node.children.joinToString { it.text } }
                } catch (e: Exception) {
                    details.description
                }
                genre = details.tag.joinToString { it.name }
                thumbnail_url = details.images.firstOrNull()?.url
            }
        }

    override fun chapterListRequest(manga: SManga): Request {
        val seriesHash = manga.url.substringAfterLast("/")
        val apiUrl = "$baseUrl/api/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .addQueryParameter("episodeFrom", "1")
            .addQueryParameter("episodeTo", "9999")
            .build()
        return GET(apiUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResponse = response.parseAs<ApiResponse>()
        return apiResponse.series.episodes.map { episode ->
            SChapter.create().apply {
                url = "/episode/${episode.id}"
                name = episode.title
                date_upload = episode.datePublished
            }
        }.reversed()
    }

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("完結", "/category/manga/complete"),
        Pair("月曜日", "/category/manga/day/1"),
        Pair("火曜日", "/category/manga/day/2"),
        Pair("水曜日", "/category/manga/day/3"),
        Pair("木曜日", "/category/manga/day/4"),
        Pair("金曜日", "/category/manga/day/5"),
        Pair("土曜日", "/category/manga/day/6"),
        Pair("日曜日", "/category/manga/day/7"),
        Pair("その他", "/category/manga/day/8"),
    )
}
