package eu.kanade.tachiyomi.extension.zh.mangabz

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator

abstract class MangabzTheme(
    override val name: String,
) : HttpSource() {

    override val lang = "zh"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list-p$page/", headers)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list-0-0-2-p$page/", headers)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isEmpty()) {
            popularMangaRequest(page)
        } else {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("title", query)
                .addQueryParameter("page", page.toString())
            Request.Builder().url(url.build()).headers(headers).build()
        }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup().also(::parseFilters)
        val mangas = document.selectFirst(Evaluator.Class("mh-list"))!!.children().map { element ->
            SManga.create().apply {
                title = element.selectFirst(Evaluator.Tag("h2"))!!.text()
                url = element.selectFirst(Evaluator.Tag("a"))!!.attr("href")
                thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.attr("src")
            }
        }
        val hasNextPage = document.run {
            val pagination = selectFirst(Evaluator.Class("page-pagination"))
            pagination != null && pagination.select(Evaluator.Tag("a")).last()!!.text() == ">"
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val details = document.selectFirst(Evaluator.Class("detail-info-tip"))!!.children()
        return SManga.create().apply {
            url = document.location().removePrefix(baseUrl)
            title = document.selectFirst(Evaluator.Class("detail-info-title"))!!.ownText()
            thumbnail_url = document.selectFirst(Evaluator.Class("detail-info-cover"))!!.attr("src")
            status = when (details[1].child(0).ownText()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                "連載中" -> SManga.ONGOING
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = details[0].children().joinToString { it.ownText() }
            genre = details[2].children().joinToString { it.ownText() }
            description = parseDescription(document.selectFirst(Evaluator.Class("detail-info-content"))!!, title, details)
            initialized = true
        }
    }

    abstract fun parseDescription(element: Element, title: String, details: Elements): String

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val needPageCount = needPageCount
        val list = getChapterElements(document).map { element ->
            val chapterName = element.ownText()
            SChapter.create().apply {
                url = element.attr("href")
                if (needPageCount) {
                    name = chapterName + element.child(0).ownText()
                    chapter_number = when (val result = floatRegex.find(chapterName)) {
                        null -> -2f
                        else -> result.value.toFloat()
                    }
                } else {
                    name = chapterName
                }
            }
        }
        if (list.isEmpty()) return emptyList()

        val listTitle = document.selectFirst(Evaluator.Class("detail-list-form-title"))!!.ownText()
        try {
            list[0].date_upload = parseDate(listTitle)
        } catch (e: Throwable) {
            Log.e("Mangabz/$name", "failed to parse date from '$listTitle'", e)
        }
        return list
    }

    protected open fun getChapterElements(document: Document): Elements =
        document.selectFirst(Evaluator.Id("chapterlistload"))!!.children()

    protected open val needPageCount = true

    protected abstract fun parseDate(listTitle: String): Long

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    protected open fun parseFilters(document: Document) = Unit

    private val floatRegex by lazy { Regex("""\d+(?:\.\d+)?""") }
}
