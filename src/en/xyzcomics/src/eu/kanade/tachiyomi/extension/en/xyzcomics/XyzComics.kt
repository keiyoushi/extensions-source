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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XyzComics : HttpSource() {

    override val name = "XYZ Comics"

    override val baseUrl = "https://xyzcomics.com"

    override val lang = "en"

    override val supportsLatest = true

    companion object {
        private const val ARTIST_TAG_FILTER_NAME = "Artist or Tag"
    }

    // ──────────────────────────────────────────────
    //  Popular Manga — "Show All" listing
    // ──────────────────────────────────────────────

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/allsexkomix/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ──────────────────────────────────────────────
    //  Latest Updates — same listing (sorted by date)
    // ──────────────────────────────────────────────

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ──────────────────────────────────────────────
    //  Search — supports Artist/Tag filter
    // ──────────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Find the Artist/Tag filter by name (not by index — fragile)
        val artistTag = filters
            .filterIsInstance<Filter.Text>()
            .firstOrNull { it.name == ARTIST_TAG_FILTER_NAME }
            ?.state
            ?.trim()

        return if (!artistTag.isNullOrBlank()) {
            val slug = artistTag.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
            // Page 1 has no /page/1/ suffix on this site
            val url = if (page == 1) {
                "$baseUrl/tag/$slug/"
            } else {
                "$baseUrl/tag/$slug/page/$page/"
            }
            GET(url, headers)
        } else {
            // Fallback: WordPress keyword search
            GET("$baseUrl/page/$page/?s=$query", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ──────────────────────────────────────────────
    //  Shared list parser (Popular, Latest, Search, Tag pages)
    // ──────────────────────────────────────────────

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val items = document.select("article.post").map { element ->
            popularMangaFromElement(element)
        }

        // Support both main listing pagination (.pagenav) and tag page pagination (.page-numbers)
        val hasNextPage = document.selectFirst(
            "a.nextp, .pagenav a.next, a.page-numbers.next, a[rel=next]",
        ) != null

        return MangasPage(items, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        // Thumbnail link → comic URL
        val thumbLink = element.selectFirst("figure.post-image a")
        val url = thumbLink?.attr("href") ?: ""
        manga.setUrlWithoutDomain(url)

        // Title from h2.post-title a
        val titleEl = element.selectFirst("h2.post-title a")
        manga.title = titleEl?.text()?.trim() ?: "Unknown"

        // Thumbnail image
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

        // Title
        manga.title = document.selectFirst("h1.post-title a, h1.post-title")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.trim()
            ?: "Unknown"

        // Cover — first gallery image or featured image
        val cover = document.selectFirst(
            ".pswp-gallery .pswp-gallery__item a[href], " +
                "figure.post-image img.wp-post-image, " +
                "img.wp-post-image",
        )
        manga.thumbnail_url = when {
            cover != null -> {
                val href = cover.attr("abs:href")
                if (href.isNotBlank()) {
                    href
                } else {
                    cover.attr("src")
                }
            }
            else -> ""
        }

        // Tags / Genres
        val tags = document.select("a.post-tag-button")
        manga.genre = tags.joinToString(", ") { it.text().trim() }.ifEmpty { null }

        // Status — unknown for this site
        manga.status = SManga.UNKNOWN

        return manga
    }

    // ──────────────────────────────────────────────
    //  Chapter List — single chapter per comic
    // ──────────────────────────────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaUrl = response.request.url.toString()

        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            chapter_number = 1f
            setUrlWithoutDomain(mangaUrl.substringAfter(baseUrl).ifEmpty { "/" })
            date_upload = parseDate(document)
            scanlator = ""
        }

        return listOf(chapter)
    }

    // ──────────────────────────────────────────────
    //  Page List — extract from PhotoSwipe gallery
    // ──────────────────────────────────────────────

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // Primary: PhotoSwipe gallery with full-size images
        val galleryItems = document.select(".pswp-gallery .pswp-gallery__item a[href]")
        for ((index, link) in galleryItems.withIndex()) {
            val imgUrl = link.attr("abs:href")
            if (imgUrl.isNotBlank()) {
                pages.add(Page(index, "", imgUrl))
            }
        }

        // Fallback: direct image links in entry-content
        if (pages.isEmpty()) {
            val directLinks = document.select(
                ".entry-content a[href*=\\/uploads\\/], " +
                    ".post-content a[href*=\\/uploads\\/]",
            )
            for ((index, link) in directLinks.withIndex()) {
                val href = link.attr("abs:href")
                if (href.isNotBlank() && !href.contains("svg") && !href.endsWith(".svg")) {
                    pages.add(Page(index, "", href))
                }
            }
        }

        // Last fallback: img tags with uploads in src
        if (pages.isEmpty()) {
            val images = document.select(
                ".entry-content img[src*=\\/uploads\\/], " +
                    ".post-content img[src*=\\/uploads\\/]",
            )
            for ((index, img) in images.withIndex()) {
                val imgUrl = img.attr("data-src").ifEmpty { img.attr("src") }
                if (imgUrl.isNotBlank() && !imgUrl.contains("svg")) {
                    pages.add(Page(index, "", imgUrl))
                }
            }
        }

        return pages
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

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private fun parseDate(document: Document): Long {
        val dateText = document.selectFirst("time[datetime]")?.attr("datetime")
            ?: document.selectFirst(".post-date time[datetime]")?.attr("datetime")
            ?: return 0

        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(dateText)?.time
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateText)?.time
                ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
