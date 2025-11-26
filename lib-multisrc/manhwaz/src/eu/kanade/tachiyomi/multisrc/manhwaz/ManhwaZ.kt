package eu.kanade.tachiyomi.multisrc.manhwaz

import android.util.Log
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

abstract class ManhwaZ(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    private val mangaDetailsAuthorHeading: String = "author(s)",
    private val mangaDetailsStatusHeading: String = "status",
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    protected val intl = Intl(
        lang,
        setOf("en", "vi"),
        "en",
        this::class.java.classLoader!!,
    )
    protected open val searchPath = "search"

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "#slide-top > .item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".info-item a")!!.let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.selectFirst(".img-item img")?.imgAttr()
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = ".page-item-detail"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".item-summary a")!!.let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.selectFirst(".item-thumb img")?.imgAttr()
    }

    override fun latestUpdatesNextPageSelector(): String? = "ul.pager a[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(searchPath)
                addQueryParameter("s", query)
                addQueryParameter("page", page.toString())
            }.build()

            return GET(url, headers)
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val filterList = filters.ifEmpty { getFilterList() }
            val genreFilter = filterList.find { it is GenreFilter } as? GenreFilter
            val orderByFilter = filterList.find { it is OrderByFilter } as? OrderByFilter
            val genreId = genreFilter?.options?.get(genreFilter.state)?.id

            if (genreFilter != null && genreFilter.state != 0) {
                addPathSegments(genreId!!)
            }

            // Can't sort in "All" or "Completed"
            if (orderByFilter != null && genreId?.startsWith("genre/") == true) {
                addQueryParameter(
                    "m_orderby",
                    orderByFilter.options[orderByFilter.state].id,
                )
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String? = latestUpdatesNextPageSelector()

    private val ongoingStatusList = listOf("ongoing", "đang ra")
    private val completedStatusList = listOf("completed", "hoàn thành", "Truyện Full")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val statusText = document.selectFirst("div.summary-heading:contains($mangaDetailsStatusHeading) + div.summary-content")
            ?.text()
            ?: ""

        title = document.selectFirst("div.post-title h1")!!.text()
        author = document.selectFirst("div.summary-heading:contains($mangaDetailsAuthorHeading) + div.summary-content")?.text()
        description = document.selectFirst("div.summary__content")?.text()
        genre = document.select("div.genres-content a[rel=tag]").joinToString { it.text() }
        status = when {
            ongoingStatusList.any { statusText.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completedStatusList.any { statusText.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.summary_image img")?.imgAttr()
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            name = it.text()
        }

        element.selectFirst("span.chapter-release-date")?.text()?.let {
            date_upload = parseRelativeDate(it)
        }
    }

    override fun pageListParse(document: Document) =
        document.select("div.page-break img").mapIndexed { i, it ->
            Page(i, imageUrl = it.imgAttr())
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        fetchGenreList()

        val filters = buildList {
            add(Filter.Header(intl["filter_ignored_warning"]))
            add(Filter.Header(intl.format("cannot_use_order_by_warning", intl["genre_all"], intl["genre_completed"])))

            if (fetchGenreStatus == FetchGenreStatus.NOT_FETCHED && fetchGenreAttempts >= 3) {
                add(Filter.Header(intl["genre_fetch_failed"]))
            } else if (fetchGenreStatus != FetchGenreStatus.FETCHED) {
                add(Filter.Header(intl["genre_missing_warning"]))
            }

            add(Filter.Separator())
            if (genres.isNotEmpty()) {
                add(GenreFilter(intl, genres))
            }
            add(OrderByFilter(intl))
        }

        return FilterList(filters)
    }

    private class GenreFilter(
        intl: Intl,
        genres: List<SelectOption>,
    ) : SelectFilter(intl["genre_filter_title"], genres)

    private class OrderByFilter(intl: Intl) : SelectFilter(
        intl["order_by_filter_title"],
        listOf(
            SelectOption(intl["order_by_latest"], "latest"),
            SelectOption(intl["order_by_rating"], "rating"),
            SelectOption(intl["order_by_most_views"], "views"),
            SelectOption(intl["order_by_new"], "new"),
        ),
    )

    private var genres = emptyList<SelectOption>()
    private var fetchGenreStatus = FetchGenreStatus.NOT_FETCHED
    private var fetchGenreAttempts = 0

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun fetchGenreList() {
        if (fetchGenreStatus != FetchGenreStatus.NOT_FETCHED || fetchGenreAttempts >= 3) {
            return
        }

        fetchGenreStatus = FetchGenreStatus.FETCHING
        fetchGenreAttempts++

        scope.launch {
            try {
                val document = client.newCall(GET("$baseUrl/genre")).await().asJsoup()

                genres = buildList {
                    add(SelectOption(intl["genre_all"], ""))
                    add(SelectOption(intl["genre_completed"], "completed"))
                    document.select("ul.page-genres li a").forEach {
                        val path = it.absUrl("href").toHttpUrl().encodedPath.removePrefix("/")

                        add(SelectOption(it.ownText(), path))
                    }
                }
                fetchGenreStatus = FetchGenreStatus.FETCHED
            } catch (e: Exception) {
                Log.e("ManhwaZ/$name", "Error fetching genres", e)
                fetchGenreStatus = FetchGenreStatus.NOT_FETCHED
            }
        }
    }

    private enum class FetchGenreStatus { NOT_FETCHED, FETCHED, FETCHING }

    private class SelectOption(val name: String, val id: String)

    private open class SelectFilter(
        name: String,
        val options: List<SelectOption>,
    ) : Filter.Select<String>(name, options.map { it.name }.toTypedArray())

    private val secondsUnit = listOf("second", "seconds", "giây")
    private val minutesUnit = listOf("minute", "minutes", "phút")
    private val hourUnit = listOf("hour", "hours", "giờ")
    private val dayUnit = listOf("day", "days", "ngày")
    private val weekUnit = listOf("week", "weeks", "tuần")
    private val monthUnit = listOf("month", "months", "tháng")
    private val yearUnit = listOf("year", "years", "năm")

    private fun parseRelativeDate(date: String): Long {
        val (valueString, unit) = date.substringBeforeLast(" ").split(" ", limit = 2)
        val value = valueString.toInt()

        val calendar = Calendar.getInstance().apply {
            val field = when {
                secondsUnit.contains(unit) -> Calendar.SECOND
                minutesUnit.contains(unit) -> Calendar.MINUTE
                hourUnit.contains(unit) -> Calendar.HOUR_OF_DAY
                dayUnit.contains(unit) -> Calendar.DAY_OF_MONTH
                weekUnit.contains(unit) -> Calendar.WEEK_OF_MONTH
                monthUnit.contains(unit) -> Calendar.MONTH
                yearUnit.contains(unit) -> Calendar.YEAR
                else -> return 0L
            }

            add(field, -value)
        }

        return calendar.timeInMillis
    }

    protected fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }
}
