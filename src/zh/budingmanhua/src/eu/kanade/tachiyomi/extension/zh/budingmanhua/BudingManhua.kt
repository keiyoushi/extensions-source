package eu.kanade.tachiyomi.extension.zh.budingmanhua

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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class BudingManhua : KeiSource() {

    override val supportsLatest = false

    // The site's ranking only holds old series whose image hosts are gone, so its list by last update is used
    override suspend fun getPopularManga(page: Int): MangasPage = getBookList(page, null, null, null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            // The search has a single page of 20 results at most
            val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("keyword", query.trim()).build()
            val mangas = client.get(url, Fetch.DOCUMENT).asJsoup().select("div.book-list-cover a").map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = link.attr("title")
                    thumbnail_url = link.selectFirst("img")?.absUrl("data-original")
                }
            }
            return MangasPage(mangas, false)
        }
        return getBookList(
            page,
            filters.firstInstanceOrNull<CategoryFilter>()?.selected,
            filters.firstInstanceOrNull<AreaFilter>()?.selected,
            filters.firstInstanceOrNull<StatusFilter>()?.selected,
        )
    }

    private suspend fun getBookList(page: Int, category: String?, area: String?, status: String?): MangasPage {
        val url = "$baseUrl/booklist".toHttpUrl().newBuilder().apply {
            category?.let { addQueryParameter("cate", it) }
            area?.let { addQueryParameter("area", it) }
            status?.let { addQueryParameter("end", it) }
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.manga-list-2 li").map { item ->
            val link = item.selectFirst("p.manga-list-2-title a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst("img.manga-list-2-cover-img")?.absUrl("src")
            }
        }
        // The arrow to the next page has no link on the last page
        return MangasPage(mangas, document.selectFirst("ul.pagination li:last-child a[href*='page=']") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // The mirrors serve the same pages
        if (url.host !in HOSTS) return null
        val path = when (url.pathSegments.firstOrNull()) {
            "manhua" -> url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { "/manhua/$it" }
            // A chapter page links back to its series
            "chapter" -> client.get(baseUrl.toHttpUrl().resolve(url.encodedPath)!!, Fetch.DOCUMENT).asJsoup()
                .selectFirst("a.view-fix-bottom-bar-item[href^=/manhua/]")?.absUrl("href")?.toHttpUrl()?.encodedPath
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = path }
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

    // The page hides the author ("...") and its status doesn't match the site's own filter, so neither is read
    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("p.detail-main-info-title")!!.text()
        thumbnail_url = selectFirst("div.detail-main-cover img")?.absUrl("data-original")
        genre = (listOfNotNull(selectFirst("p.detail-main-info-author:contains(地区) a")?.text()) + select("p.detail-main-info-class a").map { it.text() })
            .filter { it.isNotEmpty() }
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst("p.detail-desc")?.text())
            .build()
    }

    // The site only dates the series' last update, so only the newest chapter gets it
    private fun Document.toSChapters(): List<SChapter> {
        val updated = DATE_FORMAT.tryParseDate(selectFirst("span.detail-list-title-3")?.text()?.removeSuffix("更新"), ZONE)
        return select("#detail-list-select a.chapteritem").reversed().mapIndexed { index, link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                if (index == 0) date_upload = updated
            }
        }
    }

    // A chapter is split into pages of 30 images
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = mutableListOf<String>()
        var url: String? = getChapterUrl(chapter)
        while (url != null) {
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            document.select("div.readForm img").mapTo(images) { it.absUrl("data-original") }
            url = document.selectFirst("a.view-bottom-bar-item[title=下一页][href]")?.absUrl("href")
        }
        return images.mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val HOSTS = setOf("hxy4.com", "qimeitj.com", "hxy10.com")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ZONE = ZoneId.of("Asia/Shanghai")
    }
}
