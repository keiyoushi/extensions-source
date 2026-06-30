package eu.kanade.tachiyomi.extension.zh.roumanwu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Roumanwu : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder().addInterceptor(ScrambledImageInterceptor()).build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)

    private fun parseEntries(container: Element): List<SManga> = container.select("a[href*=/books/]").map {
        SManga.create().apply {
            title = it.selectFirst("div.truncate")!!.text()
            url = it.attr("href")
            thumbnail_url = it.selectFirst("div.bg-cover")!!.attr("style").substringAfter("background-image:url(\"").substringBefore("\")")
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("正熱門|今日最佳|本週熱門"))
    }

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.selectFirst("div.px-1")!!.children().flatMap { section ->
            if (section.child(0).text().contains(sections)) {
                parseEntries(section)
            } else {
                emptyList()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("最近更新"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (query.isNotBlank()) {
        GET("$baseUrl/search?term=$query&page=${page - 1}", headers)
    } else {
        val parts = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
        GET("$baseUrl/books?page=${page - 1}$parts", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = parseEntries(document)
        val hasNextPage = document.selectFirst("div.justify-end > a:contains(下一頁)") != null
        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val infobox = parseInfobox(document).iterator()

        title = infobox.next()
        thumbnail_url = document.selectFirst("div.basis-2\\/5 img")!!.absUrl("src")
            .run { toHttpUrl().queryParameter("url") ?: this }
        description = document.selectFirst("p:contains(簡介:)")!!.text().substring(3)

        val genres = ArrayList<String>()
        for (text in infobox) {
            val value = text.drop(3).trimStart()
            if (value.isEmpty()) continue
            when (text.take(3)) {
                "別名:" -> if (value != title) description = "$text\n\n$description"

                "作者:" -> author = value

                "狀態:" -> status = when (value) {
                    "連載中" -> SManga.ONGOING
                    "已完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }

                "地區:" -> genres.add(value)

                "標籤:" -> genres.addAll(value.split(","))
            }
        }
        genre = genres.joinToString()
    }

    private fun parseInfobox(document: Document): List<String> {
        val infobox = document.selectFirst("div.basis-3\\/5")!!.children()
        check(infobox.size >= 6 && infobox[0].hasClass("text-xl"))
        return infobox.map { it.text() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a[href~=/books/.*/\\d+]").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.text()
            }
        }.asReversed()
        if (chapters.isNotEmpty()) {
            for (text in parseInfobox(document).asReversed()) {
                val date = DATE_FORMAT.tryParse(text)
                if (date != 0L) {
                    chapters[0].date_upload = date
                    break
                }
            }
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Rendered HTML might have links sitting on the boundary of two scripts
        return super.pageListRequest(chapter).newBuilder().addHeader("rsc", "1").build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        return IMAGE_URL_REGEX.findAll(html).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = match.groupValues[1])
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() = when (state) {
            1 -> "&continued=true"
            2 -> "&continued=false"
            else -> ""
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("M/d/yyyy", Locale.ROOT)
        private val IMAGE_URL_REGEX = Regex(""""imageUrl":"([^"]+)""")
    }
}
