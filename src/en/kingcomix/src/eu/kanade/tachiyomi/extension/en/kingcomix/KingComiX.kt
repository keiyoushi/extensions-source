package eu.kanade.tachiyomi.extension.en.kingcomix

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KingComiX : HttpSource() {

    override val name = "KingComiX"

    override val baseUrl = "https://kingcomix.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
        }
        url.addPathSegment("") // Adds trailing slash natively
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

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

        val hasNextPage = document.selectFirst(".pagination a:contains(Next), .pagination a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            // Text search does not support pagination on the site.
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

            if (page > 1) {
                url.addPathSegment("page")
                url.addPathSegment(page.toString())
            }
            url.addPathSegment("") // Adds trailing slash natively
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = popularMangaParse(response)
        val isTextSearch = response.request.url.queryParameter("s") != null

        return if (isTextSearch) {
            MangasPage(page.mangas, false) // Text search doesn't support pagination, hardcoding hasNextPage false
        } else {
            page
        }
    }

    // =============================== Filters ==============================
    override fun getFilterList() = FilterList(
        Filter.Header("Text search ignores filters."),
        Filter.Header("Select EITHER a Category OR a Tag."),
        Filter.Header("If both are selected, Category takes priority."),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.singleTitle-h1, h1.widget-title")!!.text()
            author = document.selectFirst("meta[name=author]")?.attr("content")

            val tags = document.select(".caTotal .tagsPost a.taxLink").map { it.text() }
            genre = tags.joinToString(", ")

            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".entry-content img")?.attr("abs:src")

            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = document.selectFirst("meta[property=article:published_time]")
                    ?.attr("content")
                    ?.let { dateFormat.tryParse(it) } ?: 0L
            },
        )
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".entry-content img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
