package eu.kanade.tachiyomi.extension.ja.momonga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MomonGA : HttpSource() {
    override val lang = "ja"
    override val name = "momon:GA"
    override val supportsLatest = false

    override val baseUrl = "https://momon-ga.com"

    override val client: OkHttpClient = network.cloudflareClient

    // Chapters

    private val dateFormat = SimpleDateFormat("yyyy年M月d日H時", Locale.ENGLISH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "単一章"
                url = response.request.url.encodedPath
                date_upload = dateFormat.tryParse(document.select("#post-time").text())
            },
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // LatestUpdate (Not supported)

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            document.select("#post-tag > div.post-tag-table").forEach { div ->
                when (div.selectFirst("div.post-tag-title")!!.text()) {
                    "サークル" -> {
                        artist = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    "作者" -> {
                        author = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    "内容" -> {
                        genre = div.select("div.post-tags > a").joinToString { it.text() }
                    }

                    else -> {}
                }
            }
            title = document.selectFirst("#post-data > h1")!!.text()
            thumbnail_url = document.selectFirst("#post-hentai > img")?.absUrl("src")
            status = COMPLETED
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val document = response.asJsoup()

        document.select("#post-hentai > img").forEachIndexed { index, element ->
            add(Page(index, imageUrl = element.attr("src")))
        }
    }

    // Popular

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val lis = mutableListOf<SManga>()
        document.select("div.post-list > a").forEach { element ->
            lis.add(
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("span")!!.text()
                    thumbnail_url = element.selectFirst("div.post-list-image > img")?.absUrl("src")
                },
            )
        }

        return MangasPage(lis, document.selectFirst("div.wp-pagenavi > a.nextpostslink") != null)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popularity/", headers)

    // Search

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
            if (query != "" && !query.contains("-")) {
                addQueryParameter("s", query)
            } else {
                val path =
                    filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
                addEncodedPathSegments(path)
            }
        }

        if (page > 1) {
            urlBuilder.addPathSegments("page/$page")
        }

        return GET(urlBuilder.build().toString(), headers)
    }

    // Filters

    override fun getFilterList() = FilterList(
        CategoryGroupFiler(),
    )

    private class CategoryGroupFiler :
        UriPartFilter(
            "カテゴリーグループ",
            arrayOf(
                Pair("同人誌", "fanzine"),
                Pair("商業誌", "magazine"),
                Pair("急上昇", "trend"),
                Pair("人気", "popularity"),
                Pair("高評価", "rated"),
            ),
        )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }
}
