package eu.kanade.tachiyomi.extension.zh.ikanmh

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
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 漫小肆韓漫 (ikanmh.xyz)
 *
 * 站点特征：
 * - 自研 PHP 模板；列表/详情/正文均为服务端渲染 HTML，封面与正文图在 CDN www.jjmhw10.top
 * - 热门与筛选浏览共用 /booklist/?tag=&area=&end=&page=（每页 21 条；末页条数不足 21，下一页按钮为 disabled）
 * - 最新 /update?page=（每页 12 条；末页条数仍可能为 12，下一页按钮才 disabled，不能用条数收敛）
 * - 搜索 /search?keyword=（无分页，恒 hasNextPage=false）
 * - 详情 /book/{id}；章节列表在同一详情页（旧→新），无 per-chapter 日期，仅整书「yyyy-MM-dd更新」
 * - 正文 #cp_img 内 img[data-original]（绝对 CDN URL）
 *
 * name / lang / id / baseUrl 由 build.gradle.kts 的 keiyoushi 块注入。
 */
@Source
abstract class Ikanmh : KeiSource() {

    // ---- 热门 ----

    override suspend fun getPopularManga(page: Int): MangasPage = browseBooklist(
        page = page,
        tag = "全部",
        area = "-1",
        end = "-1",
    )

    // ---- 最新 ----

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/update".toHttpUrl().newBuilder()
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseMangaListCards(document), document.hasNextPage())
    }

    // ---- 搜索与筛选 ----

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queryTrimmed = query.trim()
        if (queryTrimmed.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", queryTrimmed)
                .build()
            val document = client.get(url).asJsoup()
            // 站点搜索无分页
            return MangasPage(parseSearchCards(document), hasNextPage = false)
        }

        var tag = "全部"
        var area = "-1"
        var end = "-1"
        filters.forEach { filter ->
            when (filter) {
                is TagFilter -> tag = filter.toUriPart()
                is AreaFilter -> area = filter.toUriPart()
                is StatusFilter -> end = filter.toUriPart()
                else -> {}
            }
        }
        return browseBooklist(page, tag, area, end)
    }

    private suspend fun browseBooklist(page: Int, tag: String, area: String, end: String): MangasPage {
        val url = "$baseUrl/booklist/".toHttpUrl().newBuilder()
            .addQueryParameter("tag", tag)
            .addQueryParameter("area", area)
            .addQueryParameter("end", end)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseMangaListCards(document), document.hasNextPage())
    }

    // ---- 详情与章节（同一页，只请求一次） ----

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(document.parseDetails(manga), document.parseChapters())
    }

    private fun Document.parseDetails(manga: SManga): SManga = manga.apply {
        title = selectFirst("p.detail-main-info-title")?.text() ?: title
        thumbnail_url = selectFirst(".detail-main-cover img")?.attr("data-original")
            ?.ifEmpty { null }
        description = selectFirst("p.detail-desc")?.text()
        genre = select("p.detail-main-info-class a").joinToString { it.text() }
        author = select("p.detail-main-info-author")
            .firstOrNull { it.ownText().startsWith("作者") }
            ?.selectFirst("a")
            ?.text()
        status = when (selectFirst("span.detail-list-title-1")?.text().orEmpty()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.parseChapters(): List<SChapter> {
        // 站点章节列表为旧→新；反转为新→旧（app 约定降序）
        val updatedDate = selectFirst("span.detail-list-title-3")?.text()
            ?.removeSuffix("更新")
            ?.let { DATE_FORMAT.tryParseDate(it) }
            ?: 0L

        return select("ul#detail-list-select a.chapteritem")
            .map { element ->
                SChapter.create().apply {
                    url = element.absUrl("href").toHttpUrl().encodedPath
                    name = element.text()
                    chapter_number = CHAPTER_NUMBER.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                }
            }
            .asReversed()
            // 站点无 per-chapter 日期，整书更新日只赋给最新一章
            .also { it[0].date_upload = updatedDate }
    }

    // ---- URL 搜索（在搜索框粘贴站点链接） ----

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "book") return null
        return client.get(url).asJsoup().parseDetails(SManga.create()).apply {
            this.url = url.encodedPath
        }
    }

    // ---- 正文 ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#cp_img img[data-original]")
            .mapIndexed { index, img ->
                Page(index = index, imageUrl = img.attr("data-original"))
            }
    }

    // ---- 筛选器 ----

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    private companion object {
        val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
        val CHAPTER_NUMBER = Regex("""第(\d+)""")
    }
}

/** 首页/更新/书单共用卡片：li > .manga-list-2-cover / .manga-list-2-title */
private fun parseMangaListCards(document: Document): List<SManga> = document
    .select("ul.manga-list-2 > li")
    .mapNotNull { item ->
        val link = item.selectFirst(".manga-list-2-title a[href]")
            ?: item.selectFirst("a[href]") ?: return@mapNotNull null
        val title = link.text().ifEmpty { item.selectFirst(".manga-list-2-title")?.text().orEmpty() }
        if (title.isEmpty()) return@mapNotNull null
        SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().encodedPath
            this.title = title
            thumbnail_url = item.selectFirst("img[data-original]")?.attr("data-original")
                ?.ifEmpty { null }
        }
    }

/** 搜索结果：ul.book-list > li（结构与书单卡片不同） */
private fun parseSearchCards(document: Document): List<SManga> = document
    .select("ul.book-list > li")
    .mapNotNull { item ->
        val link = item.selectFirst("a[href]") ?: return@mapNotNull null
        val title = item.selectFirst("p.book-list-info-title")?.text().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().encodedPath
            this.title = title
            thumbnail_url = item.selectFirst("img.book-list-cover-img")?.attr("data-original")
                ?.ifEmpty { null }
        }
    }

/**
 * 分页收敛：末页下一页按钮为 `paginate-disabled-btn` 且无 title；
 * 更新页末页条数仍可能是满页 12，不能按条数判断。
 */
private fun Document.hasNextPage(): Boolean = selectFirst("div.pagination a.paginate-btn[title=下一页]") != null
