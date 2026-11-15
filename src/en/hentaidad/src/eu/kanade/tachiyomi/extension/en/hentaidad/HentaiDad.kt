package eu.kanade.tachiyomi.extension.en.hentaidad

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class HentaiDad : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getListing(listUrl(page, "trending"))

    override suspend fun getLatestUpdates(page: Int) = getListing(listUrl(page, "images"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tag = filters.firstInstanceOrNull<TagFilter>()?.selected
        val url = when {
            query.isNotBlank() -> listUrl(page, "search").newBuilder().addQueryParameter("q", query.trim()).build()
            tag != null -> listUrl(page, "hentai", tag)
            else -> listUrl(page, "images")
        }
        return getListing(url)
    }

    private fun listUrl(page: Int, vararg segments: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        if (page > 1) addPathSegment(page.toString())
    }.build()

    // Every listing but /images mixes videos in
    private suspend fun getListing(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".archive-grid $GALLERY_CARD").map(::galleryCard)
        return MangasPage(mangas, document.selectFirst("a.pagination-direction[rel=next]") != null)
    }

    private fun galleryCard(link: Element) = SManga.create().apply {
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.selectFirst(".archive-card-title")!!.text()
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size != 1) return null
        val document = client.get(url, Fetch.DOCUMENT, ensureSuccess = false).asJsoup()
        if (document.selectFirst("main.post-main") == null) return null
        return parseDetails(document).apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Video posts have no images to read
        val chapterList = if (document.selectFirst(PAGE_SELECTOR) == null) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    date_upload = Instant.tryParse(document.selectFirst(".post-meta time")?.attr("datetime"))
                },
            )
        }
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        val header = document.selectFirst("header.post-header")!!
        title = header.selectFirst("h1")!!.text()
        // Listing thumbnails are landscape crops: the first image makes a better cover
        thumbnail_url = document.selectFirst("$PAGE_SELECTOR img")?.absUrl("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        genre = document.select(".post-tags a").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .info(header.selectFirst(".post-exclusive")?.text(), header.selectFirst(".post-meta span:matchesOwn(images?$)")?.text())
            .description(document.selectFirst(".post-description > div"))
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR)
        .mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/categories", Fetch.DOCUMENT).asJsoup()
        .select("#tags-container a.page-tag")
        .map { Tag(it.selectFirst("h2")!!.text(), it.absUrl("href").toHttpUrl().pathSegments.last()) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Tag>>() ?: return FilterList()
        return FilterList(
            Filter.Header("Text search ignores the tag"),
            TagFilter(tags),
        )
    }

    companion object {
        private const val GALLERY_CARD = "article.archive-card:has(.archive-card-type:matchesOwn(^Gallery$)) a.archive-card-link"
        private const val PAGE_SELECTOR = "#premium-gallery a.post-gallery-item"
    }
}
