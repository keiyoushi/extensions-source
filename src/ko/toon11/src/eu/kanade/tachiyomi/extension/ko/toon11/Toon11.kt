package eu.kanade.tachiyomi.extension.ko.toon11

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

class Toon11 : HttpSource() {

    override val name = "11toon"

    override val baseUrl = "https://www.11toon.com"

    override val lang = "ko"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/bbs/board.php?bo_table=toon_c&is_over=0", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".homelist-title")!!.text()
                thumbnail_url = element.selectFirst(".homelist-thumb")?.absUrl("data-mobile-image")
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/bbs/board.php?bo_table=toon_c&sord=&type=upd&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".homelist-title")!!.text()
                element.selectFirst(".homelist-thumb")?.also {
                    thumbnail_url = "https:" + it.attr("style").substringAfter("url('").substringBefore("')")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        val url = "$baseUrl/bbs/search_stx.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("stx", query)
        }.build()
        GET(url, headers)
    } else {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val urlString = sortFilter?.selected ?: sortList[0].value
        val isOver = statusFilter?.selected ?: ""
        val genre = genreFilter?.selected ?: ""

        val url = (baseUrl + urlString).toHttpUrl().newBuilder().apply {
            addQueryParameter("is_over", isOver)
            if (page > 1) addQueryParameter("page", page.toString())
            if (genre.isNotEmpty()) addQueryParameter("sca", genre)
        }.build()

        GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li[data-id]").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".homelist-title")!!.text()
                val dataId = element.attr("data-id")
                url = "/bbs/board.php?bo_table=toons&stx=${URLEncoder.encode(title, "UTF-8")}&is=$dataId"
                element.selectFirst(".homelist-thumb")?.also {
                    thumbnail_url = "https:" + it.attr("style").substringAfter("url('").substringBefore("')")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pg_end") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.title")!!.text()
            thumbnail_url = document.selectFirst("img.banner")?.absUrl("src")
            document.selectFirst("span:contains(분류) + span")?.also { status = parseStatus(it.text()) }
            document.selectFirst("span:contains(작가) + span")?.also { author = it.text() }
            document.selectFirst("span:contains(소개) + span")?.also { description = it.text() }
            document.selectFirst("span:contains(장르) + span")?.also { genre = it.text().split(",").joinToString { s -> s.trim() } }
        }
    }

    private fun parseStatus(element: String): Int = when {
        "완결" in element -> SManga.COMPLETED
        "주간" in element || "월간" in element || "연재" in element || "격주" in element -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val nav = document.selectFirst("span.pg")
        val chapters = ArrayList<SChapter>()

        document.select("#comic-episode-list > li").forEach {
            chapters.add(parseChapter(it))
        }

        if (nav == null) {
            return chapters
        }

        val pg2url = nav.selectFirst(".pg_current ~ .pg_page")?.absUrl("href")

        if (pg2url != null) {
            parseChapters(pg2url, chapters)
        }

        return chapters
    }

    private tailrec fun parseChapters(nextURL: String, chapters: ArrayList<SChapter>) {
        val newpage = client.newCall(GET(nextURL, headers)).execute().asJsoup()
        newpage.select("#comic-episode-list > li").forEach {
            chapters.add(parseChapter(it))
        }
        val newURL = newpage.selectFirst(".pg_current ~ .pg_page")?.absUrl("href")
        if (!newURL.isNullOrBlank()) parseChapters(newURL, chapters)
    }

    private fun parseChapter(element: Element): SChapter {
        val urlEl = element.selectFirst("button")
        val dateEl = element.selectFirst(".free-date")

        return SChapter.create().apply {
            urlEl?.also {
                url = it.attr("onclick").substringAfter("location.href='.").substringBefore("'")
                name = it.selectFirst(".episode-title")!!.text()
            }
            dateEl?.also { date_upload = dateFormat.tryParse(it.text()) }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "/bbs" + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val rawImageLinks = document.selectFirst("script + script[type^=text/javascript]:not([src])")!!.data()
        val imgList = extractList(rawImageLinks)

        return imgList.mapIndexed { i, img ->
            Page(i, imageUrl = "https:$img")
        }
    }

    private fun extractList(jsString: String): List<String> {
        val matchResult = imgListRegex.find(jsString)
        val listString = matchResult?.groupValues?.get(1) ?: return emptyList()
        return listString.parseAs<List<String>>()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Note: can't combine search query with filters, status filter only has effect in 인기만화"),
        Filter.Separator(),
        SortFilter(sortList, 0),
        StatusFilter(statusList, 0),
        GenreFilter(genreList, 0),
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.ENGLISH)
        private val imgListRegex = """img_list\s*=\s*(\[.*?])""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}
