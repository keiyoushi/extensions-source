package eu.kanade.tachiyomi.extension.vi.nettruyenzz

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class NetTruyenZZ : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".rank-list a.rank-item").map { item ->
            SManga.create().apply {
                setUrlWithoutDomain(item.absUrl("href"))
                title = item.selectFirst(".rank-name")!!.text()
                thumbnail_url = item.selectFirst(".rank-thumb")?.backgroundImage()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "", ALL_GENRES, "")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(
        page,
        query.trim(),
        filters.firstInstance<GenreFilter>().value,
        filters.firstInstance<SortFilter>().value,
    )

    // Only the full list searches: genre pages ignore the query
    private suspend fun getMangaList(page: Int, query: String, genre: String, sort: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(if (query.isEmpty()) genre else ALL_GENRES)
            .apply { if (query.isNotEmpty()) addQueryParameter("q", query) }
            .apply { if (sort.isNotEmpty()) addQueryParameter("sort", sort) }
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.manga-card a.manga-link").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".manga-title")!!.text()
                thumbnail_url = link.selectFirst(".cover-bg")?.backgroundImage()
            }
        }
        // The pager's links drop the query: only the next link's presence counts
        return MangasPage(mangas, document.selectFirst("a.page-link[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = url.pathSegments.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val document = client.get(baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).build(), Fetch.DOCUMENT).asJsoup()
        // Genre and list pages sit at the same level as series
        if (document.selectFirst("h1.manga-main-title") == null) return null
        return SManga.create().apply {
            this.url = "/$slug"
            parseDetails(document)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        // The page holds the first 50 chapters, the site's API gives the others
        val chapterList = document.select("#chapter-list a.chapter-row").map { it.toSChapter() }.toMutableList()
        val slug = manga.url.removePrefix("/")
        var page = document.selectFirst("#load-more-btn")?.attr("data-page")?.toInt()
        while (page != null) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/series/$slug/chapters")
                .addQueryParameter("p", page.toString())
                .build()
            val result = client.get(url, Fetch.XHR.from(getMangaUrl(manga))).parseAs<ChapterListDto>()
            result.chapters.mapTo(chapterList) { it.toSChapter(slug) }
            page = if (result.hasMore) page + 1 else null
        }
        return SMangaUpdate(manga, chapterList)
    }

    private fun SManga.parseDetails(document: Document) {
        title = document.selectFirst("h1.manga-main-title")!!.text()
        thumbnail_url = document.selectFirst("img.cover-img")?.absUrl("src")
        genre = document.select(".genre-tags a.genre-tag").joinToString { it.text() }.ifEmpty { null }
        status = when (document.statValue("Tình trạng")?.lowercase()) {
            "đang tiến hành" -> SManga.ONGOING
            "đã hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // Series without a summary get the template of the site the data comes from
        val summary = document.selectFirst(".panel-text")?.takeUnless { SEO_SUMMARY in it.text() }
        description = MarkdownDescription.Builder()
            .description(summary)
            .build()
    }

    private fun Document.statValue(label: String) = select(".stats-card .stat-row")
        .firstOrNull { it.selectFirst(".stat-k")?.text() == label }
        ?.selectFirst(".stat-v")
        ?.textOrNull()

    private fun Element.toSChapter() = SChapter.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        name = selectFirst(".chapter-title")!!.text()
        date_upload = parseRelativeDate(selectFirst(".chapter-time")?.text())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".pages figure.page img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") })
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Thể loại không áp dụng khi tìm kiếm"),
        GenreFilter(),
        SortFilter(),
    )

    private fun Element.backgroundImage(): String? = BACKGROUND_IMAGE_REGEX.find(attr("style"))?.groupValues?.get(1)
        ?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }

    companion object {
        private const val ALL_GENRES = "truyen-tranh"
        private const val SEO_SUMMARY = "được cập nhật nhanh và đầy đủ nhất tại"
        private val BACKGROUND_IMAGE_REGEX = Regex("""url\('([^']+)'\)""")
    }
}
