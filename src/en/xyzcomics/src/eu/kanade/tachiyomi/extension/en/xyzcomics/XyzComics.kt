package eu.kanade.tachiyomi.extension.en.xyzcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

@Source
abstract class XyzComics : HttpSource() {

    override val supportsLatest = false

    private class ArtistTagFilter : Filter.Text(ARTIST_TAG_FILTER_NAME)

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/allsexkomix/" else "$baseUrl/allsexkomix/page/$page/"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val artistTag = filters.firstInstanceOrNull<ArtistTagFilter>()?.state?.trim()
        return if (!artistTag.isNullOrBlank()) {
            val slug = artistTag.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
            val url = if (page == 1) "$baseUrl/tag/$slug/" else "$baseUrl/tag/$slug/page/$page/"
            GET(url, headers)
        } else {
            val url = if (page == 1) "$baseUrl/?s=$query" else "$baseUrl/page/$page/?s=$query"
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val doc = response.asJsoup()
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

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val manga = SManga.create()
        manga.initialized = true
        manga.title = doc.selectFirst("h1.post-title a, h1.post-title")?.text()
            ?: doc.selectFirst("title")!!.text()
        manga.thumbnail_url = doc.selectFirst(".pswp-gallery .pswp-gallery__item a[href]")
            ?.attr("abs:href")
        val tags = doc.select("a.post-tag-button")
        manga.genre = tags.joinToString(", ") { it.text().trim() }.ifEmpty { "" }
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            chapter_number = 1f
            setUrlWithoutDomain(manga.url)
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select(".pswp-gallery .pswp-gallery__item a[href]")
            .mapIndexed { i, link -> Page(i, "", link.attr("abs:href")) }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("See all artists & tags: $baseUrl/all-the-artists-and-tags/"),
        Filter.Separator(),
        ArtistTagFilter(),
    )

    companion object {
        private const val ARTIST_TAG_FILTER_NAME = "Artist or Tag"
    }
}
