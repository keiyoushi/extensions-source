package eu.kanade.tachiyomi.extension.en.kingcomix

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class KingComiX : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
        }
        url.addPathSegment("") // Adds trailing slash natively
        val document = client.get(url.build()).asJsoup()

        return parseFilteredManga(document, page)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        } else {
            val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: ""
            val tag = filters.firstInstanceOrNull<TagFilter>()?.toUriPart() ?: ""

            // Ensure categories and tags are not mixed. Defaulting to category if both are picked.
            when {
                category.isNotBlank() -> {
                    url.addPathSegment("category")
                    url.addPathSegment(category)
                }
                tag.isNotBlank() -> {
                    url.addPathSegment("tag")
                    url.addPathSegment(tag)
                }
            }
        }
        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
        }
        url.addPathSegment("") // Adds trailing slash natively

        val document = client.get(url.build()).asJsoup()
        return parseFilteredManga(document, page)
    }

    private fun parseFilteredManga(document: Document, page: Int): MangasPage {
        val mangas = document.select("div.entry, article.thumb-block").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("h2.information a, a[title]")!!

                title = a.text().ifEmpty { a.attr("title") }
                setUrlWithoutDomain(a.absUrl("href"))

                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                }
            }
        }

        val hasNextPage = document.select(".pagination a").mapNotNull { it.text().toIntOrNull() }.any { it > page }

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search ignores filters."),
        Filter.Header("Select EITHER a Category OR a Tag."),
        Filter.Header("If both are selected, Category takes priority."),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    // =========================== Manga Update ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = baseUrl + manga.url
        val document = client.get(url).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document, url))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.singleTitle-h1, h1.widget-title")!!.text()
        author = document.selectFirst("meta[name=author]")?.attr("content")

        val tags = document.select(".caTotal .tagsPost a.taxLink").map { it.text() }
        genre = tags.joinToString(", ")

        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".entry-content img")?.attr("abs:src")

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun parseChapterList(document: Document, url: String): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(url)
            date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
        },
    )

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return document.select(".entry-content img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }
}
