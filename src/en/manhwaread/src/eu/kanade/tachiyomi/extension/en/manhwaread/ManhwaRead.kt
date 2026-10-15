package eu.kanade.tachiyomi.extension.en.manhwaread

import android.util.Base64
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ManhwaRead : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT)

    // Popular
    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortByFilter.POPULAR)

    // Latest
    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortByFilter.LATEST)

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manhwa") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null

        // Rewrite to strip suffixes after slug
        val manga = SManga.create().apply { this.url = "/manhwa/$slug/" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("")
            }
            addQueryParameter("s", query)

            filters.forEach { filter ->
                when (filter) {
                    is SortByFilter -> {
                        addQueryParameter(filter.queryName, filter.queryValue)
                        addQueryParameter("order", filter.orderValue)
                    }

                    is KeywordModeFilter -> addQueryParameter(filter.queryName, filter.queryValue)

                    is TagsSearchModeFilter -> addQueryParameter(filter.queryName, filter.queryValue)

                    is StatusFilter -> addQueryParameter(filter.queryName, filter.queryValue)

                    is ArtistsFilter -> filter.queryValues.forEach { addQueryParameter(filter.queryName, it) }

                    is AuthorsFilter -> filter.queryValues.forEach { addQueryParameter(filter.queryName, it) }

                    is PublishersFilter -> filter.queryValues.forEach { addQueryParameter(filter.queryName, it) }

                    is GenresFilter -> filter.queryValues.forEach { addQueryParameter(filter.queryName, it) }

                    is TagsFilter -> {
                        filter.includedQueryValues.forEach { addQueryParameter(filter.includedQueryName, it) }
                        filter.excludedQueryValues.forEach { addQueryParameter(filter.excludedQueryName, it) }
                    }

                    is PublishYearFilter -> addQueryParameter(filter.queryName, filter.queryValue)

                    is ChapterNumbersFilter -> addQueryParameter(filter.queryName, filter.queryValue)

                    else -> {}
                }
            }
        }

        val document = client.get(urlBuilder.build()).asJsoup()
        val mangas = document
            .select(".main-container .manga-item")
            .map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst(".wp-pagenavi a.last") != null
        return MangasPage(mangas, hasNextPage = hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("a.manga-item__link")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = element.selectFirst(".manga-item__img img")?.absUrl("src")
    }

    // Details
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = manga.apply {
            title = document.selectFirst("#mangaSummary .manga-titles h1")!!.text()
            artist = document.select("#mangaSummary .text-primary:contains(Artist:) + .flex a span:first-child").joinToString { it.text() }
            author = document.select("#mangaSummary .text-primary:contains(Author:) + .flex a span:first-child").joinToString { it.text() }

            description = buildString {
                val metrics = document.selectFirst("div:has(> #mangaRating)")
                metrics?.selectFirst("#mangaRating .rating__current")?.text()?.also { rating ->
                    metrics.selectFirst("#mangaRating .rating__count")?.text()?.also { ratingCount ->
                        val ratingString = getRatingString(rating, ratingCount.toIntOrNull() ?: 0)
                        if (ratingString.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Rating: ", ratingString)
                        }
                    }
                }
                metrics?.selectFirst(".fa-eye + span")?.text()?.also { views ->
                    if (isNotEmpty()) append("\n")
                    append("Views: ", views)
                }
                metrics?.selectFirst(".fa-comments + span")?.text()?.also { comments ->
                    if (isNotEmpty()) append("\n")
                    append("Comments: ", comments)
                }
                metrics?.selectFirst(".w-5.h-5 + span")?.text()?.also { bookmarks ->
                    if (isNotEmpty()) append("\n")
                    append("Bookmarks: ", bookmarks)
                }

                document.select("#mangaSummary .text-primary:contains(Publisher:) + .flex a span:first-child")
                    .takeIf { it.isNotEmpty() }
                    ?.also { publisher ->
                        if (isNotEmpty()) append("\n")
                        val publishers = publisher.joinToString { it.text() }
                        val publisherLabel = if (publisher.count() > 1) "Publishers" else "Publisher"
                        append(publisherLabel, ": ", publishers)
                    }

                document.selectFirst("#mangaDesc > .manga-desc__content")?.text()?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(it)
                }

                document.selectFirst("#mangaSummary .manga-titles h2")
                    ?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.split("|")
                    ?.joinToString("\n") { "- " + it.trim() }
                    ?.also { altTitles ->
                        if (isNotEmpty()) append("\n\n")
                        appendLine("Alternative titles:")
                        append(altTitles)
                    }
            }

            val siteGenres = document.select("#mangaSummary .manga-genres a").map { it.text() }
            val siteTags = document.select("#mangaSummary .text-primary:contains(Tags:) + .flex a span:first-child").map { it.text() }
            genre = (siteGenres + siteTags).joinToString()

            val statusText = document.selectFirst("#mangaSummary .manga-status")?.attr("data-status")
            status = when (statusText) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "canceled" -> SManga.CANCELLED
                "on-hold" -> SManga.ON_HIATUS
                "incomplete" -> SManga.PUBLISHING_FINISHED
                else -> SManga.UNKNOWN
            }

            thumbnail_url = document.selectFirst("head meta[property=og:image]")?.absUrl("content")
        }

        val chapterList = document
            .select("#chaptersList > a.chapter-item")
            .map(::chapterFromElement)
            .asReversed()

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst("span.chapter-item__name")!!.text()
        date_upload = dateFormat.tryParseDate(element.selectFirst("span.chapter-item__date")?.text())
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterDataString = client.get(getChapterUrl(chapter)).use { it.body.string() }
            .let { PATTERN_CHAPTER_DATA.find(it)?.groupValues?.get(1) }
            ?: throw Exception("Chapter data not found")

        val chapterData = chapterDataString.parseAs<ChapterData>()
        val chapterDataData = String(Base64.decode(chapterData.data, Base64.DEFAULT))
        val pages = chapterDataData.parseAs<List<ChapterDataData>>()

        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = "${chapterData.base}/${page.src}")
        }
    }

    // Other
    override fun getFilterList(data: JsonElement?) = getFilters()

    private fun getRatingString(rate: String, rateCount: Int): String {
        val ratingValue = rate.toDoubleOrNull() ?: 0.0
        val ratingStar = when {
            ratingValue >= 4.75 -> "★★★★★"
            ratingValue >= 4.25 -> "★★★★✬"
            ratingValue >= 3.75 -> "★★★★☆"
            ratingValue >= 3.25 -> "★★★✬☆"
            ratingValue >= 2.75 -> "★★★☆☆"
            ratingValue >= 2.25 -> "★★✬☆☆"
            ratingValue >= 1.75 -> "★★☆☆☆"
            ratingValue >= 1.25 -> "★✬☆☆☆"
            ratingValue >= 0.75 -> "★☆☆☆☆"
            ratingValue >= 0.25 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }
        return if (ratingValue > 0.0) {
            buildString {
                append(ratingStar, " ", rate)
                if (rateCount > 0) {
                    append(" (", rateCount, ")")
                }
            }
        } else {
            ""
        }
    }

    companion object {
        val PATTERN_CHAPTER_DATA = """var\s+chapterData\s*=\s*(\{.*\})""".toRegex()
    }
}
