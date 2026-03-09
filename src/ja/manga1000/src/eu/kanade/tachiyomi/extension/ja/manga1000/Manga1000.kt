package eu.kanade.tachiyomi.extension.ja.manga1000

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

class Manga1000 : HttpSource() {

    override val name = "Manga1000"
    override val baseUrl = "https://hachiraw.win"
    override val lang = "ja"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Upgrade-Insecure-Requests", "1")

    // ============================== Popular / Homepage ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/"
        } else {
            "$baseUrl/page/$page/"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.post.manga").map { element ->
            SManga.create().apply {
                element.selectFirst(".entry-title a")?.let {
                    title = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.selectFirst(".featured-thumb img")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("nav.pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search & Filters =====================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = if (page == 1) {
                "$baseUrl/search?query=$query"
            } else {
                "$baseUrl/search/page/$page/?query=$query"
            }
            return GET(url, headers)
        }

        var categoryId = ""
        filters.forEach { filter ->
            if (filter is CategoryFilter) {
                categoryId = filter.toUriPart()
            }
        }

        val url = if (categoryId.isNotEmpty()) {
            if (page == 1) {
                "$baseUrl/category/$categoryId/"
            } else {
                "$baseUrl/category/$categoryId/page/$page/"
            }
        } else {
            if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Text search ignores categories"),
        Filter.Separator(),
        CategoryFilter(),
    )

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.entry-title")?.text()?.substringBefore(" - ") ?: ""

            thumbnail_url = document.selectFirst(".entry-content img")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }

            author = document.selectFirst(".entry-content > p:contains(Author:)")?.text()?.replace("Author:", "")?.trim()
            genre = document.select(".entry-content > p:contains(Category:) a").joinToString { it.text() }

            val descriptionElements = document.select(".entry-content > p").filter {
                val text = it.text()
                !text.contains("Author:") && !text.contains("Category:")
            }
            description = descriptionElements.joinToString("\n") { it.text() }.trim()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".chaplist table tbody tr td a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.text().trim().ifEmpty { "Chapter" }
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".entry-content img").mapIndexedNotNull { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }

            if (url.isNotEmpty() && !url.contains("lazy.png")) {
                Page(i, "", url)
            } else {
                null
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
