package eu.kanade.tachiyomi.extension.en.kaliscansorg

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class KaliScansOrg : KeiSource() {

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    // Image host 403s requests with a Referer
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Referer").removeAll("Origin")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listingUrl("popular", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listingUrl("latest", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.map { it.value }.orEmpty()
        val status = filters.firstInstance<StatusFilter>().selected
        val sort = filters.firstInstance<SortFilter>().selected

        // The site's search returns nothing without a query or a filter; its own listings cover the sorts
        if (query.isBlank() && genres.isEmpty() && status == "all") {
            val path = when (sort) {
                "updated_at" -> "latest"
                "name" -> "az-list"
                else -> "popular"
            }
            return getMangaList(listingUrl(path, page))
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.trim())
            genres.forEach { addQueryParameter("genre[]", it) }
            addQueryParameter("status", status)
            addQueryParameter("sort", sort)
            addQueryParameter("page", page.toString())
        }.build()
        return getMangaList(url)
    }

    private fun listingUrl(path: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .addQueryParameter("page", page.toString())
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.book-item").map { element ->
            val link = element.selectFirst("h3 a[href]")!!
            SManga.create().apply {
                this.url = link.absUrl("href").toHttpUrl().pathSegments[1]
                title = link.text()
                thumbnail_url = element.selectFirst(".thumb img")?.attrOrNull("abs:data-src")
            }
        }
        val hasNextPage = document.selectFirst(".paginator a.active ~ a.btn") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        // Site dates are relative ("3 weeks ago"), so keep the date a chapter was first stored with
        val knownDates = chapters.filter { it.date_upload > 0 }.associate { it.url to it.date_upload }
        val chapterList = parseChapterList(document, manga.url).onEach { chapter ->
            knownDates[chapter.url]?.let { chapter.date_upload = it }
        }
        return SMangaUpdate(parseMangaDetails(document, manga), chapterList)
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        val info = document.selectFirst(".book-info")!!
        title = info.selectFirst(".name h1")!!.text()
        thumbnail_url = info.selectFirst("#cover img")?.attrOrNull("abs:data-src")
        author = info.selectFirst(".meta p:has(strong:contains(Authors)) span")?.textOrNull()
        genre = info.select(".meta p:has(strong:contains(Genres)) a")
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.text().trimEnd(',', ' ') }
        status = when (info.selectFirst(".meta p:has(strong:contains(Status)) span")?.text()?.lowercase()) {
            "ongoing", "publishing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            val facts = buildList {
                document.select("script[type=application/ld+json]")
                    .map { it.data().parseAs<LinkedData>() }
                    .firstOrNull { it.type == "AggregateRating" }
                    ?.ratingValue
                    ?.let { add("**Rating:** $it") }
                info.selectFirst(".meta p:has(strong:contains(Views)) span")?.textOrNull()?.let { add("**Views:** $it") }
            }
            if (facts.isNotEmpty()) append(facts.joinToString(" · "), "\n\n")
            document.selectFirst(".summary .content")?.let { content ->
                content.select("br").forEach { it.replaceWith(TextNode("\n")) }
                val synopsis = content.wholeText().lines().joinToString("\n") { it.trim() }
                    .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
                    .trim()
                if (synopsis.isNotEmpty()) append(synopsis, "\n\n")
            }
            info.selectFirst(".name h2")?.text()?.split(";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { append("**Alternative titles:** ", it.joinToString(" · ")) }
        }.trim().ifEmpty { null }
    }

    private fun parseChapterList(document: Document, mangaSlug: String): List<SChapter> {
        val chapters = document.select("#chapter-list li a").map { element ->
            SChapter.create().apply {
                url = "$mangaSlug/${element.absUrl("href").toHttpUrl().pathSegments[2]}"
                name = element.selectFirst(".chapter-title")!!.text()
                date_upload = parseRelativeDate(element.selectFirst(".chapter-update")?.text())
            }
        }
        // The site repeats some rows; same order as its reader, by the number in the url ("41-7" = 41.7)
        return chapters.distinctBy { it.url }
            .sortedByDescending { it.url.substringAfterLast('/').replace('-', '.').toFloatOrNull() }
    }

    private fun parseRelativeDate(text: String?): Long {
        val (amount, unit) = text?.let(RELATIVE_DATE_REGEX::find)?.destructured ?: return 0L
        val chronoUnit = when (unit) {
            "second" -> ChronoUnit.SECONDS
            "minute" -> ChronoUnit.MINUTES
            "hour" -> ChronoUnit.HOURS
            "day" -> ChronoUnit.DAYS
            "week" -> ChronoUnit.WEEKS
            "month" -> ChronoUnit.MONTHS
            else -> ChronoUnit.YEARS
        }
        return ZonedDateTime.now().minus(amount.toLong(), chronoUnit).toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapter-images img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search").asJsoup()
        return document.select("#search-form-large input[name='genre[]']").map { input ->
            Genre(input.closest("label")!!.selectFirst(".radio__label")!!.text(), input.attr("value"))
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>().orEmpty()
        return FilterList(
            buildList {
                add(StatusFilter())
                add(SortFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    companion object {
        private val RELATIVE_DATE_REGEX = """(\d+) (second|minute|hour|day|week|month|year)s? ago""".toRegex()
        private val MULTIPLE_NEWLINES_REGEX = """\n{3,}""".toRegex()
    }
}
