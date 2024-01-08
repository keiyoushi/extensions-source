package eu.kanade.tachiyomi.extension.en.dynasty

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

abstract class DynastyScans : ParsedHttpSource() {

    override val baseUrl = "https://dynasty-scans.com"

    abstract fun popularMangaInitialUrl(): String

    override val lang = "en"

    override val supportsLatest = false

    open val searchPrefix = ""

    private var parent: List<Node> = ArrayList()

    private var list = InternalList(ArrayList(), "")

    private var imgList = InternalList(ArrayList(), "")

    private var _valid: Validate = Validate(false, -1)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        return GET(popularMangaInitialUrl(), headers)
    }

    override fun popularMangaSelector() = "ul.thumbnails > li.span2"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("div.caption").text()
        return manga
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("manga:")) {
            return if (query.startsWith("manga:$searchPrefix:")) {
                val newQuery = query.removePrefix("manga:$searchPrefix:")
                client.newCall(GET("$baseUrl/$searchPrefix/$newQuery"))
                    .asObservableSuccess()
                    .map { response ->
                        val details = mangaDetailsParse(response)
                        details.url = "/$searchPrefix/$newQuery"
                        MangasPage(listOf(details), false)
                    }
            } else {
                return Observable.just(MangasPage(ArrayList<SManga>(0), false))
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = "a.name"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.pagination > ul > li.active + li > a"

    private fun buildListfromResponse(): List<Node> {
        return client.newCall(
            Request.Builder().headers(headers)
                .url(popularMangaInitialUrl()).build(),
        ).execute().asJsoup()
            .select("div#main").first { it.hasText() }.childNodes()
    }

    protected fun parseHeader(document: Document, manga: SManga): Boolean {
        manga.title = document.selectFirst("div.tags > h2.tag-title > b")!!.text()
        val elements = document.selectFirst("div.tags > h2.tag-title")!!.getElementsByTag("a")
        if (elements.isEmpty()) {
            return false
        }
        if (elements.lastIndex == 0) {
            manga.author = elements[0].text()
        } else {
            manga.artist = elements[0].text()
            manga.author = elements[1].text()
        }
        manga.status = document.select("div.tags > h2.tag-title > small").text().let {
            when {
                it.contains("Ongoing") -> SManga.ONGOING
                it.contains("Completed") -> SManga.COMPLETED
                it.contains("Licensed") -> SManga.LICENSED
                else -> SManga.UNKNOWN
            }
        }
        return true
    }

    protected fun parseGenres(document: Document, manga: SManga, select: String = "div.tags > div.tag-tags a") {
        val tagElements = document.select(select)
        val doujinElements = document.select("div.tags >  h2.tag-title > small > a[href*=doujins]")
        tagElements.addAll(doujinElements)
        parseGenres(tagElements, manga)
    }

    protected fun parseGenres(elements: Elements, manga: SManga) {
        if (!elements.isEmpty()) {
            val genres = mutableListOf<String>()
            elements.forEach {
                genres.add(it.text())
            }
            manga.genre = genres.joinToString(", ")
        }
    }

    protected fun parseDescription(document: Document, manga: SManga) {
        manga.description = document.select("div.tags > div.row div.description").text()
    }

    private fun getValid(manga: SManga): Validate {
        if (parent.isEmpty()) parent = buildListfromResponse()
        if (list.isEmpty()) list = InternalList(parent, "href")
        if (imgList.isEmpty()) imgList = InternalList(parent, "src")
        val pos = list.indexOf(manga.url.substringBeforeLast("/") + "/" + Uri.encode(manga.url.substringAfterLast("/")))
        return Validate((pos > -1), pos)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        _valid = getValid(manga)
        return manga
    }

    override fun chapterListSelector() = "div.span10 > dl.chapter-list > dd"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val nodes = InternalList(element.childNodes(), "text")

        chapter.setUrlWithoutDomain(element.select("a.name").attr("href"))
        chapter.name = nodes[0]
        if (nodes.contains(" by ")) {
            chapter.name += " by ${nodes[nodes.indexOfPartial(" by ") + 1]}"
            if (nodes.contains(" and ")) {
                chapter.name += " and ${nodes[nodes.indexOfPartial(" and ") + 1]}"
            }
        }
        chapter.date_upload = nodes[nodes.indexOfPartial("released")]
            .substringAfter("released ")
            .replace("\'", "")
            .toDate("MMM dd yy")
        return chapter
    }

    protected fun String?.toDate(pattern: String): Long {
        this ?: return 0
        return try {
            SimpleDateFormat(pattern, Locale.ENGLISH).parse(this)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return try {
            val imageUrl = document.select("script").last()!!.html().substringAfter("var pages = [").substringBefore("];")

            json.parseToJsonElement("[$imageUrl]").jsonArray.mapIndexed { index, it ->
                Page(index, imageUrl = "$baseUrl${it.jsonObject["image"]!!.jsonPrimitive.content}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    class InternalList(nodes: List<Node>, type: String) : ArrayList<String>() {

        init {
            if (type == "text") {
                for (node in nodes) {
                    if (node is TextNode) {
                        if (node.text() != " " && !node.text().contains("\n")) {
                            this.add(node.text())
                        }
                    } else if (node is Element) this.add(node.text())
                }
            }
            if (type == "src") {
                nodes
                    .filter { it is Element && it.hasClass("thumbnails") }
                    .flatMap { it.childNodes() }
                    .filterIsInstance<Element>()
                    .filter { it.hasClass("span2") }
                    .forEach { this.add(it.child(0).child(0).attr(type)) }
            }
            if (type == "href") {
                nodes
                    .filter { it is Element && it.hasClass("thumbnails") }
                    .flatMap { it.childNodes() }
                    .filterIsInstance<Element>()
                    .filter { it.hasClass("span2") }
                    .forEach { this.add(it.child(0).attr(type)) }
            }
        }

        fun indexOfPartial(partial: String): Int {
            return (0..this.lastIndex).firstOrNull { this[it].contains(partial) }
                ?: -1
        }
    }

    data class Validate(val _isManga: Boolean, val _pos: Int)

    override fun popularMangaNextPageSelector() = ""
    override fun latestUpdatesSelector() = ""
    override fun latestUpdatesNextPageSelector() = ""
    override fun imageUrlParse(document: Document): String = ""
    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return popularMangaRequest(page)
    }
}
