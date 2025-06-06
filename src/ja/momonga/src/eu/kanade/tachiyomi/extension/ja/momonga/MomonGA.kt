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
        val doc = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "単一章"
                url = response.request.url.encodedPath
                date_upload = dateFormat.tryParse(doc.select("#post-time").text())
            },
        )
    }

    override fun imageUrlParse(response: Response): String = ""

    // LatestUpdate (Not supported)

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(emptyList(), false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return Request.Builder().build()
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()

        var dauthor: String? = null
        var dartist: String? = null
        var dgenre: String? = null
        doc.select("#post-tag > div.post-tag-table").forEach { div ->

            when (div.selectFirst("div.post-tag-title")!!.text()) {
                "サークル" -> {
                    dartist = div.select("div.post-tags > a").joinToString(", ") { it.text() }
                }

                "作者" -> {
                    dauthor = div.select("div.post-tags > a").joinToString(", ") { it.text() }
                }

                "内容" -> {
                    dgenre = div.select("div.post-tags > a").joinToString(", ") { it.text() }
                }

                else -> {}
            }
        }

        return SManga.create().apply {
            title = doc.selectFirst("#post-data > h1")!!.text()
            author = dauthor
            artist = dartist
            genre = dgenre
            thumbnail_url = doc.selectFirst("#post-hentai > img")!!.attr("src")
            status = COMPLETED
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val doc = response.asJsoup()

        doc.select("#post-hentai > img").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("src")))
        }
    }

    // Popular

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()

        val lis = mutableListOf<SManga>()
        doc.select("div.post-list > a").forEach { element ->
            lis.add(
                SManga.create().apply {
                    title = element.select("span").text()
                    url = element.attr("href").toHttpUrl().encodedPath
                    thumbnail_url = element.selectFirst("div.post-list-image > img")?.attr("src")
                },
            )
        }

        return MangasPage(lis, doc.selectFirst("div.wp-pagenavi > a.nextpostslink") != null)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popularity/", headers)

    // Search

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (page > 1) {
            urlBuilder.encodedPath("/page/$page")
        }

        val url: String = if (query != "" && !query.contains("-")) {
            urlBuilder.encodedQuery("s=$query").build().toString()
        } else {
            val path = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
            urlBuilder.encodedPath(path).build().toString()
        }
        return GET(url, headers)
    }

    // Filters

    override fun getFilterList() = FilterList(
        CategoryGroupFiler(),
    )

    private class CategoryGroupFiler : UriPartFilter(
        "カテゴリーグループ",
        arrayOf(
            Pair("同人誌", "/fanzine"),
            Pair("商業誌", "/magazine"),
            Pair("急上昇", "/trend"),
            Pair("人気", "/popularity"),
            Pair("高評価", "/rated"),
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
