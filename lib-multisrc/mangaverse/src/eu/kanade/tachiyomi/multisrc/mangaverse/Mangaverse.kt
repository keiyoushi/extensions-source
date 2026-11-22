package eu.kanade.tachiyomi.multisrc.mangaverse

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class Mangaverse : KeiSource() {

    // The theme's latest chapters feed mixes every language, so there is no Latest
    override val supportsLatest = false

    // Path segment of the source's language
    protected open val siteLang get() = lang.lowercase()

    private val langUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegment(siteLang).addPathSegment("").build()

    override fun getHomeUrl() = langUrl.toString()

    // Nonce of the theme's "load more", from the last home page loaded
    private var gridNonce: String? = null

    // The home page's series grid, then the theme's "load more", one page each
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) {
            val document = client.get(langUrl, Fetch.DOCUMENT).asJsoup()
            gridNonce = document.nonce()
            val series = document.select(SERIES_SELECTOR).map { it.toSManga() }
            return MangasPage(series, document.selectFirst("#load-more-series-grid") != null)
        }
        val more = loadMore(gridNonce!!, langUrl, "type" to "series_grid", "page" to "$page")
        val series = Jsoup.parseBodyFragment(more.html, baseUrl).select(SERIES_SELECTOR).map { it.toSManga() }
        return MangasPage(series, more.hasMore)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val url = langUrl.newBuilder()
            .addQueryParameter("s", query.trim())
            .addQueryParameter("lang", siteLang)
            .build()
        val mangas = client.get(url, Fetch.DOCUMENT).asJsoup()
            .select(".search-series-results $SERIES_SELECTOR")
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst(".series-card-title")!!.text()
        thumbnail_url = selectFirst(".series-card-thumb")?.attr("style")
            ?.let { COVER_REGEX.find(it)?.groupValues?.get(1) }
            ?.toOriginalSize()
    }

    // Series and chapter links: /<lang>/manga/<series>.html and /<lang>/<series>/<chapter>.html
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || segments.size != 3 || segments[0] != siteLang) return null
        val series = if (segments[1] == "manga") segments[2] else "${segments[1]}.html"
        val manga = SManga.create().apply { this.url = "/$siteLang/manga/$series" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesUrl = getMangaUrl(manga).toHttpUrl()
        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.series-title")!!.text()
            description = MarkdownDescription.Builder().description(document.selectFirst(".series-description")).build()
            thumbnail_url = document.selectFirst(".series-header-thumbnail img")?.absUrl("src")?.toOriginalSize()
        }
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val chapterList = document.select(CHAPTER_SELECTOR).map { it.toSChapter() }.toMutableList()
        // The page shows the newest chapters, and the theme's "load more" the others
        document.selectFirst("#load-more-series")?.let { button ->
            val nonce = document.nonce()
            var next = 2
            do {
                val more = loadMore(nonce, seriesUrl, "type" to "series", "page" to "${next++}", "category_id" to button.attr("data-category"), "order" to "desc")
                val page = Jsoup.parseBodyFragment(more.html, baseUrl).select(CHAPTER_SELECTOR).map { it.toSChapter() }
                chapterList += page
            } while (more.hasMore && page.isNotEmpty())
        }
        return SMangaUpdate(details, chapterList)
    }

    private fun Element.toSChapter() = SChapter.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        name = selectFirst(".chapter-title")!!.text()
        // Names start with the series title, which can hold numbers of its own
        chapter_number = NUMBER_REGEX.find(name.substringAfterLast(", "))?.value?.toFloat() ?: -1f
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".entry-content figure > img")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("data-src")) }

    private fun Document.nonce() = selectFirst("script#mangaverse-load-more-js-extra")!!.data()
        .substringAfter("=")
        .substringBeforeLast(";")
        .parseAs<AjaxConfigDto>()
        .nonce

    private suspend fun loadMore(nonce: String, from: HttpUrl, vararg params: Pair<String, String>): LoadMoreDataDto {
        val body = FormBody.Builder()
            .add("action", "mangaverse_load_more")
            .add("nonce", nonce)
            .add("lang", siteLang)
            .apply { params.forEach { (name, value) -> add(name, value) } }
            .build()
        return client.post(baseUrl.toHttpUrl().resolve("/wp-admin/admin-ajax.php")!!, Fetch.XHR.from(from), body).parseAs<LoadMoreDto>().data
    }

    // Covers in their original size, without WordPress's resized suffix (-464x722)
    private fun String.toOriginalSize() = replace(SIZE_REGEX, "")

    companion object {
        private const val SERIES_SELECTOR = ".series-card a.series-card-link"
        private const val CHAPTER_SELECTOR = "article.chapter-item a.chapter-link"
        private val COVER_REGEX = Regex("""url\('([^']+)'\)""")
        private val NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
        private val SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
