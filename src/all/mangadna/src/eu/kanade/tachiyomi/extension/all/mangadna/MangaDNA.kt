package eu.kanade.tachiyomi.extension.all.mangadna

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaDNA : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page?orderby=rating", headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page?orderby=latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", trimmedQuery)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected

        val builder: HttpUrl.Builder = if (genre.isNullOrEmpty()) {
            "$baseUrl/manga/page/$page".toHttpUrl().newBuilder()
        } else {
            "$baseUrl/manga-genre/$genre/$page".toHttpUrl().newBuilder()
        }
        if (!sort.isNullOrEmpty()) builder.addQueryParameter("orderby", sort)
        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val cards = document.select("div.home-item")
        val filtered = if (lang == "en") cards.filterNot { it.isRaw() } else cards
        val mangas = filtered.map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h3.htitle a, .hthumb a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.attr("title").ifBlank { link.text() }
                thumbnail_url = card.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.isRaw(): Boolean = selectFirst("a[href]")?.attr("href")?.trimEnd('/')?.endsWith("-raw") == true

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst("div.summary_content_wrap, div.tab-summary") ?: document

        return SManga.create().apply {
            title = document.selectFirst("h1.entry-title")?.text()
                ?: document.selectFirst("div.post-title h1, h1")?.text()
                ?: throw Exception("Title not found")

            thumbnail_url = document.selectFirst("div.summary_image img")?.imgAttr()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")

            // Labels arrive with/without trailing colons inconsistently — normalize.
            val rows = info.select("div.post-content_item").associate { item ->
                val label = item.selectFirst(".summary-heading")?.text().orEmpty().trimEnd(':').trim()
                val value = item.selectFirst(".summary-content")?.text().orEmpty().trim()
                label to value
            }

            // Author / Artist / Genre values arrive as concatenated <a> text without
            // separators in the parent `.text()`, so pick the anchors individually.
            author = info.select("div.author-content a").joinToString(", ") { it.text() }
                .takeIf { it.isNotEmpty() && it != "Updating" }
            artist = info.select("div.artist-content a").joinToString(", ") { it.text() }
                .takeIf { it.isNotEmpty() && it != "Updating" }

            val genreNames = info.select("div.genres-content a").map { it.text().trim() }
                .filter { it.isNotEmpty() }
            val type = rows["Type"]?.takeIf { it.isNotEmpty() && it != "Updating" }
            genre = (genreNames + listOfNotNull(type)).distinct().joinToString(", ").ifEmpty { null }

            status = parseStatus(rows["Status"])
            description = buildDescription(document, info, rows)
        }
    }

    private fun buildDescription(doc: Document, info: Element, rows: Map<String, String>): String? {
        val synopsis = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: doc.selectFirst("div.summary__content, div.dsct, div.manga-content p")?.text()

        val rating = info.selectFirst("#averagerate")?.text()?.trim()
        val ratingMax = info.selectFirst("[property=bestRating]")?.text()?.trim() ?: "5"
        val ratingVotes = info.selectFirst("#countrate")?.text()?.trim()
        val ratingLine = rating?.takeIf { it.isNotEmpty() }?.let {
            if (!ratingVotes.isNullOrEmpty()) {
                "Rating: $it / $ratingMax ($ratingVotes votes)"
            } else {
                "Rating: $it / $ratingMax"
            }
        }

        val parts = buildList {
            synopsis?.takeIf { it.isNotEmpty() }?.let(::add)
            rows["Alternative"]?.takeIf { it.isNotEmpty() && it != "Updating" }
                ?.let { add("Alternative: $it") }
            rows["Release"]?.takeIf { it.isNotEmpty() }?.let { add("Released: $it") }
            ratingLine?.let(::add)
        }
        return parts.joinToString("\n\n").ifEmpty { null }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.row-content-chapter li.a-h").map { li ->
            SChapter.create().apply {
                val link = li.selectFirst("a.chapter-name, a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                name = link.text()
                val time = li.selectFirst(".chapter-time")
                val raw = time?.attr("title")?.takeIf { it.isNotEmpty() } ?: time?.text().orEmpty()
                date_upload = dateFormat.tryParse(raw)
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.read-content img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = getFilters()

    private fun parseStatus(raw: String?): Int = when (raw?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed", "complete", "finished" -> SManga.COMPLETED
        "hiatus", "on hiatus", "on hold" -> SManga.ON_HIATUS
        "cancelled", "canceled", "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        else -> attr("abs:src")
    }

    private val dateFormat by lazy { SimpleDateFormat("dd MMM yy", Locale.ENGLISH) }
}
