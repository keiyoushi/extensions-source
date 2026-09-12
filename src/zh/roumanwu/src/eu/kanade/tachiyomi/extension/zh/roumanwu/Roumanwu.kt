package eu.kanade.tachiyomi.extension.zh.roumanwu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParse
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

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/books?page=${page - 1}", headers)

    private fun parseEntries(container: Element): List<SManga> = container.select("a.site-comic").map {
        SManga.create().apply {
            title = it.selectFirst("h3")!!.text()
            url = it.attr("href")
            thumbnail_url = it.selectFirst("img")!!.absUrl("src")
        }
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        return MangasPage(parseEntries(document), hasNextPage(document))
    }

    // 页码文案形如 "1 / 103"；末页「下一頁」会变成 disabled button
    private fun hasNextPage(document: Document): Boolean {
        val parts = document.selectFirst(".site-pagination-mobile")?.text()?.split('/').orEmpty()
        val current = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
        val total = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return false
        return current < total
    }

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.selectFirst("div.site-home")!!.children().flatMap { section ->
            val heading = section.selectFirst(".site-section-heading")?.text().orEmpty()
            if (heading.contains(sections)) {
                parseEntries(section)
            } else {
                emptyList()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/home", headers)

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

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst("div.site-book-info")!!

        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.site-detail-cover")!!.absUrl("src")

        val alias = info.selectFirst("p.site-book-alias")?.text()
        val synopsis = document.selectFirst("div.site-book-synopsis")?.text().orEmpty()
        description = if (!alias.isNullOrEmpty() && alias != title) {
            "別名: $alias\n\n$synopsis"
        } else {
            synopsis
        }

        val data = info.select("dl.site-book-data dt").associate { dt ->
            dt.text() to dt.nextElementSibling()?.text().orEmpty()
        }
        author = data["作者"]
        status = when {
            data["狀態"]?.startsWith("連載中") == true -> SManga.ONGOING
            data["狀態"]?.startsWith("已完結") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = ArrayList<String>()
        data["地區"]?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
        info.selectFirst("p.site-eyebrow")?.text()
            ?.substringBefore("/")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { genres.add(it) }
        genre = genres.joinToString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a.site-chapter-link").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.selectFirst("span")!!.attr("title")
            }
        }.asReversed()
        if (chapters.isNotEmpty()) {
            val date = DATE_FORMAT.tryParse(
                document.selectFirst("dl.site-book-data dt:contains(更新) + dd")?.text(),
            )
            if (date != 0L) {
                chapters[0].date_upload = date
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
