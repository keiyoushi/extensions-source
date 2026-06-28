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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class XyzComics : HttpSource() {

    override val name = "XYZ Comics"

    override val baseUrl = "https://xyzcomics.com"

    override val lang = "en"

    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    companion object {
        private const val ARTIST_TAG_FILTER_NAME = "Artist or Tag"
    }

    // ──────────────────────────────────────────────
    //  Popular Manga — "Show All" listing
    // ──────────────────────────────────────────────

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/allsexkomix/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ──────────────────────────────────────────────
    //  Search — supports Artist/Tag filter
    // ──────────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val artistTag = filters.firstInstance<Filter.Text>()
            .takeIf { it.name == ARTIST_TAG_FILTER_NAME }
            ?.state
            ?.trim()

        return if (!artistTag.isNullOrBlank()) {
            val slug = artistTag.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
            val url = if (page == 1) {
                "$baseUrl/tag/$slug/"
            } else {
                "$baseUrl/tag/$slug/page/$page/"
            }
            GET(url, headers)
        } else {
            GET("$baseUrl/page/$page/?s=$query", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ──────────────────────────────────────────────
    //  Shared list parser (Popular, Search, Tag pages)
    // ──────────────────────────────────────────────

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val items = document.select("article.post").map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = document.selectFirst(
            "a.nextp, .pagenav a.next, a.page-numbers.next, a[rel=next]",
        ) != null

        return MangasPage(items, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val thumbLink = element.selectFirst("figure.post-image a")!!
        manga.setUrlWithoutDomain(thumbLink.attr("href"))

        val titleEl = element.selectFirst("h2.post-title a")!!
        manga.title = titleEl.text().trim()

        val img = element.selectFirst("figure.post-image img.wp-post-image")
        manga.thumbnail_url = when {
            img != null -> {
                img.attr("data-src").ifEmpty {
                    img.attr("src")
                }
            }
            else -> ""
        }

        return manga
    }

    // ──────────────────────────────────────────────
    //  Manga Details
    // ──────────────────────────────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        manga.initialized = true

        manga.title = document.selectFirst("h1.post-title a, h1.post-title")!!.text().trim()
        manga.thumbnail_url = document.selectFirst(".pswp-gallery .pswp-gallery__item a[href]")?.attr("abs:href") ?: ""

        val tags = document.select("a.post-tag-button")
        manga.genre = tags.joinToString(", ") { it.text().trim() }.ifEmpty { null }

        manga.status = SManga.UNKNOWN

        return manga
    }

    // ──────────────────────────────────────────────
    //  Chapter List — single chapter per comic
    // ──────────────────────────────────────────────

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val document = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            chapter_number = 1f
            setUrlWithoutDomain(manga.url)
            date_upload = document.selectFirst("time[datetime]")?.attr("datetime")
                ?.let { dateFormat.tryParse(it) }
                ?: 0L
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ──────────────────────────────────────────────
    //  Page List — extract from PhotoSwipe gallery
    // ──────────────────────────────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".pswp-gallery .pswp-gallery__item a[href]")
            .mapIndexed { index, link -> Page(index, "", link.attr("abs:href")) }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ──────────────────────────────────────────────
    //  Filters — Artist / Tag only
    // ──────────────────────────────────────────────

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("See all artists & tags: $baseUrl/all-the-artists-and-tags/"),
        Filter.Separator(),
        object : Filter.Text(ARTIST_TAG_FILTER_NAME, "") {},
    )
}
