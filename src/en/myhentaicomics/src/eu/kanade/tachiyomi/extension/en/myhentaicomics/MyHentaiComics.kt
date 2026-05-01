package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class MyHentaiComics : HttpSource() {

    override val name = "MyHentaiComics"
    override val baseUrl = "https://myhentaicomics.com"
    override val lang = "en"
    override val supportsLatest = true

    // =============================== Popular ================================

    // Popular = most viewed
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/views/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/gallery/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        // Text search takes priority
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search/$page".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        // Category filter
        if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
            val catId = categoryFilter.toUriPart()
            return GET("$baseUrl/gallery/category/$catId/$page", headers)
        }

        // Sort filter
        val sortPath = sortFilter?.toUriPart() ?: "gallery"
        return GET("$baseUrl/$sortPath/$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseComicListing(response)

    // ============================== Filters =================================

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Text search ignores all filters below"),
        Filter.Separator(),
        SortFilter(),
        Filter.Separator(),
        CategoryFilter(),
    )

    // =========================== Comic Listing ==============================

    private fun parseComicListing(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("li.item:not(.image-block) .comic-inner a").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.absUrl("href"))
                title = el.select("h2.comic-name").text()
                thumbnail_url = el.select("img").first()?.absUrl("src")?.encodeSpaces()
            }
        }

        val hasNextPage = document.selectFirst("li.next a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ==============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val descriptionDiv = document.selectFirst("div.comic-description")

        val categories = descriptionDiv
            ?.select("a[href*='/gallery/category/']")
            ?.map { it.text() }
            .orEmpty()

        val artists = descriptionDiv
            ?.select("a[href*='/gallery/artist/']")
            ?.map { it.text() }
            .orEmpty()

        val groups = descriptionDiv
            ?.select("a[href*='/gallery/group/']")
            ?.map { it.text() }
            .orEmpty()

        val pagesText = descriptionDiv
            ?.select("div")
            ?.firstOrNull { it.ownText().startsWith("Pages:") }
            ?.text()

        return SManga.create().apply {
            title = descriptionDiv?.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.comic-cover img")?.absUrl("src")?.encodeSpaces()
            genre = (categories + artists + groups).joinToString(", ")
            status = SManga.COMPLETED
            initialized = true
            description = buildString {
                if (artists.isNotEmpty()) appendLine("Artists: ${artists.joinToString(", ")}")
                if (groups.isNotEmpty()) appendLine("Groups: ${groups.joinToString(", ")}")
                if (!pagesText.isNullOrEmpty()) append(pagesText)
            }.trimEnd()
        }
    }

    // =========================== Chapter List ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // Extract comic ID from the "Back to gallery" / first page link on the thumbnail page
        val firstPageHref = document.selectFirst("div.comic-cover a")?.absUrl("href")
            ?: return emptyList()

        // href = "https://myhentaicomics.com/gallery/show/59109/1"
        val comicId = firstPageHref
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        return listOf(
            SChapter.create().apply {
                url = "/gallery/show/$comicId/1"
                name = "Chapter 1"
                chapter_number = 1f
                date_upload = 0L
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ============================== Page List ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // The comic ID from the request URL: /gallery/show/59109/1
        val requestUrl = response.request.url.toString()
        val comicId = requestUrl
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        // Get the current page image to derive folder and extension
        val imageUrl = document.selectFirst("ul.gallery-slide li img")?.absUrl("src")
            ?: return emptyList()

        // imageUrl = "https://cdn.myhentaicomics.com/mhc/images/The Mayor 6/original/001.jpg?22"
        val imageBase = imageUrl.substringBeforeLast("/") + "/"
        val fileName = imageUrl.substringAfterLast("/") // "001.jpg?22"
        val fileExtension = fileName.substringAfter(".") // "jpg?22"

        // Find total page count from all pagination links pointing to this comic
        val totalPages = document
            .select("ul li a[href*='/gallery/show/$comicId/']")
            .mapNotNull { it.attr("href").substringAfterLast("/").toIntOrNull() }
            .maxOrNull() ?: 1

        return (1..totalPages).mapIndexed { index, pageNum ->
            val paddedNum = pageNum.toString().padStart(3, '0')
            Page(index, imageUrl = "${imageBase}$paddedNum.$fileExtension".encodeSpaces())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")
}
