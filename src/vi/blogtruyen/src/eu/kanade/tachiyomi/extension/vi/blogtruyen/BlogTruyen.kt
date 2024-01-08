package eu.kanade.tachiyomi.extension.vi.blogtruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class BlogTruyen : ParsedHttpSource() {

    override val name = "BlogTruyen"

    override val baseUrl = "https://blogtruyenmoi.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    private val json: Json by injectLazy()

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH)

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_AUTHOR_SEARCH = "author:"
        const val PREFIX_TEAM_SEARCH = "team:"
    }

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/ajax/Search/AjaxLoadListManga?key=tatca&orderBy=3&p=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val manga = document.select(popularMangaSelector()).map {
            val tiptip = it.attr("data-tiptip")
            popularMangaFromElement(it, document.getElementById(tiptip)!!)
        }

        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaSelector() = ".list .tiptip"

    override fun popularMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException("Not used")

    private fun popularMangaFromElement(element: Element, tiptip: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.attr("title").replace("truyện tranh ", "").trim()

        thumbnail_url = tiptip.selectFirst("img")!!.attr("abs:src")
        description = tiptip.selectFirst(".al-j")!!.text()
    }

    override fun popularMangaNextPageSelector() = ".paging:last-child:not(.current_page)"

    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl + if (page != 1) "/page-$page" else "", headers)

    override fun latestUpdatesSelector() = ".storyitem .fl-l"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").attr("title")
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = "select.slcPaging option:last-child:not([selected])"

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                var id = query.removePrefix(PREFIX_ID_SEARCH).trim()

                // it's a chapter, resolve to manga ID
                if (id.startsWith("c")) {
                    val document = client.newCall(GET("$baseUrl/$id", headers)).execute().asJsoup()
                    id = document.selectFirst(".breadcrumbs a:last-child")!!.attr("href").removePrefix("/")
                }

                fetchMangaDetails(
                    SManga.create().apply {
                        url = "/$id"
                    },
                )
                    .map { MangasPage(listOf(it.apply { url = "/$id" }), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    private fun extractIdFromQuery(prefix: String, query: String): String {
        val q = query.substringAfter(prefix).trim()
        return if (q.contains("-")) {
            q.substringAfterLast("-")
        } else {
            q
        }
    }

    private val ajaxSearchUrls: Map<String, String> = mapOf(
        PREFIX_AUTHOR_SEARCH to "Author/AjaxLoadMangaByAuthor?orderBy=3",
        PREFIX_TEAM_SEARCH to "TranslateTeam/AjaxLoadMangaByTranslateTeam",
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        ajaxSearchUrls.keys.forEach {
            if (!query.startsWith(it)) {
                return@forEach
            }
            val id = extractIdFromQuery(it, query)
            val url = "$baseUrl/ajax/${ajaxSearchUrls[it]}".toHttpUrl().newBuilder()
                .addQueryParameter("id", id)
                .addQueryParameter("p", page.toString())
                .build()
                .toString()
            return GET(url, headers)
        }

        val url = "$baseUrl/timkiem/nangcao/1".toHttpUrl().newBuilder().apply {
            addQueryParameter("txt", query)
            addQueryParameter("p", page.toString())

            val genres = mutableListOf<Int>()
            val genresEx = mutableListOf<Int>()
            var status = 0
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> genres.add(it.id)
                            Filter.TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                            else -> {}
                        }
                    }
                    is Author -> {
                        addQueryParameter("aut", filter.state)
                    }
                    is Scanlator -> {
                        addQueryParameter("gr", filter.state)
                    }
                    is Status -> {
                        status = filter.state
                    }
                    else -> {}
                }
            }

            addPathSegment(status.toString())
            addPathSegment(genres.joinToString(","))
            addPathSegment(genresEx.joinToString(","))
        }.build().toString()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val manga = document.select(searchMangaSelector()).map {
            val tiptip = it.attr("data-tiptip")
            searchMangaFromElement(it, document.getElementById(tiptip)!!)
        }

        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null

        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException("Not used")

    private fun searchMangaFromElement(element: Element, tiptip: Element) =
        popularMangaFromElement(element, tiptip)

    override fun searchMangaNextPageSelector() = ".pagination .glyphicon-step-forward"

    private fun getMangaTitle(document: Document) = document.selectFirst(".entry-title a")!!
        .attr("title")
        .replaceFirst("truyện tranh", "", false)
        .trim()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val anchor = document.selectFirst(".entry-title a")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = getMangaTitle(document)

        thumbnail_url = document.select(".thumbnail img").attr("abs:src")
        author = document.select("a[href*=tac-gia]").joinToString { it.text() }
        genre = document.select("span.category a").joinToString { it.text() }
        status = parseStatus(
            document.select("span.color-red:not(.bold)").text(),
        )

        description = StringBuilder().apply {
            // the actual synopsis
            val synopsisBlock = document.selectFirst(".manga-detail .detail .content")!!

            // replace the facebook blockquote in synopsis with the link (if there is one)
            val fbElement = synopsisBlock.selectFirst(".fb-page, .fb-group")
            if (fbElement != null) {
                val fbLink = fbElement.attr("data-href")

                val node = document.createElement("p")
                node.appendText(fbLink)

                fbElement.replaceWith(node)
            }
            appendLine(synopsisBlock.textWithNewlines().trim())
            appendLine()

            // other metadata
            document.select(".description p").forEach {
                val text = it.text()
                if (text.contains("Thể loại") ||
                    text.contains("Tác giả") ||
                    text.isBlank()
                ) {
                    return@forEach
                }

                if (text.contains("Trạng thái")) {
                    appendLine(text.substringBefore("Trạng thái").trim())
                    return@forEach
                }

                if (text.contains("Nguồn") ||
                    text.contains("Tham gia update") ||
                    text.contains("Nhóm dịch")
                ) {
                    val key = text.substringBefore(":")
                    val value = it.select("a").joinToString { el -> el.text() }
                    appendLine("$key: $value")
                    return@forEach
                }

                it.select("a, span").append("\\n")
                appendLine(it.text().replace("\\n", "\n").replace("\n ", "\n").trim())
            }
        }.toString().trim()
    }

    private fun Element.textWithNewlines() = run {
        select("p").prepend("\\n")
        select("br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        status.contains("Tạm ngưng") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val title = getMangaTitle(document)
        return document.select(chapterListSelector()).map { chapterFromElement(it, title) }
    }

    override fun chapterListSelector() = "div.list-wrap > p"

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    private fun chapterFromElement(element: Element, title: String): SChapter = SChapter.create().apply {
        val anchor = element.select("span > a").first()!!

        setUrlWithoutDomain(anchor.attr("href"))
        name = anchor.attr("title").replace(title, "", true).trim()
        date_upload = runCatching {
            dateFormat.parse(
                element.selectFirst("span.publishedDate")!!.text(),
            )?.time
        }.getOrNull() ?: 0L
    }

    private fun countViewRequest(mangaId: String, chapterId: String): Request = POST(
        "$baseUrl/Chapter/UpdateView",
        headers,
        FormBody.Builder()
            .add("mangaId", mangaId)
            .add("chapterId", chapterId)
            .build(),
    )

    private fun countView(document: Document) {
        val mangaId = document.getElementById("MangaId")!!.attr("value")
        val chapterId = document.getElementById("ChapterId")!!.attr("value")
        runCatching {
            client.newCall(countViewRequest(mangaId, chapterId)).execute().close()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("#content > img").forEachIndexed { i, e ->
            pages.add(Page(i, imageUrl = e.attr("abs:src")))
        }

        // Some chapters use js script to render images
        document.select("#content > script:containsData(listImageCaption)").lastOrNull()
            ?.let { script ->
                val imagesStr = script.data().substringBefore(";").substringAfterLast("=").trim()
                val imageArr = json.parseToJsonElement(imagesStr).jsonArray
                imageArr.forEach {
                    val imageUrl = it.jsonObject["url"]!!.jsonPrimitive.content
                    pages.add(Page(pages.size, imageUrl = imageUrl))
                }
            }

        countView(document)
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    private class Status : Filter.Select<String>(
        "Status",
        arrayOf("Sao cũng được", "Đang tiến hành", "Đã hoàn thành", "Tạm ngưng"),
    )

    private class Author : Filter.Text("Tác giả")
    private class Scanlator : Filter.Text("Nhóm dịch")
    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    override fun getFilterList() = FilterList(
        Author(),
        Scanlator(),
        Status(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("16+", 54),
        Genre("18+", 45),
        Genre("Action", 1),
        Genre("Adult", 2),
        Genre("Adventure", 3),
        Genre("Anime", 4),
        Genre("Comedy", 5),
        Genre("Comic", 6),
        Genre("Doujinshi", 7),
        Genre("Drama", 49),
        Genre("Ecchi", 48),
        Genre("Even BT", 60),
        Genre("Fantasy", 50),
        Genre("Game", 61),
        Genre("Gender Bender", 51),
        Genre("Harem", 12),
        Genre("Historical", 13),
        Genre("Horror", 14),
        Genre("Isekai/Dị Giới", 63),
        Genre("Josei", 15),
        Genre("Live Action", 16),
        Genre("Magic", 46),
        Genre("Manga", 55),
        Genre("Manhua", 17),
        Genre("Manhwa", 18),
        Genre("Martial Arts", 19),
        Genre("Mature", 20),
        Genre("Mecha", 21),
        Genre("Mystery", 22),
        Genre("Nấu ăn", 56),
        Genre("NTR", 62),
        Genre("One shot", 23),
        Genre("Psychological", 24),
        Genre("Romance", 25),
        Genre("School Life", 26),
        Genre("Sci-fi", 27),
        Genre("Seinen", 28),
        Genre("Shoujo", 29),
        Genre("Shoujo Ai", 30),
        Genre("Shounen", 31),
        Genre("Shounen Ai", 32),
        Genre("Slice of Life", 33),
        Genre("Smut", 34),
        Genre("Soft Yaoi", 35),
        Genre("Soft Yuri", 36),
        Genre("Sports", 37),
        Genre("Supernatural", 38),
        Genre("Tạp chí truyện tranh", 39),
        Genre("Tragedy", 40),
        Genre("Trap", 58),
        Genre("Trinh thám", 57),
        Genre("Truyện scan", 41),
        Genre("Video clip", 53),
        Genre("VnComic", 42),
        Genre("Webtoon", 52),
        Genre("Yuri", 59),
    )
}
