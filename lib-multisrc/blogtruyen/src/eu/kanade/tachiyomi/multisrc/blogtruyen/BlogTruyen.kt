package eu.kanade.tachiyomi.multisrc.blogtruyen

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

abstract class BlogTruyen(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    override fun popularMangaRequest(page: Int) =
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

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    private fun popularMangaFromElement(element: Element, tiptip: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a")!!

        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = tiptip.selectFirst("img")?.absUrl("src")
        description = tiptip.selectFirst(".al-j")?.text()
    }

    override fun popularMangaNextPageSelector() = ".paging:last-child:not(.current_page)"

    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl + if (page > 1) "/page-$page" else "", headers)

    override fun latestUpdatesSelector() = ".storyitem .fl-l"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst("a")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        title = anchor.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesNextPageSelector() = "select.slcPaging option:last-child:not([selected])"

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            var id = query.removePrefix(PREFIX_ID_SEARCH).trimStart()

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
                .map { MangasPage(listOf(it), false) }
        }
        else -> super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        ajaxSearchUrls.keys
            .firstOrNull { query.startsWith(it) }
            ?.let {
                val id = extractIdFromQuery(it, query)
                val url = "$baseUrl/ajax/${ajaxSearchUrls[it]!!}".toHttpUrl().newBuilder()
                    .addQueryParameter("id", id)
                    .addQueryParameter("p", page.toString())
                    .build()

                return GET(url, headers)
            }

        val url = "$baseUrl/timkiem/nangcao/1".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("txt", query)
            }

            if (page > 1) {
                addQueryParameter("p", page.toString())
            }

            val inclGenres = mutableListOf<String>()
            val exclGenres = mutableListOf<String>()
            var status = 0

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> inclGenres.add(it.id)
                            Filter.TriState.STATE_EXCLUDE -> exclGenres.add(it.id)
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
            addPathSegment(inclGenres.joinToString(",").ifEmpty { "-1" })
            addPathSegment(exclGenres.joinToString(",").ifEmpty { "-1" })
        }.build()

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
        throw UnsupportedOperationException()

    private fun searchMangaFromElement(element: Element, tiptip: Element) =
        popularMangaFromElement(element, tiptip)

    override fun searchMangaNextPageSelector() = ".pagination .glyphicon-step-forward"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val anchor = document.selectFirst(".entry-title a")!!
        val descriptionBlock = document.selectFirst("div.description")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        title = getMangaTitle(document)
        thumbnail_url = document.selectFirst(".thumbnail img")?.absUrl("src")
        author = descriptionBlock.select("p:contains(Tác giả) a").joinToString { it.text() }
        genre = descriptionBlock.select("span.category").joinToString { it.text() }
        status = when (descriptionBlock.selectFirst("p:contains(Trạng thái) span.color-red")?.text()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Đã hoàn thành" -> SManga.COMPLETED
            "Tạm ngưng" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            document.selectFirst(".manga-detail .detail .content")?.let {
                // replace the facebook blockquote in synopsis with the link (if there is one)
                it.selectFirst(".fb-page, .fb-group")?.let { fb ->
                    val link = fb.attr("data-href")
                    val node = document.createElement("p")

                    node.appendText(link)
                    fb.replaceWith(node)
                }

                appendLine(it.textWithNewlines().trim())
                appendLine()
            }

            descriptionBlock.select("p:not(:contains(Thể loại)):not(:contains(Tác giả))")
                .forEach { e ->
                    val text = e.text()

                    if (text.isBlank()) {
                        return@forEach
                    }

                    // Uploader and status share the same <p>
                    if (text.contains("Trạng thái")) {
                        appendLine(text.substringBefore("Trạng thái").trim())
                        return@forEach
                    }

                    // "Source", "Updaters" and "Scanlators" use badges with links
                    if (text.contains("Nguồn") ||
                        text.contains("Tham gia update") ||
                        text.contains("Nhóm dịch")
                    ) {
                        val key = text.substringBefore(":")
                        val value = e.select("a").joinToString { el -> el.text() }
                        appendLine("$key: $value")
                        return@forEach
                    }

                    // Generic paragraphs i.e. view count and follower count for this series
                    // Basically the same trick as [Element.textWithNewlines], just applied to
                    // different elements.
                    e.select("a, span").append("\\n")
                    appendLine(
                        e.text()
                            .replace("\\n", "\n")
                            .replace("\n ", "\n")
                            .trim(),
                    )
                }
        }.trim()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val title = getMangaTitle(document)
        return document.select(chapterListSelector()).map { chapterFromElement(it, title) }
    }

    override fun chapterListSelector() = "div.list-wrap > p"

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    private fun chapterFromElement(element: Element, title: String): SChapter = SChapter.create().apply {
        val anchor = element.select("span > a").first()!!

        setUrlWithoutDomain(anchor.attr("href"))
        name = anchor.text().removePrefix("$title ")
        date_upload = runCatching {
            dateFormat.parse(
                element.selectFirst("span.publishedDate")!!.text(),
            )!!.time
        }.getOrDefault(0L)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select(".content > img, #content > img").forEachIndexed { i, e ->
            pages.add(Page(i, imageUrl = e.absUrl("src")))
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

        runCatching { countView(document) }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Author(),
            Scanlator(),
            Status(),
        )
        val genres = getGenreList()

        if (genres.isNotEmpty()) {
            filters.add(GenreList(genres))
        }

        return FilterList(filters)
    }

    // copy([...document.querySelectorAll(".CategoryFilter li")].map((e) => `Genre("${e.textContent.trim()}", "${e.dataset.id}"),`).join("\n"))
    open fun getGenreList(): List<Genre> = emptyList()

    private class Status : Filter.Select<String>(
        "Status",
        arrayOf("Sao cũng được", "Đang tiến hành", "Đã hoàn thành", "Tạm ngưng"),
    )
    private class Author : Filter.Text("Tác giả")
    private class Scanlator : Filter.Text("Nhóm dịch")
    class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    private fun getMangaTitle(document: Document) =
        document
            .selectFirst(".entry-title a")!!
            .attr("title")
            .removePrefix("truyện tranh ")

    private fun Element.textWithNewlines() = run {
        select("p, br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }

    private fun extractIdFromQuery(prefix: String, query: String): String =
        query.substringAfter(prefix).trimStart().substringAfterLast("-")

    private fun countView(document: Document) {
        val mangaId = document.getElementById("MangaId")!!.attr("value")
        val chapterId = document.getElementById("ChapterId")!!.attr("value")
        val request = POST(
            "$baseUrl/Chapter/UpdateView",
            headers,
            FormBody.Builder()
                .add("mangaId", mangaId)
                .add("chapterId", chapterId)
                .build(),
        )

        Single.fromCallable {
            try {
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("BlogTruyen", "Error updating view count", e)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe()
    }

    private val ajaxSearchUrls: Map<String, String> = mapOf(
        PREFIX_AUTHOR_SEARCH to "Author/AjaxLoadMangaByAuthor?orderBy=3",
        PREFIX_TEAM_SEARCH to "TranslateTeam/AjaxLoadMangaByTranslateTeam",
    )

    companion object {
        internal const val PREFIX_ID_SEARCH = "id:"
        internal const val PREFIX_AUTHOR_SEARCH = "author:"
        internal const val PREFIX_TEAM_SEARCH = "team:"
    }
}
