package eu.kanade.tachiyomi.extension.en.xyzcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Element

@Source
abstract class XyzComics : KeiSource() {

    override val supportsLatest = false

    private class ArtistTagFilter : Filter.Text(ARTIST_TAG_FILTER_NAME)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/allsexkomix/" else "$baseUrl/allsexkomix/page/$page/"
        return parseMangaList(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val artistTag = filters.firstInstanceOrNull<ArtistTagFilter>()?.state?.trim()
        val url = if (!artistTag.isNullOrBlank()) {
            val slug = artistTag.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
            if (page == 1) "$baseUrl/tag/$slug/" else "$baseUrl/tag/$slug/page/$page/"
        } else {
            if (page == 1) "$baseUrl/?s=$query" else "$baseUrl/page/$page/?s=$query"
        }
        return parseMangaList(url)
    }

    private suspend fun parseMangaList(url: String): MangasPage {
        val doc = client.get(url).asJsoup()
        val items = doc.select("article.post").mapNotNull { popManga(it) }
        val hasNextPage = doc.selectFirst("a.nextp, .pagenav a.next, a.page-numbers.next, a[rel=next]") != null
        return MangasPage(items, hasNextPage)
    }

    private fun popManga(el: Element): SManga? {
        val thumbLink = el.selectFirst("figure.post-image a") ?: return null
        val titleEl = el.selectFirst("h2.post-title a") ?: return null
        val manga = SManga.create()
        manga.setUrlWithoutDomain(thumbLink.absUrl("href"))
        manga.title = titleEl.text().trim()
        val img = el.selectFirst("figure.post-image img.wp-post-image")
        if (img != null) {
            manga.thumbnail_url = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
        return manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchDetails) {
            val doc = client.get(getMangaUrl(manga)).asJsoup()
            manga.title = doc.selectFirst("h1.post-title a, h1.post-title")?.text()
                ?: doc.selectFirst("title")!!.text()
            manga.thumbnail_url = doc.selectFirst(".pswp-gallery .pswp-gallery__item a[href]")
                ?.attr("abs:href")
            val tags = doc.select("a.post-tag-button")
            manga.genre = tags.joinToString(", ") { it.text().trim() }.ifEmpty { "" }
            manga.status = SManga.UNKNOWN
        }

        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            chapter_number = 1f
            setUrlWithoutDomain(manga.url)
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        return doc.select(".pswp-gallery .pswp-gallery__item a[href]")
            .mapIndexed { i, link -> Page(i, imageUrl = link.attr("abs:href")) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("See all artists & tags: $baseUrl/all-the-artists-and-tags/"),
        Filter.Separator(),
        ArtistTagFilter(),
    )

    companion object {
        private const val ARTIST_TAG_FILTER_NAME = "Artist or Tag"
    }
}
