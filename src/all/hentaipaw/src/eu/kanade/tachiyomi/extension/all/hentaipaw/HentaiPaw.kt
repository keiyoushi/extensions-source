package eu.kanade.tachiyomi.extension.all.hentaipaw

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class HentaiPaw : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getListing(searchUrl("", SortOption.POPULAR), page)

    override suspend fun getLatestUpdates(page: Int) = getListing(baseUrl.toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getListing(searchUrl(query.trim(), filters.firstInstance<SortFilter>().selected), page)

    private fun searchUrl(keyword: String, sort: SortOption) = "$baseUrl/articles/search".toHttpUrl().newBuilder()
        .addQueryParameter("keyword", keyword)
        .apply { sort.type?.let { addQueryParameter("type", it) } }
        .build()

    private suspend fun getListing(url: HttpUrl, page: Int): MangasPage {
        val document = client.get(url.newBuilder().setQueryParameter("page", page.toString()).build(), Fetch.DOCUMENT).asJsoup()
        // The home page also has a ranking block, whose grid isn't next to a heading
        val mangas = document.select("h1 ~ div.grid > a[href^=\"/articles/\"]").map(::galleryCard)
        return MangasPage(mangas, document.selectFirst("nav[aria-label=pagination] a[aria-label=\"next page\"]") != null)
    }

    private fun galleryCard(card: Element) = SManga.create().apply {
        setUrlWithoutDomain(card.absUrl("href"))
        title = card.selectFirst("div[title]")!!.attr("title")
        thumbnail_url = card.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        val isListing = segments[0] in LISTING_PATHS || (segments[0] == "articles" && segments.getOrNull(1) in ARTICLE_LISTINGS)
        return if (isListing) getListing(url, page) else super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = when (url.pathSegments[0]) {
            "articles" -> url.pathSegments.getOrNull(1)
            "viewer" -> url.queryParameter("articleId")
            else -> null
        }?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = "/articles/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst("#article-details")!!
        val tags = details.select("#article-tag-information div:has(> h3)").associate { row ->
            row.selectFirst("h3")!!.text().removeSuffix(":") to row.select("a").map { it.text() }
        }
        val gallery = SManga.create().apply {
            url = manga.url
            title = details.selectFirst("h1")!!.text()
            thumbnail_url = details.selectFirst("img")?.absUrl("src")
            author = tags["Artists"]?.joinToString()?.ifEmpty { null }
            artist = author
            genre = tags["Tags"]?.joinToString()?.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .field("Parodies", tags["Parodies"].orEmpty())
                .field("Characters", tags["Characters"].orEmpty())
                .field("Groups", tags["Groups"].orEmpty())
                .field("Language", tags["Language"].orEmpty())
                .field("Category", tags["Category"].orEmpty())
                .build()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = "/viewer?articleId=${manga.url.substringAfterLast('/')}&page=1"
            name = "Chapter"
        }
        return SMangaUpdate(gallery, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .extractNextJs<Viewer>()!!
        .slides.mapIndexed { index, slide -> Page(index, imageUrl = slide.src) }

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter())

    companion object {
        private val LISTING_PATHS = setOf("tags", "artists", "parodies", "characters", "groups", "categories")
        private val ARTICLE_LISTINGS = setOf("search", "rank")
    }
}
