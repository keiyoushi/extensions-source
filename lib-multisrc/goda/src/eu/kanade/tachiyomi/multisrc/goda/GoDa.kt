package eu.kanade.tachiyomi.multisrc.goda

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import rx.Observable

open class GoDa(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest get() = true

    private val enableGenres = true

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient

    private fun getKey(link: String): String {
        return link.substringAfter("/manga/").removeSuffix("/")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hots/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup().also(::parseGenres)
        val mangas = document.select(".container > .cardlist .pb-2 a").map { element ->
            SManga.create().apply {
                val imgSrc = element.selectFirst("img")!!.attr("src")
                url = getKey(element.attr("href"))
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = if ("url=" in imgSrc) imgSrc.toHttpUrl().queryParameter("url")!! else imgSrc
            }
        }
        val nextPage = if (lang == "zh") "下一頁" else "NEXT"
        val hasNextPage = document.selectFirst("a[aria-label=$nextPage] button") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/newss/page/$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/s".toHttpUrl().newBuilder()
                .addPathSegment(query)
                .addEncodedQueryParameter("page", "$page")
                .build()
            return GET(url, headers)
        }
        for (filter in filters) {
            if (filter is UriPartFilter) return GET(baseUrl + filter.toUriPart() + "/page/$page", headers)
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(getMangaUrl(manga), headers)
    }

    private fun Element.getMangaId() = selectFirst("#mangachapters")!!.attr("data-mid")

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup().selectFirst("main")!!
        val titleElement = document.selectFirst("h1")!!
        val elements = titleElement.parent()!!.parent()!!.children()
        check(elements.size == 6)

        title = titleElement.ownText()
        status = when (titleElement.child(0).text()) {
            "連載中", "Ongoing" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = Entities.unescape(elements[1].children().drop(1).joinToString { it.text().removeSuffix(" ,") })
        genre = buildList {
            elements[2].children().drop(1).mapTo(this) { it.text().removeSuffix(" ,") }
            elements[3].children().mapTo(this) { it.text().removePrefix("#") }
        }.joinToString()
        description = (elements[4].text() + "\n\nID: ${document.getMangaId()}").trim()
        thumbnail_url = document.selectFirst("img.object-cover")!!.attr("src")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val mangaId = manga.description
            ?.substringAfterLast("ID: ", "")
            ?.takeIf { it.toIntOrNull() != null }
            ?: client.newCall(mangaDetailsRequest(manga)).execute().asJsoup().getMangaId()

        fetchChapterList(mangaId)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException()
    }

    open fun fetchChapterList(mangaId: String): List<SChapter> {
        val response = client.newCall(GET("$baseUrl/manga/get?mid=$mangaId&mode=all", headers)).execute()

        return response.asJsoup().select(".chapteritem").asReversed().map { element ->
            val anchor = element.selectFirst("a")!!
            SChapter.create().apply {
                url = getKey(anchor.attr("href")) + "#$mangaId/" + anchor.attr("data-cs")
                name = anchor.attr("data-ct")
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/" + chapter.url.substringBeforeLast('#')

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast('#', "")
        val mangaId = id.substringBefore('/', "")
        val chapterId = id.substringAfter('/', "")
        return pageListRequest(mangaId, chapterId)
    }

    open fun pageListRequest(mangaId: String, chapterId: String) = GET("$baseUrl/chapter/getcontent?m=$mangaId&c=$chapterId", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapcontent > div > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("data-src").ifEmpty { element.attr("src") })
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private var genres: Array<Pair<String, String>> = emptyArray()

    private fun parseGenres(document: Document) {
        if (!enableGenres || genres.isNotEmpty()) return
        val box = document.selectFirst("h2")?.parent()?.parent() ?: return
        val items = box.select("a")
        genres = Array(items.size) { i ->
            val item = items[i]
            Pair(item.text().removePrefix("#"), item.attr("href"))
        }
    }

    override fun getFilterList(): FilterList =
        if (!enableGenres) {
            FilterList()
        } else if (genres.isEmpty()) {
            FilterList(listOf(Filter.Header(if (lang == "zh") "点击“重置”刷新分类" else "Tap 'Reset' to load genres")))
        } else {
            val list = listOf(
                Filter.Header(if (lang == "zh") "分类（搜索文本时无效）" else "Filters are ignored when using text search."),
                UriPartFilter(if (lang == "zh") "分类" else "Genre", genres),
            )
            FilterList(list)
        }

    class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
