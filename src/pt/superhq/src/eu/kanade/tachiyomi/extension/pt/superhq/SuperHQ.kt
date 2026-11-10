package eu.kanade.tachiyomi.extension.pt.superhq

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class SuperHQ : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getListing(listingUrl(page, "", null))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getListing(listingUrl(page, query.trim(), filters.firstInstanceOrNull<CategoryFilter>()?.selected))

    private fun listingUrl(page: Int, query: String, category: String?): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        if (query.isEmpty() && category != null) addPathSegment("category").addPathSegment(category)
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        if (query.isNotEmpty()) {
            addQueryParameter("s", query)
            category?.let { addQueryParameter("category_name", it) }
        }
    }.build()

    private suspend fun getListing(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val host = baseUrl.toHttpUrl().host
        val mangas = document.select("section.post-list-grade article.item-post-grade").mapNotNull { article ->
            val link = article.selectFirst(".entry-title a")!!
            // Ads in the grid lead to partner sites
            if (link.absUrl("href").toHttpUrlOrNull()?.host != host) return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = article.selectFirst(".entry-featured-media img")?.coverUrl()
            }
        }
        return MangasPage(mangas, document.selectFirst(".wp-pagenavi a.larger") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || !url.encodedPath.endsWith(".html")) return null
        val manga = SManga.create().apply { this.url = url.encodedPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val entry = document.selectFirst(".entry.single")!!
        // Posts that only hold a video, or tease a comic read elsewhere, have no pages
        val chapterList = if (entry.galleryImages().isEmpty() && entry.galleryLink() == null) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Capítulo"
                    date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
                },
            )
        }
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst(".page-title h1")!!.text()
        // Posts from 2016 have no og:image, only a small cover at the top
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?: document.selectFirst(".entry.single img")?.imageUrl()
        genre = (document.select("ul.cats a") + document.select(".page-title ul.tags a"))
            .map { it.text() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(document.summary())
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // "RESUMO:" heads the summary, either in the same paragraph or alone before it
    private fun Document.summary(): String? {
        val paragraph = selectFirst(".entry.single strong:matches(^RESUMO)")?.parent() ?: return null
        return paragraph.ownTextOrNull() ?: paragraph.nextElementSibling()?.textOrNull()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val entry = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup().selectFirst(".entry.single")!!
        val images = entry.galleryImages().ifEmpty {
            // Older posts link to a separate gallery page instead
            val gallery = entry.galleryLink() ?: return emptyList()
            client.get(gallery, Fetch.DOCUMENT).asJsoup().select("td img").map { it.absUrl("src") }
        }
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    // Pages live under /galerias/, apart from the site's banners; lazy images repeat in <noscript>
    private fun Element.galleryImages() = select("img").mapNotNull { it.imageUrl() }.filter { "/galerias/" in it }.distinct()

    private fun Element.galleryLink() = selectFirst("a[href*=/galerias/]")?.absUrl("href")

    private fun Element.imageUrl() = absUrl(if (hasAttr("data-lazy-src")) "data-lazy-src" else "src").takeIf { it.startsWith("http") }

    private fun Element.coverUrl() = (attrOrNull("data-lazy-srcset") ?: attrOrNull("srcset"))
        ?.split(",")
        ?.map { it.trim().split(" ") }
        ?.maxByOrNull { it.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0 }
        ?.first()
        ?: imageUrl()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select(".sidepanel .categories a")
        .map { Category(it.text(), it.absUrl("href").toHttpUrl().pathSegments.last()) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<Category>>() ?: return FilterList()
        return FilterList(CategoryFilter(categories))
    }
}
