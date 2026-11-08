package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class VyvyManga : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
    private val relativeDateRegex = Regex("""(\d+)""")
    private val ratingRegex = Regex("""([\d.]+)/10 \(([\d,]+) votes\)""")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (page != 1) addQueryParameter("page", page.toString())
        }.build()
        return parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".comic-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".comic-title")!!.text()
                thumbnail_url = element.selectFirst(".comic-image img.image.lozad")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "updated_at")
            if (page != 1) addQueryParameter("page", page.toString())
        }.build()
        return parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SearchType -> url.addQueryParameter("search_po", filter.selected)
                is SearchDescription -> if (filter.state) url.addQueryParameter("check_search_desc", "1")
                is AuthorSearchType -> url.addQueryParameter("author_po", filter.selected)
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is StatusFilter -> url.addQueryParameter("completed", filter.selected)
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is SortType -> url.addQueryParameter("sort_type", filter.selected)
                is GenreFilter -> {
                    filter.state.forEach {
                        if (!it.isIgnored()) url.addQueryParameter(if (it.isIncluded()) "genre[]" else "exclude_genre[]", it.id)
                    }
                }
                else -> {}
            }
        }

        return parseMangasPage(client.get(url.build(), Fetch.DOCUMENT))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // Mirrors differ only in their top-level domain
        if (url.host.removePrefix("www.").substringBeforeLast('.') != baseUrl.toHttpUrl().host.substringBeforeLast('.')) return null
        if (url.pathSegments.size < 2 || url.pathSegments[0] != "manga") return null

        val manga = SManga.create().apply { this.url = "/manga/${url.pathSegments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = manga.url
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val rating = document.selectFirst("p:has(.pre-title:contains(Rating))")?.text()
            ?.let { ratingRegex.find(it) }
            ?.takeIf { it.groupValues[2] != "0" }

        manga.apply {
            title = document.selectFirst("h1")!!.text()
            artist = document.select(".pre-title:contains(Artist) ~ a").eachText().joinToString().ifEmpty { null }
            author = document.select(".pre-title:contains(Author) ~ a").eachText().joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .rating(rating?.groupValues?.get(1)?.toDoubleOrNull(), votes = rating?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull())
                .description(document.selectFirst(".summary > .content")?.wholeText())
                .altTitles(document.selectFirst("h1.title + p:not(.text-info)")?.text()?.split(',', ';').orEmpty().filter { it.trim() != title })
                .build()
            genre = document.select(".pre-title:contains(Genres) ~ a").eachText().joinToString().ifEmpty { null }
            status = when (document.selectFirst(".pre-title:contains(Status) ~ span:not(.space)")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".img-manga img")?.absUrl("src")
        }

        // Stored chapters by name: links expire and recent rows show relative dates
        val storedChapters = chapters.associateBy { it.name }
        val updatedChapters = document.select(".list-group > a").map { element ->
            SChapter.create().apply {
                // "Uploaded by <span>name</span> date" on user uploads
                val info = element.selectFirst("> p")
                val dateText = info?.textNodes()?.lastOrNull()?.text()?.trim()
                val chapterDate = parseChapterDate(dateText)
                val title = element.selectFirst("span")!!.text()
                val stored = storedChapters[title]
                url = stored?.url ?: "$chapterDate:$title".md5().toHexString().takeLast(10)
                name = title
                element.id().removePrefix("chapter-").toFloatOrNull()?.let { chapter_number = it }
                scanlator = info?.selectFirst("span.font-weight-bold")?.textOrNull()
                date_upload = if (stored != null && dateText?.endsWith("ago") == true) stored.date_upload else chapterDate
                memo = buildJsonObject {
                    put("mangaUrl", mangaUrl)
                    put("chapterUrl", element.absUrl("href"))
                }
            }
        }

        return SMangaUpdate(manga, updatedChapters)
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["chapterUrl"]?.string
        ?: baseUrl.toHttpUrl().resolve(chapter.url)!!.toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaUrl = chapter.memo["mangaUrl"]?.string ?: error("Refresh to reload chapters")

        // Chapter links expire after a few hours
        val url = client.get(baseUrl.toHttpUrl().resolve(mangaUrl)!!, Fetch.DOCUMENT).asJsoup()
            .select(".list-group > a")
            .firstOrNull { it.selectFirst("span")!!.text() == chapter.name }
            ?.absUrl("href")
            ?: error("Refresh to reload chapters")

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select("img.d-block").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    private fun parseChapterDate(date: String?): Long {
        date ?: return 0L

        return when {
            date.endsWith("ago") -> parseRelativeDate(date)
            else -> dateFormat.tryParseDate(date, ZoneOffset.UTC)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = relativeDateRegex.find(date)?.value?.toIntOrNull() ?: return 0L
        val cal = Clock.System.now()

        return when {
            date.contains("day") -> cal.minus(number.days).toEpochMilliseconds()
            date.contains("hour") -> cal.minus(number.hours).toEpochMilliseconds()
            date.contains("minute") -> cal.minus(number.minutes).toEpochMilliseconds()
            date.contains("second") -> cal.minus(number.seconds).toEpochMilliseconds()
            else -> 0L
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search", Fetch.DOCUMENT).asJsoup()

        return document.select(".check-genre div div:has(.checkbox-genre)").map {
            GenreData(
                it.selectFirst("label")!!.text(),
                it.selectFirst(".checkbox-genre")!!.attr("data-value"),
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreData>>()?.map { it.toGenre() } ?: emptyList()

        val filterList = buildList {
            add(SearchType())
            add(SearchDescription())
            add(AuthorSearchType())
            add(AuthorFilter())
            add(StatusFilter())
            add(SortFilter())
            add(SortType())
            if (genres.isNotEmpty()) {
                add(GenreFilter(genres))
            }
        }

        return FilterList(filterList)
    }
}
