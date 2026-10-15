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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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

    // ---- 通用解析 ----

    /** 首页/分类/搜索结果共用的三列卡片 */
    private fun Element.toSManga(): SManga = SManga.create().apply {
        val link = selectFirst("a.ImgA") ?: selectFirst("a[href^=/comic/]")!!
        url = link.attr("href")
        title = link.attr("title")
            .ifEmpty { selectFirst("a.txtA")?.text().orEmpty() }
            .ifEmpty { text() }
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
    private fun Document.hasNextPage(): Boolean = selectFirst("div.pagination-wrap li:last-child a")?.text()?.startsWith("...") == true

    // ---- 热门（排行页，单页无分页） ----

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(parseItemBoxes(client.get("$baseUrl/ranking").asJsoup()), false)

    // ---- 最新（更新页，无分页） ----

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(parseItemBoxes(client.get("$baseUrl/update").asJsoup()), false)

    // ---- 搜索与筛选 ----

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queryTrimmed = query.trim()
        val url = if (queryTrimmed.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(queryTrimmed)
                .apply { if (page > 1) addPathSegments("page/$page") }
                .build()
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
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("comics/$genre/ob/$order/st/$status")
                .apply { if (page > 1) addPathSegments("page/$page") }
                .build()
        }

        val document = client.get(url).asJsoup()
        return MangasPage(parseCol3Cards(document), document.hasNextPage())
    }

    // ---- 详情与章节（同一详情页，只请求一次） ----

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetails(document), chapterList(document))
    }

    private fun mangaDetails(document: Document): SManga = SManga.create().apply {
        url = document.location().toHttpUrl().encodedPath
        title = document.selectFirst("h1")!!.text()
            .removePrefix("《").removeSuffix("》")
        thumbnail_url = document.selectFirst("div.pic img")?.attr("src")
        author = document.select("div.sub_r > p.txtItme")
            .firstOrNull { it.selectFirst("a[href^=/comics/]") == null && it.selectFirst("span.date") == null }
            ?.ownText()?.takeIf { it.isNotEmpty() }
        genre = document.select("p.txtItme a[href^=/comics/]").joinToString { it.text() }
        val statusText = document.selectFirst("span.date")?.text().orEmpty()
        status = when {
            statusText.contains("连载中") -> SManga.ONGOING
            statusText.contains("已完结") -> SManga.COMPLETED
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
        return mangaDetails(document)
    }

    // ---- 正文 ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
}
