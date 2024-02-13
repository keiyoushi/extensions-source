package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Evaluator
import rx.Observable

class Manhuagold : MangaReader() {

    override val name = "Manhuagold"

    override val lang = "en"

    override val baseUrl = "https://manhuagold.com"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/filter/$page/?sort=views&sex=All&chapter_count=0", headers)

    // Latest

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/filter/$page/?sort=latest-updated&sex=All&chapter_count=0", headers)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search").apply {
                addQueryParameter("keyword", query)
            }
        } else {
            urlBuilder.addPathSegment("filter").apply {
                filters.ifEmpty(::getFilterList).forEach { filter ->
                    when (filter) {
                        is Select -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        is GenresFilter -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        else -> {}
                    }
                }
            }
        }

        urlBuilder.addPathSegment(page.toString())
        urlBuilder.addPathSegment("")

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaSelector() = ".manga_list-sbs .manga-poster"

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            element.selectFirst(Evaluator.Tag("img"))!!.let {
                title = it.attr("alt")
                thumbnail_url = it.imgAttr()
            }
        }

    override fun searchMangaNextPageSelector() = "ul.pagination > li.active + li"

    // Filters

    override fun getFilterList() =
        FilterList(
            Note,
            StatusFilter(),
            SortFilter(),
            GenresFilter(),
        )

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val root = document.selectFirst(Evaluator.Id("ani_detail"))!!
        val mangaTitle = root.selectFirst(Evaluator.Class("manga-name"))!!.ownText()
        title = mangaTitle
        description = root.run {
            val description = selectFirst(Evaluator.Class("description"))!!.ownText()
            when (val altTitle = selectFirst(Evaluator.Class("manga-name-or"))!!.ownText()) {
                "", mangaTitle -> description
                else -> "$description\n\nAlternative Title: $altTitle"
            }
        }
        thumbnail_url = root.selectFirst(Evaluator.Tag("img"))!!.imgAttr()
        genre = root.selectFirst(Evaluator.Class("genres"))!!.children().joinToString { it.ownText() }
        for (item in root.selectFirst(Evaluator.Class("anisc-info"))!!.children()) {
            if (item.hasClass("item").not()) continue
            when (item.selectFirst(Evaluator.Class("item-head"))!!.ownText()) {
                "Authors:" -> item.parseAuthorsTo(this)
                "Status:" -> status = when (item.selectFirst(Evaluator.Class("name"))!!.ownText().lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    "on-hold" -> SManga.ON_HIATUS
                    "canceled" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    private fun Element.parseAuthorsTo(manga: SManga) {
        val authors = select(Evaluator.Tag("a"))
        val text = authors.map { it.ownText().replace(",", "") }
        val count = authors.size
        when (count) {
            0 -> return
            1 -> {
                manga.author = text[0]
                return
            }
        }
        val authorList = ArrayList<String>(count)
        val artistList = ArrayList<String>(count)
        for ((index, author) in authors.withIndex()) {
            val textNode = author.nextSibling() as? TextNode
            val list = if (textNode != null && "(Art)" in textNode.wholeText) artistList else authorList
            list.add(text[index])
        }
        if (authorList.isEmpty().not()) manga.author = authorList.joinToString()
        if (artistList.isEmpty().not()) manga.artist = artistList.joinToString()
    }

    // Chapters

    override fun chapterListRequest(mangaUrl: String, type: String): Request =
        GET(baseUrl + mangaUrl, headers)

    override fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> {
        TODO("Not yet implemented")
    }

    override val chapterType = ""
    override val volumeType = ""

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map(::parseChapterList)
    }

    private fun parseChapterList(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }

        return document.select(chapterListSelector())
            .map(::chapterFromElement)
    }

    private fun chapterListSelector(): String = "#chapters-list > li"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href"))
            name = selectFirst(".name")?.text() ?: text()
        }
    }

    // Images

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val document = client.newCall(pageListRequest(chapter)).execute().asJsoup()

        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")!!.data()
        val id = script.substringAfter("const CHAPTER_ID = ").substringBefore(";")

        val ajaxHeaders = super.headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Referer", baseUrl + chapter.url)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val ajaxUrl = "$baseUrl/ajax/image/list/chap/$id"
        client.newCall(GET(ajaxUrl, ajaxHeaders)).execute().let(::pageListParse)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.use { it.parseHtmlProperty() }

        val pageList = document.select("div").map {
            val index = it.attr("data-number").toInt()
            val imgUrl = it.imgAttr().ifEmpty { it.selectFirst("img")!!.imgAttr() }

            Page(index, "", imgUrl)
        }

        return pageList
    }

    // Utilities

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun Response.parseHtmlProperty(): Document {
        val html = Json.parseToJsonElement(body.string()).jsonObject["html"]!!.jsonPrimitive.content
        return Jsoup.parseBodyFragment(html)
    }
}
