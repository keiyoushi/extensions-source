package eu.kanade.tachiyomi.extension.zh.mangaxiaosi

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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaXiaoSi : HttpSource() {

    override val name = "Manga Xiao Si"

    override val baseUrl = "https://www.jjmhw2.top"

    override val lang = "zh"

    override val supportsLatest = true

    // Set a desktop User-Agent to prevent the site from serving the mobile layout
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rank", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val popularSection = document.selectFirst(".mh-list.col3.top-cat > li:has(.title:contains(人气榜))")

        val mangas = popularSection?.select(".mh-item, .mh-itme-top")?.mapNotNull { element ->
            parseMangaFromElement(element, "h2.title a")
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/update?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".mh-item").mapNotNull { element ->
            parseMangaFromElement(element, ".title a")
        }

        val hasNextPage = document.selectFirst("a#nextPage") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        } else {
            val url = "$baseUrl/booklist".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())

            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
            val areaFilter = filters.firstInstanceOrNull<AreaFilter>()
            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

            url.addQueryParameter("tag", genreFilter?.selectedValue() ?: "全部")
            url.addQueryParameter("area", areaFilter?.selectedValue() ?: "-1")
            url.addQueryParameter("end", statusFilter?.selectedValue() ?: "-1")

            return GET(url.build(), headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            val info = document.selectFirst(".banner_detail_form .info") ?: return this

            title = info.selectFirst("h1")?.text() ?: ""
            author = info.selectFirst(".subtitle:contains(作者)")?.text()?.substringAfter("：")?.trim()
            status = parseStatus(info.selectFirst(".tip span.block:contains(状态) span")?.text())
            genre = info.select(".tip span.block:contains(标签) a").joinToString { it.text() }
            description = info.selectFirst(".content")?.text()
            thumbnail_url = document.selectFirst(".banner_detail_form .cover img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val updateDateText = document.selectFirst(".tip span.block:contains(更新时间)")?.text()?.substringAfter("：")?.trim()
        val updateDate = dateFormat.tryParse(updateDateText)

        val chapters = document.select("#detail-list-select li a").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }.reversed()

        if (chapters.isNotEmpty() && updateDate != 0L) {
            chapters[0].date_upload = updateDate
        }

        return chapters
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".comicpage img").mapIndexed { index, element ->
            val url = element.absUrl("data-original").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Filter.Header("注意：搜索时不支持分类过滤"),
        Filter.Separator(),
        GenreFilter(),
        AreaFilter(),
        StatusFilter(),
    )

    // ============================= Utilities =============================

    private fun parseMangaFromElement(element: Element, titleSelector: String): SManga? {
        val a = element.selectFirst(titleSelector) ?: return null
        return SManga.create().apply {
            title = a.text()
            setUrlWithoutDomain(a.attr("abs:href"))
            thumbnail_url = parseThumbnailFromStyle(element)
        }
    }

    private fun parseThumbnailFromStyle(element: Element): String? {
        val style = element.selectFirst(".mh-cover")?.attr("style") ?: return null
        if (!style.contains("url(")) return null
        return style.substringAfter("url(").substringBefore(")").removeSurrounding("\"").removeSurrounding("'")
    }

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
        }
    }
}
