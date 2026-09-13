package eu.kanade.tachiyomi.multisrc.manhwaz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

abstract class ManhwaZ : KeiSource() {

    protected open val mangaDetailsAuthorHeading: String = "author(s)"

    protected open val mangaDetailsStatusHeading: String = "status"

    protected val intl = Intl(
        lang,
        setOf("en", "vi"),
        "en",
        this::class.java.classLoader!!,
    )
    protected open val searchPath = "search"

    protected open val zoneId: ZoneId = ZoneId.systemDefault()

    // ============================== Common ======================================

    protected open fun parseMangaPage(
        response: Response,
        selector: String,
        fromElement: (Element) -> SManga,
        hasNextPage: Boolean? = null,
    ): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(selector).map(fromElement)
        val hasNext = hasNextPage ?: latestUpdatesNextPageSelector()?.let { sel ->
            document.selectFirst(sel) != null
        } ?: false
        return MangasPage(mangas, hasNext)
    }

    // ============================== Popular ======================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get(baseUrl)
        return parseMangaPage(response, popularMangaSelector(), ::popularMangaFromElement, hasNextPage = false)
    }

    protected open fun popularMangaSelector() = "#slide-top > .item"

    protected open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".info-item a")!!.also { it: Element ->
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.selectFirst(".img-item img")?.imgAttr()
    }

    // ============================== Latest ======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/?page=$page")
        return parseMangaPage(response, latestUpdatesSelector(), ::latestUpdatesFromElement)
    }

    protected open fun latestUpdatesSelector() = ".page-item-detail"

    protected open fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".item-summary a")!!.also { it: Element ->
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.selectFirst(".item-thumb img")?.imgAttr()
    }

    protected open fun latestUpdatesNextPageSelector(): String? = "ul.pager a[rel=next]"

    // ============================== Search ======================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(searchPath)
                addQueryParameter("s", query)
                addQueryParameter("page", page.toString())
            }.build()
        } else {
            baseUrl.toHttpUrl().newBuilder().apply {
                val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
                val orderByFilter = filters.firstInstanceOrNull<OrderByFilter>()
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
        }

        val response = client.get(url)
        return parseMangaPage(response, searchMangaSelector(), ::searchMangaFromElement)
    }

    protected open fun searchMangaSelector() = latestUpdatesSelector()

    protected open fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    // ============================== Details ======================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val document = response.asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    protected open fun parseMangaDetails(document: Document) = SManga.create().apply {
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

    private val ongoingStatusList = listOf("ongoing", "đang ra")
    private val completedStatusList = listOf("completed", "hoàn thành", "Truyện Full")

    // ============================== Chapters ======================================

    protected open fun parseChapterList(document: Document): List<SChapter> = document.select(chapterListSelector()).map { element ->
        SChapter.create().apply {
            element.selectFirst("a")!!.also { it: Element ->
                setUrlWithoutDomain(it.attr("href"))
                name = it.text()
            }

            element.selectFirst("span.chapter-release-date")?.text()?.let { date: String ->
                date_upload = parseRelativeDate(date)
            }
        }
    }

    protected open fun chapterListSelector() = "li.wp-manga-chapter"

    // ============================== Pages ======================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = parsePageList(client.get(getChapterUrl(chapter)).asJsoup())

    protected open fun pageListSelector(): String = "div.page-break img"

    protected open fun parsePageList(document: Document): List<Page> = document.select(pageListSelector()).mapIndexed { i, element ->
        Page(i, imageUrl = element.imgAttr())
    }

    // ============================== Filters ======================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/genre").asJsoup()
        val genres = document.select(genreListSelector()).map {
            SelectOption(
                it.ownText(),
                it.absUrl("href").toHttpUrl().encodedPath.removePrefix("/"),
            )
        }
        return genres.toJsonElement()
    }

    protected open fun genreListSelector() = "ul.page-genres li a"

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<SelectOption>>()

        val filters = buildList {
            add(Filter.Header(intl["filter_ignored_warning"]))
            add(Filter.Header(intl.format("cannot_use_order_by_warning", intl["genre_all"], intl["genre_completed"])))

            add(Filter.Separator())

            val genreOptions = buildList {
                add(SelectOption(intl["genre_all"], ""))
                add(SelectOption(intl["genre_completed"], "completed"))
                if (genres != null) {
                    addAll(genres)
                }
            }

            add(GenreFilter(intl, genreOptions))
            add(OrderByFilter(intl))
        }

        return FilterList(filters)
    }

    private class GenreFilter(
        intl: Intl,
        genres: List<SelectOption>,
    ) : SelectFilter(intl["genre_filter_title"], genres)

    private class OrderByFilter(intl: Intl) :
        SelectFilter(
            intl["order_by_filter_title"],
            listOf(
                SelectOption(intl["order_by_latest"], "latest"),
                SelectOption(intl["order_by_rating"], "rating"),
                SelectOption(intl["order_by_most_views"], "views"),
                SelectOption(intl["order_by_new"], "new"),
            ),
        )

    @Serializable
    protected open class SelectOption(val name: String, val id: String)

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
        val value = valueString.toLong()

        val amount = when {
            secondsUnit.contains(unit) -> ChronoUnit.SECONDS
            minutesUnit.contains(unit) -> ChronoUnit.MINUTES
            hourUnit.contains(unit) -> ChronoUnit.HOURS
            dayUnit.contains(unit) -> ChronoUnit.DAYS
            weekUnit.contains(unit) -> ChronoUnit.WEEKS
            monthUnit.contains(unit) -> ChronoUnit.MONTHS
            yearUnit.contains(unit) -> ChronoUnit.YEARS
            else -> return 0L
        }

        return ZonedDateTime.now(zoneId).minus(value, amount).toInstant().toEpochMilli()
    }

    protected fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }
}
