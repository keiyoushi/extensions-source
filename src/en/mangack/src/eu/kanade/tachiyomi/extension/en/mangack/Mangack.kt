package eu.kanade.tachiyomi.extension.en.mangack

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Mangack : HttpSource() {

    override val name = "Mangack"

    override val baseUrl = "https://mangack.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = mangaListUrlBuilder(page)
            .addQueryParameter("orderby", "date")
            .addQueryParameter("order", "desc")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Latest ===============================

    // The REST `orderby=modified` reflects any edit to the manga post, not just
    // chapter publication, so we scrape /updates/ for true latest-by-chapter.
    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page <= 1) "/updates/" else "/updates/page/$page/"
        return GET(baseUrl + path, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".latestmanga .Latest_chapter_update").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.attr("title").ifBlank { link.text() }
                thumbnail_url = card.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst(".pagination a.next, a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fetchTaxonomies()

        val builder = mangaListUrlBuilder(page)
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            builder.addQueryParameter("search", trimmedQuery)
        }
        filters.filterIsInstance<UriFilter>().forEach { it.applyTo(builder) }
        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    private fun mangaListUrlBuilder(page: Int): HttpUrl.Builder = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", PAGE_SIZE.toString())
        .addQueryParameter("_embed", "wp:featuredmedia")

    private fun mangaListParse(response: Response): MangasPage {
        val list = response.parseAs<List<MangaDto>>().map { dto ->
            SManga.create().apply {
                title = dto.title()
                thumbnail_url = dto.coverUrl()
                setUrlWithoutDomain(dto.link())
            }
        }
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(list, currentPage < totalPages)
    }

    // ============================== Details ================================

    // The Ifenzi theme renders broken Author / Type rows (`foreach() over bool`),
    // but the taxonomy slugs survive on the <article> class list. Scraping the
    // public manga page also gives us Followers / Views, which the REST DTO omits.
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val article = doc.selectFirst("article")
        val articleClasses = article?.classNames().orEmpty()

        return SManga.create().apply {
            title = doc.selectFirst("h1.entry-title")?.text()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.removeSuffix(" mangack")
                ?: throw Exception("Title not found")

            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: article?.selectFirst("figure img, .mediumthumbnail1 img")?.imgAttr()

            val typeName = articleClasses
                .firstOrNull { it.startsWith("comic-type-") }
                ?.removePrefix("comic-type-")
                ?.humanizeSlug()

            val genreNames = articleClasses
                .filter { it.startsWith("Genres-") }
                .map { it.removePrefix("Genres-").humanizeSlug() }

            val statusSlug = articleClasses
                .firstOrNull { it.startsWith("manga-status-") }
                ?.removePrefix("manga-status-")

            genre = (genreNames + listOfNotNull(typeName))
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .ifEmpty { null }

            status = parseStatus(statusSlug)

            description = buildDescription(doc, article)
        }
    }

    private fun buildDescription(doc: Document, article: Element?): String? {
        val synopsis = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val infobox = (article ?: doc).select("table.infobox tr").associate { tr ->
            val label = tr.selectFirst("td:first-child, th:first-child")?.text().orEmpty()
            val rawValue = tr.selectFirst("td:nth-child(2), th:nth-child(2)")?.text().orEmpty()
            val value = if ("Warning" in rawValue) "" else rawValue
            label to value
        }

        val followers = doc.select(".follow-text")
            .firstOrNull { it.text().startsWith("Followers", ignoreCase = true) }?.text()
        val views = doc.select(".follow-text")
            .firstOrNull { it.text().startsWith("Views", ignoreCase = true) }?.text()

        val parts = buildList {
            synopsis?.takeIf { it.isNotEmpty() }?.let(::add)
            infobox["Alternative"]?.takeIf { it.isNotEmpty() }?.let { add("Alternative: $it") }
            infobox["Realized in"]?.takeIf { it.isNotEmpty() }?.let { add("Year: $it") }
            followers?.takeIf { it.isNotEmpty() }?.let(::add)
            views?.takeIf { it.isNotEmpty() }?.let(::add)
        }
        return parts.joinToString("\n\n").ifEmpty { null }
    }

    // =============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapterslist li").map { li ->
            SChapter.create().apply {
                val link = li.selectFirst("a.title, a[href*=/chapter/]")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                name = link.ownText().ifEmpty { link.text() }
                date_upload = parseChapterDate(li.selectFirst(".entry-date")?.text())
            }
        }
    }

    // =============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.trim('/').substringAfterLast('/')
        val url = "$baseUrl/wp-json/wp/v2/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id,content")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<List<ChapterContentDto>>().firstOrNull()
            ?: return emptyList()
        return IMG_SRC_REGEX.findAll(dto.contentHtml())
            .map { it.groupValues[1] }
            .filterNot(SKIP_ASSET_REGEX::containsMatchIn)
            .toList()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ===============================

    private var genres: List<TaxonomyOption> = emptyList()
    private var years: List<TaxonomyOption> = emptyList()
    private var taxonomyState = TaxonomyState.NOT_FETCHED
    private var taxonomyAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun getFilterList(): FilterList {
        fetchTaxonomies()
        return FilterList(
            buildList {
                add(TypeFilter())
                add(StatusFilter())
                if (years.isNotEmpty()) add(YearFilter(years))
                add(SortFilter())
                add(Filter.Separator())
                when {
                    genres.isNotEmpty() -> add(GenreFilterGroup(genres))
                    taxonomyAttempts >= MAX_TAXONOMY_ATTEMPTS -> add(WarningHeader(GENRE_FETCH_FAILED_MSG))
                    else -> add(WarningHeader(GENRE_LOADING_MSG))
                }
            },
        )
    }

    private fun fetchTaxonomies() {
        if (taxonomyState != TaxonomyState.NOT_FETCHED || taxonomyAttempts >= MAX_TAXONOMY_ATTEMPTS) return
        taxonomyState = TaxonomyState.FETCHING
        taxonomyAttempts++
        scope.launch {
            try {
                val genresList = client
                    .newCall(GET("$baseUrl/wp-json/wp/v2/Genres?per_page=100&hide_empty=true", headers))
                    .execute()
                    .parseAs<List<TermPayloadDto>>()
                val yearsList = client
                    .newCall(GET("$baseUrl/wp-json/wp/v2/realised?per_page=100&hide_empty=true&orderby=name&order=desc", headers))
                    .execute()
                    .parseAs<List<TermPayloadDto>>()
                genres = genresList.map { TaxonomyOption(it.id, it.name) }.sortedBy { it.name }
                years = yearsList.map { TaxonomyOption(it.id, it.name) }
                taxonomyState = TaxonomyState.FETCHED
            } catch (e: Exception) {
                Log.e("Mangack", "Failed to fetch taxonomies", e)
                taxonomyState = TaxonomyState.NOT_FETCHED
            }
        }
    }

    private enum class TaxonomyState { NOT_FETCHED, FETCHING, FETCHED }

    // =============================== Helpers ===============================

    private fun parseStatus(slug: String?): Int = when (slug?.lowercase(Locale.ROOT)) {
        "ongoing", "publishing", "updating" -> SManga.ONGOING
        "completed", "complete", "finished" -> SManga.COMPLETED
        "hiatus", "on-hiatus", "on-hold" -> SManga.ON_HIATUS
        "cancelled", "canceled", "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String.humanizeSlug(): String = split('-')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
        }

    private fun parseChapterDate(raw: String?): Long {
        if (raw.isNullOrEmpty()) return 0L
        val text = raw.lowercase(Locale.ROOT)
        val number = RELATIVE_NUMBER_REGEX.find(text)?.groupValues?.get(1)?.toLongOrNull()
        if (number != null) {
            val msPerUnit = when {
                "second" in text -> 1_000L
                "minute" in text -> 60_000L
                "hour" in text -> 3_600_000L
                "day" in text -> 86_400_000L
                "week" in text -> 604_800_000L
                "month" in text -> 2_592_000_000L
                "year" in text -> 31_536_000_000L
                else -> return 0L
            }
            return System.currentTimeMillis() - number * msPerUnit
        }
        return absoluteDateFormat.tryParse(raw)
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }

    private val absoluteDateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val MAX_TAXONOMY_ATTEMPTS = 3
        private const val GENRE_LOADING_MSG = "Genres are loading — press Reset to refresh the filter list."
        private const val GENRE_FETCH_FAILED_MSG = "Could not load the Genres list. Check your connection and press Reset."

        private val IMG_SRC_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""")
        private val SKIP_ASSET_REGEX = Regex(
            """(?i)/wp-content/(?:themes|plugins)/|/(?:logo|icon|cropped|preroll|placeholder|loading|spinner|chainsaw)[^/]*\.(?:png|jpe?g|webp|gif|svg)""",
        )
        private val RELATIVE_NUMBER_REGEX = Regex("""^(\d+)""")
    }
}
