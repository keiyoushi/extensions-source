package eu.kanade.tachiyomi.extension.en.yaoitoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

class YaoiToon : HttpSource() {

    override val name = "YaoiToon"

    override val baseUrl = "https://yaoitoon.net"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    private val ajaxHeaders by lazy {
        headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/filter/$page/?sort=most-viewd&sex=All&chapter_count=0", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".manga_list-sbs .mls-wrap .item").mapNotNull { element ->
            val titleElement = element.selectFirst(".manga-name a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(titleElement.attr("abs:href"))
                title = titleElement.text()
                thumbnail_url = element.selectFirst(".manga-poster img")?.let {
                    val dataSrc = it.attr("data-src")
                    if (dataSrc.isNotEmpty()) {
                        if (dataSrc.startsWith("/")) baseUrl + dataSrc else dataSrc
                    } else {
                        it.attr("abs:src")
                    }
                }
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li.page-item a:contains(›)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/filter/$page/?sort=latest-updated&sex=All&chapter_count=0", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/$page/".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/filter/$page/".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.selectedValue())
                is GenreList -> {
                    val selected = filter.state.filter { it.state }
                    if (selected.isNotEmpty()) {
                        val genres = selected.joinToString(",") { it.id }
                        url.addQueryParameter("genres", genres)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".anisc-detail .manga-name")!!.text()
            thumbnail_url = document.selectFirst(".anisc-poster img")?.let {
                val dataSrc = it.attr("data-src")
                if (dataSrc.isNotEmpty()) {
                    if (dataSrc.startsWith("/")) baseUrl + dataSrc else dataSrc
                } else {
                    it.attr("abs:src")
                }
            }
            description = document.select(".description").text()
            genre = document.select(".genres a").joinToString(", ") { it.text() }
            status = parseStatus(document.selectFirst(".item-title:contains(Status) .name")?.text())
        }
    }

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on-hold" -> SManga.ON_HIATUS
        "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul#chapters-list li.chapter-item").mapNotNull { element ->
            val link = element.selectFirst("a.item-link") ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                name = element.selectFirst(".name")!!.text()
                date_upload = parseRelativeDate(element.selectFirst(".release-time")?.text())
            }
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        val match = dateRegex.find(dateStr.trim()) ?: return 0L
        val number = match.groupValues[1].toIntOrNull() ?: return 0L
        val unit = match.groupValues[2]

        val calendar = Calendar.getInstance()
        when (unit) {
            "s" -> calendar.add(Calendar.SECOND, -number)
            "m" -> calendar.add(Calendar.MINUTE, -number)
            "M" -> calendar.add(Calendar.MONTH, -number)
            "h" -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            "d" -> calendar.add(Calendar.DAY_OF_YEAR, -number)
            "w" -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            "y" -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }
        return calendar.timeInMillis
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$baseUrl/ajax/image/list/chap/$chapterId", ajaxHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<Dto>()
        val document = Jsoup.parseBodyFragment(dto.html, baseUrl)

        return document.select(".separator").mapIndexed { i, element ->
            val dataSrc = element.attr("data-src")
            val imageUrl = if (dataSrc.isNotEmpty()) {
                if (dataSrc.startsWith("/")) baseUrl + dataSrc else dataSrc
            } else {
                element.selectFirst("img")?.attr("abs:src") ?: ""
            }
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = getFilters()

    companion object {
        private val dateRegex = """(\d+)([a-zA-Z]+)\s+ago""".toRegex()
    }
}
