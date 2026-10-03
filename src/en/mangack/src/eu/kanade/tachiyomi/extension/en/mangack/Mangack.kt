package eu.kanade.tachiyomi.extension.en.mangack

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangack : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = mangaListUrlBuilder(page)
            .addQueryParameter("orderby", "date")
            .addQueryParameter("order", "desc")
            .build()
        return mangaList(url, page)
    }

    // =============================== Latest ===============================

    // The REST `orderby=modified` reflects any edit to the manga post, not just
    // chapter publication, so we scrape /updates/ for true latest-by-chapter.
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page <= 1) "/updates/" else "/updates/page/$page/"
        val document = client.get(baseUrl + path).asJsoup()
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = mangaListUrlBuilder(page)
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            builder.addQueryParameter("search", trimmedQuery)
        }
        filters.filterIsInstance<UriFilter>().forEach { it.applyTo(builder) }
        return mangaList(builder.build(), page)
    }

    private fun mangaListUrlBuilder(page: Int): HttpUrl.Builder = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", PAGE_SIZE.toString())
        .addQueryParameter("_embed", "wp:featuredmedia")

    private suspend fun mangaList(url: HttpUrl, page: Int): MangasPage {
        val response = client.get(url)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
        val list = response.parseAs<List<MangaDto>>().map { dto ->
            SManga.create().apply {
                title = dto.title()
                thumbnail_url = dto.coverUrl()
                setUrlWithoutDomain(dto.link())
            }
        }
        return MangasPage(list, page < totalPages)
    }

    // ============================== Details ================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(doc, manga), chapterListParse(doc))
    }

    // The Ifenzi theme renders broken Author / Type rows (`foreach() over bool`),
    // but the taxonomy slugs survive on the <article> class list. Scraping the
    // public manga page also gives us Followers / Views, which the REST DTO omits.
    private fun mangaDetailsParse(doc: Document, manga: SManga): SManga {
        val article = doc.selectFirst("article")
        val articleClasses = article?.classNames().orEmpty()

        return manga.apply {
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

    private fun chapterListParse(document: Document): List<SChapter> = document.select("ul.chapterslist li").map { li ->
        SChapter.create().apply {
            val link = li.selectFirst("a.title, a[href*=/chapter/]")!!
            setUrlWithoutDomain(link.attr("abs:href"))
            name = link.ownText().ifEmpty { link.text() }
            date_upload = parseChapterDate(li.selectFirst(".entry-date")?.text())
        }
    }

    // =============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.trim('/').substringAfterLast('/')
        val url = "$baseUrl/wp-json/wp/v2/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id,content")
            .build()
        val dto = client.get(url).parseAs<List<ChapterContentDto>>().firstOrNull()
            ?: return emptyList()
        return IMG_SRC_REGEX.findAll(dto.contentHtml())
            .map { it.groupValues[1] }
            .filterNot(SKIP_ASSET_REGEX::containsMatchIn)
            .toList()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    // =============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async {
            client.get("$baseUrl/wp-json/wp/v2/Genres?per_page=100&hide_empty=true")
                .parseAs<List<TermPayloadDto>>()
        }
        val years = async {
            client.get("$baseUrl/wp-json/wp/v2/realised?per_page=100&hide_empty=true&orderby=name&order=desc")
                .parseAs<List<TermPayloadDto>>()
        }
        FilterDataDto(genres.await(), years.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val taxonomies = data?.parseAs<FilterDataDto>()
        val genres = taxonomies?.genres.orEmpty().map { TaxonomyOption(it.id, it.name) }.sortedBy { it.name }
        val years = taxonomies?.years.orEmpty().map { TaxonomyOption(it.id, it.name) }
        return FilterList(
            buildList {
                add(TypeFilter())
                add(StatusFilter())
                if (years.isNotEmpty()) add(YearFilter(years))
                add(SortFilter())
                if (genres.isNotEmpty()) {
                    add(Filter.Separator())
                    add(GenreFilterGroup(genres))
                }
            },
        )
    }

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
        return absoluteDateFormat.tryParseDate(raw, ZoneOffset.UTC)
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }

    private val absoluteDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    companion object {
        private const val PAGE_SIZE = 24

        private val IMG_SRC_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""")
        private val SKIP_ASSET_REGEX = Regex(
            """(?i)/wp-content/(?:themes|plugins)/|/(?:logo|icon|cropped|preroll|placeholder|loading|spinner|chainsaw)[^/]*\.(?:png|jpe?g|webp|gif|svg)""",
        )
        private val RELATIVE_NUMBER_REGEX = Regex("""^(\d+)""")
    }
}
