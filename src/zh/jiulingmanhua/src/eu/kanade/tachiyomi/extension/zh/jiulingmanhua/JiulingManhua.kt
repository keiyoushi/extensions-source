package eu.kanade.tachiyomi.extension.zh.jiulingmanhua

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class JiulingManhua : SinMH() {

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != mobileUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.takeIf { it.firstOrNull() == "manhua" }?.getOrNull(1)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = "/manhua/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
            .apply { this.url = manga.url }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".view-sub h1.title")!!.text()
        thumbnail_url = document.selectFirst("#Cover mip-img")?.absUrl("src")
        author = document.info("作者")?.text()
        genre = listOfNotNull(document.info("类别")?.text(), document.info("地区")?.text())
            .plus(document.info("主题")?.select("a")?.map { it.text().removePrefix("#") }.orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString()
            .ifEmpty { null }
        status = when (document.info("状态")?.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val altTitles = document.info("别名")?.text()?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != title }
            ?.distinct()
            .orEmpty()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("p.txtDesc")?.text()?.removePrefix("简介："))
            .altTitles(altTitles, "别名")
            .build()
    }

    override fun chapterListSelector() = "ul.Drama li > a"

    override val dateSelector = "dl.pic_zi:contains(更新于) dd"

    // The phone reader shows one image per page (<id>-<n>.html), and the desktop one fails with a server error on recent chapters
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val count = document.selectFirst("#k_total")!!.text().toInt()
        val id = chapterUrl.pathSegments.last().removeSuffix(".html")
        return (1..count).map { number ->
            if (number == 1) {
                Page(0, url = chapterUrl.toString(), imageUrl = document.pageImageUrl())
            } else {
                val pageUrl = chapterUrl.newBuilder().setPathSegment(chapterUrl.pathSize - 1, "$id-$number.html").build()
                Page(number - 1, url = pageUrl.toString())
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url, Fetch.DOCUMENT).asJsoup().pageImageUrl()

    private fun Document.info(label: String): Element? = selectFirst("dl.pic_zi:has(dt:contains($label)) dd")

    private fun Document.pageImageUrl() = selectFirst("#chapter-image img")!!.absUrl("src")
}
