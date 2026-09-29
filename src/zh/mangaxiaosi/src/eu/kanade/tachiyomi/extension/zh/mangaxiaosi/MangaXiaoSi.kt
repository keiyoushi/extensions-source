package eu.kanade.tachiyomi.extension.zh.mangaxiaosi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaXiaoSi : KeiSource() {

    // Set a desktop User-Agent to prevent the site from serving the mobile layout
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/rank").asJsoup()

        val popularSection = document.selectFirst(".mh-list.col3.top-cat > li:has(.title:contains(人气榜))")

        val mangas = popularSection?.select(".mh-item, .mh-itme-top")?.mapNotNull { element ->
            parseMangaFromElement(element, "h2.title a")
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/update?page=$page".toHttpUrl())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select(".mh-item").mapNotNull { element ->
            parseMangaFromElement(element, ".title a")
        }

        val hasNextPage = document.selectFirst("a#nextPage") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return getMangaList(url)
        } else {
            val url = "$baseUrl/booklist".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())

            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
            val areaFilter = filters.firstInstanceOrNull<AreaFilter>()
            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

            url.addQueryParameter("tag", genreFilter?.selectedValue() ?: "全部")
            url.addQueryParameter("area", areaFilter?.selectedValue() ?: "-1")
            url.addQueryParameter("end", statusFilter?.selectedValue() ?: "-1")

            return getMangaList(url.build())
        }
    }

    // ========================= Details & Chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        document.selectFirst(".banner_detail_form .info")?.let { info ->
            manga.apply {
                title = info.selectFirst("h1")?.text() ?: title
                author = info.selectFirst(".subtitle:contains(作者)")?.text()?.substringAfter("：")?.trim()
                status = parseStatus(info.selectFirst(".tip span.block:contains(状态) span")?.text())
                genre = info.select(".tip span.block:contains(标签) a").joinToString { it.text() }
                description = info.selectFirst(".content")?.text()
                thumbnail_url = document.selectFirst(".banner_detail_form .cover img")?.absUrl("src")
            }
        }

        val updateDateText = document.selectFirst(".tip span.block:contains(更新时间)")?.text()?.substringAfter("：")?.trim()
        val updateDate = dateFormat.tryParseDate(updateDateText, ZoneId.of("Asia/Shanghai"))

        val chapterList = document.select("#detail-list-select li a").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }.reversed()

        if (chapterList.isNotEmpty() && updateDate != 0L) {
            chapterList[0].date_upload = updateDate
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".comicpage img").mapIndexed { index, element ->
            val url = element.absUrl("data-original").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
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
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}
