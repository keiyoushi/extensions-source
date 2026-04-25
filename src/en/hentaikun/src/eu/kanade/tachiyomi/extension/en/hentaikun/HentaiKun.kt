package eu.kanade.tachiyomi.extension.en.hentaikun

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiKun : HttpSource() {

    override val name = "HentaiKun"

    override val baseUrl = "https://hentaikun.com"

    override val lang = "en"

    override val supportsLatest = true

    private val mangaUrl = "$baseUrl/manga"

    // =============================== Popular ================================

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page > 1) "$page/" else ""
        return GET("$mangaUrl/manga-list/most-viewed/$pageStr", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return MangasPage(
            parseTableListing(document),
            hasNextPage = document.selectFirst("ul.pagination li[aria-label=Next]") != null,
        )
    }

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page > 1) "$page/" else ""
        return GET("$mangaUrl/manga-list/last-updated/$pageStr", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val pageStr = if (page > 1) "$page/" else ""
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "title"

        return if (query.isNotBlank()) {
            GET("$mangaUrl/search/$searchType/${query.trim()}/$pageStr", headers)
        } else {
            popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val hasNextPage = document.selectFirst("ul.pagination li[aria-label=Next]") != null

        val mangas = if (document.selectFirst("table.table-striped") != null) {
            parseTableListing(document)
        } else {
            parseGalleryListing(document)
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Manga Details ================================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.single_title h1")?.text()
                ?: throw Exception("Title not found")

            thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")

            author = document.select("h2:has(strong:contains(Artist)) a")
                .joinToString(", ") { it.text() }
                .ifEmpty { null }

            val category = document.selectFirst("h2:has(strong:contains(Category)) a")?.text()
            val tags = document.select("div.desc a[href*='/tag/'] span.label-danger")
                .map { it.text() }
            genre = buildList {
                if (category != null) add(category)
                addAll(tags)
            }.joinToString(", ").ifEmpty { null }

            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    // ========================= Chapter List =================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("table a.readchap").map { anchor ->
            SChapter.create().apply {
                name = anchor.text().ifEmpty { "Chapter" }
                setUrlWithoutDomain(anchor.attr("href").trim())
                val row = anchor.closest("tr")
                val dateText = row?.selectFirst("td:last-child h6")?.text()
                date_upload = dateText?.let { dateFormat.tryParse(it) } ?: 0L
                chapter_number = chapterNumberRegex.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    // ========================= Page List ====================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val firstImageUrl = document.selectFirst("img.image_rin")?.attr("src")?.trim()
            ?: throw Exception("Could not find any images for this chapter.")

        val totalPages = document.select("label:contains(Page) + select option").size
            .takeIf { it > 0 }
            ?: document.select("select[onchange]").last()?.select("option")?.size
            ?: 0

        if (totalPages == 0) return listOf(Page(0, imageUrl = firstImageUrl))

        val basePath = firstImageUrl.substringBeforeLast('/') + "/"
        val fileName = firstImageUrl.substringAfterLast('/').substringBeforeLast('.')
        val ext = firstImageUrl.substringAfterLast('.')

        val prefix = fileName.replace(trailingDigitsRegex, "")
        val numberPart = fileName.substring(prefix.length)
        val padLength = numberPart.length

        return (1..totalPages).map { i ->
            val pageNum = if (padLength > 0) i.toString().padStart(padLength, '0') else i.toString()
            Page(i - 1, imageUrl = "$basePath$prefix$pageNum.$ext")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters =====================================

    override fun getFilterList() = FilterList(
        SearchTypeFilter(),
    )

    // ========================= Helpers =====================================
    private fun parseTableListing(document: Document): List<SManga> {
        return document.select("table.table-striped tr:not(.danger)").mapNotNull { row ->
            val anchor = row.selectFirst("td:first-child a") ?: return@mapNotNull null
            SManga.create().apply {
                title = anchor.text()
                setUrlWithoutDomain(anchor.absUrl("href"))
                thumbnail_url = Jsoup.parseBodyFragment(anchor.attr("title"), baseUrl)
                    .selectFirst("img")?.absUrl("src")
            }
        }
    }

    private fun parseGalleryListing(document: Document): List<SManga> {
        return document.select("div.thumbnail[id^='galary-']").mapNotNull { div ->
            val overlayAnchor = div.selectFirst("div.overlay a") ?: return@mapNotNull null
            SManga.create().apply {
                title = overlayAnchor.text()
                setUrlWithoutDomain(overlayAnchor.absUrl("href"))
                thumbnail_url = div.selectFirst("img.img-responsive")?.absUrl("src")
            }
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
    }

    companion object {
        private val chapterNumberRegex = Regex("""(\d+(?:\.\d+)?)""")
        private val trailingDigitsRegex = Regex("""\d+$""")
    }
}
