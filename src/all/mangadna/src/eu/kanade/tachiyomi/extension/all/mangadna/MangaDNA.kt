package eu.kanade.tachiyomi.extension.all.mangadna

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDNA : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/manga/page/$page?orderby=rating").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/manga/page/$page?orderby=latest").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", trimmedQuery)
                .addQueryParameter("page", page.toString())
                .build()
            return mangaListParse(client.get(url).asJsoup())
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected

        val builder: HttpUrl.Builder = if (genre.isNullOrEmpty()) {
            "$baseUrl/manga/page/$page".toHttpUrl().newBuilder()
        } else {
            "$baseUrl/manga-genre/$genre/$page".toHttpUrl().newBuilder()
        }
        if (!sort.isNullOrEmpty()) builder.addQueryParameter("orderby", sort)
        return mangaListParse(client.get(builder.build()).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
            .apply { initialized = true }
    }

    private fun mangaListParse(document: Document): MangasPage {
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

    // Details and chapters share the manga page, so both are always parsed from one request.
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { url = manga.url },
            chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga {
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

    private fun chapterListParse(document: Document): List<SChapter> = document.select("ul.row-content-chapter li.a-h").map { li ->
        SChapter.create().apply {
            val link = li.selectFirst("a.chapter-name, a")!!
            setUrlWithoutDomain(link.attr("abs:href"))
            name = link.text()
            val time = li.selectFirst(".chapter-time")
            val raw = time?.attr("title")?.takeIf { it.isNotEmpty() } ?: time?.text().orEmpty()
            date_upload = dateFormat.tryParseDate(raw)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.read-content img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

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

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)
}
