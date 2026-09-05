package eu.kanade.tachiyomi.extension.zh.nnhanman

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 鸟鸟韩漫 (nnhanman.xyz) — qTcms 移动模板
 *
 * 解析要点（详见调研报告）：
 * - 列表卡片：ul.col_3_1 > li（首页/分类/搜索共用）；更新/排行页为 div.itemBox
 * - 搜索：/search/{关键词}/page/{N}
 * - 分类：/comics/{分类}/ob/{time|hits}/st/{all|completed|serialized}/page/{N}
 * - 详情：/comic/{slug}.html，章节列表 ul#mh-chapter-list-ol-0（页面为新→旧，需反转）
 * - 正文：#m_r_imgbox_0 内 img[data-src]，按 data-index 排序；备用图床池 *.nnpic.xyz
 *
 * name / lang / baseUrl 由 build.gradle.kts 的 keiyoushi 块注入。
 */
@Source
abstract class NNHanman : KeiSource() {

    private val encodeURIComponent: (String) -> String = {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    // ---- 通用解析 ----

    /** 首页/分类/搜索结果共用的三列卡片 */
    private fun Element.toSManga(): SManga = SManga.create().apply {
        val link = selectFirst("a.ImgA") ?: selectFirst("a[href^=/comic/]")!!
        url = link.attr("href")
        title = link.attr("title").ifEmpty { selectFirst("a.txtA")?.text()?.ifEmpty { null } ?: text() }
        thumbnail_url = selectFirst("source[srcset]")?.attr("srcset")
            ?: selectFirst("img[src]")?.attr("src")
    }

    /** 更新/排行页的 itemBox 卡片 */
    private fun parseItemBoxes(document: Document): List<SManga> = document.select("div.itemBox").map { box ->
        SManga.create().apply {
            val link = box.selectFirst("a.title")!!
            url = link.attr("href")
            title = link.attr("title").ifEmpty { link.text() }
            thumbnail_url = box.selectFirst("img[src]")?.attr("src")
        }
    }

    private fun parseCol3Cards(document: Document): List<SManga> = document.select("ul.col_3_1 > li")
        .filter { it.selectFirst("a.ImgA, a[href^=/comic/]") != null }
        .map { it.toSManga() }
        .distinctBy { it.url }

    /** 分页条里 "... 147" 的末页数字项，用于判断 hasNextPage */
    private fun Document.hasNextPage(): Boolean = selectFirst("div.pagination-wrap li:last-child a")?.text()?.trim()?.startsWith("...") == true

    // ---- 热门（排行页，单页无分页） ----

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(parseItemBoxes(client.get("$baseUrl/ranking").asJsoup()), false)

    // ---- 最新（更新页，无分页） ----

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(parseItemBoxes(client.get("$baseUrl/update").asJsoup()), false)

    // ---- 搜索与筛选 ----

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val path = if (query.isNotBlank()) {
            val kw = encodeURIComponent(query.trim())
            if (page > 1) "search/$kw/page/$page" else "search/$kw"
        } else {
            // 纯筛选浏览：/comics/{genre}/ob/{order}/st/{status}/page/{N}
            var genre = "all"
            var order = "time"
            var status = "all"
            filters.forEach { f ->
                when (f) {
                    is GenreFilter -> genre = f.toUriPart()
                    is OrderFilter -> order = f.toUriPart()
                    is StatusFilter -> status = f.toUriPart()
                    else -> {}
                }
            }
            if (page > 1) "comics/$genre/ob/$order/st/$status/page/$page" else "comics/$genre/ob/$order/st/$status"
        }

        val document = client.get("$baseUrl/$path").asJsoup()
        return MangasPage(parseCol3Cards(document), document.hasNextPage())
    }

    // ---- 详情与章节（同一详情页，只请求一次） ----

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(mangaDetails(document, manga.url), chapterList(document))
    }

    private fun mangaDetails(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst("h1")!!.text()
            .removePrefix("《").removeSuffix("》")
        thumbnail_url = document.selectFirst("div.pic img")?.attr("src")
        author = document.select("div.sub_r > p.txtItme")
            .firstOrNull { it.selectFirst("a[href^=/comics/]") == null && it.selectFirst("span.date") == null }
            ?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
        genre = document.select("p.txtItme a[href^=/comics/]").joinToString { it.text() }
        status = when {
            document.selectFirst("span.date")?.text()?.contains("连载中") == true -> SManga.ONGOING
            document.selectFirst("span.date")?.text()?.contains("已完结") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("p.txtDesc")?.text()
            ?.removePrefix("介绍:")?.trim()
    }

    private fun chapterList(document: Document): List<SChapter> {
        // 页面为新→旧排列，先反转为旧→新再编号
        return document.select("#mh-chapter-list-ol-0 li a")
            .asReversed()
            .mapIndexed { index, a ->
                SChapter.create().apply {
                    url = a.attr("href")
                    name = a.selectFirst("span")?.text() ?: a.text()
                    chapter_number = (index + 1).toFloat()
                }
            }
    }

    // ---- URL 搜索（在搜索框粘贴站点链接） ----

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "comic") return null
        val document = client.get(url).asJsoup()
        return mangaDetails(document, url.encodedPath)
    }

    // ---- 正文 ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url).asJsoup()
        return document.select("#m_r_imgbox_0 img[data-src]")
            .sortedBy { it.attr("data-index").toIntOrNull() ?: 0 }
            .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-src")) }
    }

    // ---- 筛选器 ----

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("分类浏览（搜索时无效）"),
        GenreFilter(),
        OrderFilter(),
        StatusFilter(),
    )

    private class GenreFilter : Filter.Select<String>("分类", GENRES.map { it.first }.toTypedArray()) {
        fun toUriPart() = GENRES[state].second
    }

    private class OrderFilter : Filter.Select<String>("排序", arrayOf("按时间", "按热度")) {
        fun toUriPart() = arrayOf("time", "hits")[state]
    }

    private class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "已完结", "连载中")) {
        fun toUriPart() = arrayOf("all", "completed", "serialized")[state]
    }

    private companion object {
        val GENRES = arrayOf(
            "全部" to "all",
            "正妹" to "正妹",
            "恋爱" to "恋爱",
            "出版漫画" to "出版漫画",
            "肉慾" to "肉慾",
            "浪漫" to "浪漫",
            "大尺度" to "大尺度",
            "巨乳" to "巨乳",
            "有夫之婦" to "有夫之婦",
            "女大生" to "女大生",
            "狗血劇" to "狗血劇",
            "同居" to "同居",
            "好友" to "好友",
            "調教" to "調教",
            "动作" to "动作",
            "後宮" to "後宮",
            "不倫" to "不倫",
            "3D" to "3D",
            "校園" to "校園",
            "耽美" to "耽美",
            "日漫" to "日漫",
        )
    }
}
