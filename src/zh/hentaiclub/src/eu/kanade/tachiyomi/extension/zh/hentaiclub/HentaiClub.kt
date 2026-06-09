package eu.kanade.tachiyomi.extension.zh.hentaiclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class HentaiClub : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val name = "绅士会所"
    override val baseUrl = "https://www.hentaiclub.net"
    override val lang = "zh"
    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val searchUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(query.trim())
                .build()
                .toString()
            val url = if (page > 1) "$searchUrl/page/$page/" else "$searchUrl/"
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()

        if (tagFilter != null && tagFilter.state.isNotBlank()) {
            val tag = tagFilter.state.trim()
            val base = "$baseUrl/tag/$tag/"
            val url = if (page > 1) "$base$page/" else base
            return GET(url, headers)
        }

        if (sortFilter != null && sortFilter.state > 0) {
            val sortValue = sortFilter.getValue()
            return GET("$baseUrl/sort/$sortValue.html", headers)
        }

        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val contentEl = document.selectFirst(".content")

        return SManga.create().apply {
            title = document.title().substringBefore(" - 绅士会所")

            val firstImage = contentEl?.selectFirst("div.post-item[data-src]")
            thumbnail_url = firstImage?.absUrl("data-src")

            val tagLinks = contentEl?.select("a[href*=/tag/]") ?: emptyList()
            author = tagLinks.firstOrNull()?.text()
            genre = tagLinks.joinToString { it.text() }

            val viewsMatch = VIEWS_REGEX.find(contentEl?.text() ?: "")
            description = viewsMatch?.let { "浏览量：${it.groupValues[1]}次" }

            status = when {
                response.request.url.encodedPath.contains("/r18/") -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                name = "章节 1"
                setUrlWithoutDomain(manga.url)
            },
        ),
    )

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.post-item[data-src]").mapIndexed { idx, el ->
            Page(idx, imageUrl = el.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        TagFilter(),
    )

    // ============================= Utilities =============================

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.item-link")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".item-link-text")!!.text()

                val img = element.selectFirst("img.item-img")
                thumbnail_url = img?.absUrl("data-original")?.ifEmpty { img.absUrl("src") }
            }
        }

        val hasNextPage = mangas.size >= 24

        return MangasPage(mangas, hasNextPage)
    }

    companion object {
        private val VIEWS_REGEX = Regex("浏览[：:]\\s*(\\d+)次")
    }
}
