package eu.kanade.tachiyomi.multisrc.masonry

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class Masonry : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = getGridPage(page, "updates", "trending")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getGridPage(page, "updates", "newest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/post/".toHttpUrl().newBuilder()
                .addPathSegment(query.trim())
                .addEncodedPathSegments("mpage/$page/")
                .build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            val mangas = document.select(".list-gallery:not(.static) figure:not(:has(.icon-play))").mapNotNull(::mangaFromElement)
            return MangasPage(mangas, document.selectFirst(".pagination-a li.next") != null)
        }

        val tags = filters.firstInstanceOrNull<TagFilter>()?.selected.orEmpty()
        val content = GridFilter(
            content = "pix",
            quality = filters.firstInstance<QualityFilter>().selected,
            label = tags.joinToString("+").ifEmpty { "0" },
            ord = filters.firstInstance<OrderFilter>().selected,
        )
        return getGridPage(page, content.toJsonString(), "filter")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = url.pathSegments.singleOrNull { it.isNotEmpty() } ?: return null
        val document = client.get("$baseUrl/$slug/", Fetch.DOCUMENT).asJsoup()
        val firstImage = document.selectFirst(".list-gallery a[href^=https://cdn.]") ?: return null
        return parseMangaDetails(document, SManga.create()).apply {
            this.url = "/$slug/"
            title = document.selectFirst("header h1")!!.text()
            thumbnail_url = firstImage.selectFirst("img")?.imgAttr()
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
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document, manga),
        )
    }

    protected open fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        document.selectFirst("p.link-btn")?.run {
            artist = select("a[href*=/model/]").eachText().joinToString()
            genre = select("a[href*=/tag/]").eachText().joinToString()
            author = selectFirst("a")?.text()
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#content > p"))
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    protected open fun parseChapterList(document: Document, manga: SManga): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Gallery"
            url = manga.url
        },
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".list-gallery a[href^=https://cdn.]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("href"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/sort/newest/", Fetch.DOCUMENT).asJsoup()
        .select("#filter-a section[data-slot=tags] label")
        .map { Tag(it.selectFirst(".txt")!!.text(), it.selectFirst("input")!!.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Tag>>()

        val filters = mutableListOf(
            Filter.Header("Filters ignored with text search"),
            Filter.Separator(),
            OrderFilter(),
            QualityFilter(),
        )

        if (!tags.isNullOrEmpty()) {
            filters += TagFilter(tags)
        }

        return FilterList(filters)
    }

    private suspend fun getGridPage(page: Int, content: String, sort: String): MangasPage {
        val url = "$baseUrl/gridapi/".toHttpUrl().newBuilder()
            .addQueryParameter("content", content)
            .addQueryParameter("sort", sort)
            .addQueryParameter("mpage", page.toString())
            .build()
        val document = client.get(url, Fetch.XHR).asJsoup()
        val figures = document.select("figure")
        val mangas = figures.filter { it.selectFirst(".icon-play") == null }.mapNotNull(::mangaFromElement)
        return MangasPage(mangas, figures.size >= GRID_PAGE_SIZE)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a")!!
        // Some galleries have no title; the card then shows only the models
        val name = link.attr("title").ifEmpty { element.select("a[href*=/model/]").eachText().joinToString() }
            .ifEmpty { return null }
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = name
            thumbnail_url = element.selectFirst("img")?.imgAttr()
        }
    }

    protected fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        else -> attr("abs:src")
    }

    companion object {
        private const val GRID_PAGE_SIZE = 20
    }
}
