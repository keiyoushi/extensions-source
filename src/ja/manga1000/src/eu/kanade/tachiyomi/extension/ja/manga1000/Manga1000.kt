package eu.kanade.tachiyomi.extension.ja.manga1000

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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Manga1000 : KeiSource() {

    override val supportsLatest = false

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Upgrade-Insecure-Requests", "1")

    // ============================== Popular / Homepage ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
            urlBuilder.addPathSegment("")
        }
        return getMangaList(urlBuilder.build())
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select("article.post.manga").mapNotNull { element ->
            val a = element.selectFirst(".entry-title a") ?: return@mapNotNull null
            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("abs:href"))
                thumbnail_url = element.selectFirst(".featured-thumb img")?.run {
                    attr("abs:data-src").ifEmpty { attr("abs:src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("nav.pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search & Filters =====================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search")
            if (page > 1) {
                urlBuilder.addPathSegment("page")
                urlBuilder.addPathSegment(page.toString())
            }
            urlBuilder.addQueryParameter("query", query)
            return getMangaList(urlBuilder.build())
        }

        val categoryId = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart().orEmpty()

        if (categoryId.isNotEmpty()) {
            urlBuilder.addPathSegment("category")
            urlBuilder.addPathSegment(categoryId)
            urlBuilder.addPathSegment("")
        }
        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
            urlBuilder.addPathSegment("")
        }

        return getMangaList(urlBuilder.build())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Text search ignores categories"),
        Filter.Separator(),
        CategoryFilter(),
    )

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")?.text()?.substringBefore(" - ")!!

        thumbnail_url = document.selectFirst(".entry-content img")?.run {
            attr("abs:data-src").ifEmpty { attr("abs:src") }
        }

        author = document.selectFirst(".entry-content > p:contains(Author:)")?.text()?.replace("Author:", "")?.trim()
        genre = document.select(".entry-content > p:contains(Category:) a").joinToString { it.text() }

        val descriptionElements = document.select(".entry-content > p").filter {
            val text = it.text()
            !text.contains("Author:") && !text.contains("Category:")
        }
        description = descriptionElements.joinToString("\n") { it.text() }
    }

    // ============================== Chapters ==============================
    private fun chapterListParse(document: Document): List<SChapter> = document.select(".chaplist table tbody tr td a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.text().ifEmpty { "Chapter" }
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".entry-content img").mapIndexedNotNull { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }

            if (url.isNotEmpty() && !url.contains("lazy.png")) {
                Page(i, imageUrl = url)
            } else {
                null
            }
        }
    }
}
