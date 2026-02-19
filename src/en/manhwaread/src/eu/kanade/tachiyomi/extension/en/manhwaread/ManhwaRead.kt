package eu.kanade.tachiyomi.extension.en.manhwaread

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.getValue

class ManhwaRead :
    HttpSource(),
    ConfigurableSource {

    override val name = "ManhwaRead"

    private val mirrors = arrayOf("https://manhwaread.com", "https://manhwaread.org")

    override val baseUrl: String
        get() = when {
            System.getenv("CI") == "true" -> mirrors.joinToString("#, ")
            else -> mirrors[preferences.getString(MIRROR_PREF_KEY, "0")!!.toInt().coerceIn(mirrors.indices)]
        }

    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    // Popular
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortByFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortByFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val slug = query.toHttpUrlOrNull()
                ?.pathSegments
                ?.getOrNull(1)
                ?: throw Exception("Invalid URL")

            // Rewrite to strip suffixes after slug
            val newUrl = "$baseUrl/manhwa/$slug/"
            return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(newUrl) })
                .map { manga -> MangasPage(listOf(manga), hasNextPage = false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
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
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document
            .select("#chaptersList > a.chapter-item")
            .map(::chapterFromElement)
            .asReversed()
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst("span.chapter-item__name")!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst("span.chapter-item__date")?.text())
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val chapterDataString = response.body.string()
            .let { PATTERN_CHAPTER_DATA.find(it)?.groupValues?.get(1) }
            ?: throw Exception("Chapter data not found")

        val chapterData = chapterDataString.parseAs<ChapterData>()
        val chapterDataData = String(Base64.decode(chapterData.data, Base64.DEFAULT))
        val pages = chapterDataData.parseAs<List<ChapterDataData>>()

        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = "${chapterData.base}/${page.src}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Other
    override fun getFilterList() = getFilters()

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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Mirror URL"
            entries = mirrors
            entryValues = Array(mirrors.size, Int::toString)
            setDefaultValue("0")
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        const val MIRROR_PREF_KEY = "pref_mirror"
        val PATTERN_CHAPTER_DATA = """var\s+chapterData\s*=\s*(\{.*\})""".toRegex()
    }
}
