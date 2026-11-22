package eu.kanade.tachiyomi.extension.zh.woaimanhua

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Woaimanhua : KeiSource() {

    // The ranking is a single page; its second list ranks by popularity
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/rank", Fetch.DOCUMENT).asJsoup().select("#rankList_2 > a").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBookList(page, "全部", "-1", LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            // The search has a single page of results
            val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("keyword", query.trim()).build()
            val mangas = client.get(url, Fetch.DOCUMENT).asJsoup().select("ul.book-list div.book-list-cover a").map { it.toSManga() }
            return MangasPage(mangas, false)
        }
        return getBookList(
            page,
            filters.firstInstanceOrNull<TagFilter>()?.selected ?: "全部",
            filters.firstInstanceOrNull<AreaFilter>()?.selected ?: "-1",
            filters.firstInstanceOrNull<StatusFilter>()?.selected ?: "-1",
        )
    }

    private suspend fun getBookList(page: Int, tag: String, area: String, status: String): MangasPage {
        val url = "$baseUrl/booklist".toHttpUrl().newBuilder()
            .addQueryParameter("tag", tag)
            .addQueryParameter("area", area)
            .addQueryParameter("end", if (status == LATEST) "-1" else status)
            .apply { if (status == LATEST) addQueryParameter("last", "1") }
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li:has(div.manga-list-2-cover)").map { item ->
            SManga.create().apply {
                setUrlWithoutDomain(item.selectFirst("p.manga-list-2-title a")!!.absUrl("href"))
                title = item.selectFirst("p.manga-list-2-title")!!.text()
                thumbnail_url = item.selectFirst("img")?.absUrl("data-original")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.paginate-btn[title=下一页]") != null)
    }

    // A link of the ranking or of the search results
    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = attr("title")
        thumbnail_url = selectFirst("img")?.absUrl("data-original")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val bookPath = when (url.pathSegments.firstOrNull()) {
            "book" -> url.encodedPath
            // A chapter page links back to its book
            "chapter" -> client.get(baseUrl.toHttpUrl().resolve(url.encodedPath)!!, Fetch.DOCUMENT).asJsoup()
                .selectFirst("a.view-fix-bottom-bar-item[href^=/book/]")?.absUrl("href")?.toHttpUrl()?.encodedPath
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = bookPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("p.detail-main-info-title")!!.text()
        thumbnail_url = selectFirst("div.detail-main-cover img")?.absUrl("src")
        author = info("作者")
        genre = (listOfNotNull(info("地区")) + select("p.detail-main-info-class a").map { it.text() }).joinToString().ifEmpty { null }
        status = when (selectFirst("span.detail-list-title-1")?.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(selectFirst("p.detail-desc")?.text())
            .altTitles(listOfNotNull(info("别名")))
            .build()
    }

    private fun Document.info(label: String) = selectFirst("p.detail-main-info-author:contains($label) a")?.text()
        ?.takeUnless { it.isEmpty() || it == "未知" }

    // The site only dates the latest update, so only the newest chapter gets it
    private fun Document.toSChapters(): List<SChapter> {
        val updated = DATE_FORMAT.tryParseDate(selectFirst("span.detail-list-title-3")?.text()?.removeSuffix("更新"), ZONE)
        return select("#detail-list-select li:has(a.chapteritem)").reversed().mapIndexed { index, item ->
            val link = item.selectFirst("a.chapteritem")!!
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                // Paid chapters carry a lock icon and send guests to the login page
                name = if (item.selectFirst("img.detail-list-3-info-right") != null) "🔒 ${link.text()}" else link.text()
                if (index == 0) date_upload = updated
            }
        }
    }

    // Browsers load the images the page gives over http through https
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) return emptyList()
        return client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .select("div.readForm img")
            .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src").toHttpUrl().newBuilder().scheme("https").build().toString()) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ZONE = ZoneId.of("Asia/Shanghai")
    }
}
