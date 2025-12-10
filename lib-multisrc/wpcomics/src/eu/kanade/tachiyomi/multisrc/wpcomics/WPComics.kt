package eu.kanade.tachiyomi.multisrc.wpcomics

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class WPComics(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    protected val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US),
    protected val gmtOffset: String? = "+0500",
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    open val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "vi", "ja"),
        classLoader = this::class.java.classLoader!!,
    )

    protected fun List<String>.doesInclude(thisWord: String): Boolean = this.any { it.contains(thisWord, ignoreCase = true) }

    // Popular
    open val popularPath = "hot"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularPath" + if (page > 1) "?page=$page" else "", headers)
    }

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = imageOrNull(element.select("div.image:first-of-type img").first()!!)
        }
    }

    override fun popularMangaNextPageSelector() = "a.next-page, a[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    protected open val searchPath = "tim-truyen"
    protected open val queryParam = "keyword"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                else -> {}
            }
        }

        url.apply {
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "0")
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.items div.item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = imageOrNull(element.select("div.image a img").first()!!)
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                val otherName = info.select("h2.other-name").text()
                description = info.select("div.detail-content p").text() +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }

    open fun String?.toStatus(): Int {
        val ongoingWords = listOf("Ongoing", "Updating", "Đang tiến hành", "Đang cập nhật", "Đang thực hiện", "Đang ra", "連載中")
        val completedWords = listOf("Complete", "Completed", "Hoàn thành", "Đã hoàn thành", "Full", "Truyện Full", "完結済み")
        val hiatusWords = listOf("Tạm Ngưng", "Tạm Hoãn")
        return when {
            this == null -> SManga.UNKNOWN
            ongoingWords.doesInclude(this) -> SManga.ONGOING
            completedWords.doesInclude(this) -> SManga.COMPLETED
            hiatusWords.doesInclude(this) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // Chapters
    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("div.col-xs-4").text().toDate()
        }
    }

    protected val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    protected open fun String?.toDate(): Long {
        this ?: return 0L

        val secondWords = listOf("second", "giây")
        val minuteWords = listOf("minute", "phút")
        val hourWords = listOf("hour", "giờ")
        val dayWords = listOf("day", "ngày")
        val monthWords = listOf("month", "tháng")
        val yearWords = listOf("year", "năm")
        val agoWords = listOf("ago", "trước")

        return try {
            if (agoWords.any { this.contains(it, ignoreCase = true) }) {
                val trimmedDate = this.substringBefore(" ago").removeSuffix("s").split(" ")
                val calendar = Calendar.getInstance()

                when {
                    yearWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                    monthWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                    dayWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                    hourWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                    minuteWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                    secondWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }
                }

                calendar.timeInMillis
            } else {
                (if (gmtOffset == null) this.substringAfterLast(" ") else "$this $gmtOffset").let {
                    // timestamp has year
                    if (Regex("""\d+/\d+/\d\d""").find(it)?.value != null) {
                        dateFormat.parse(it)?.time ?: 0L
                    } else {
                        // MangaSum - timestamp sometimes doesn't have year (current year implied)
                        dateFormat.parse("$it/$currentYear")?.time ?: 0L
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    // Pages
    open fun imageOrNull(element: Element): String? {
        // sources sometimes have an image element with an empty attr that isn't really an image
        fun Element.hasValidAttr(attr: String): Boolean {
            val regex = Regex("""https?://.*""", RegexOption.IGNORE_CASE)
            return when {
                this.attr(attr).isNullOrBlank() -> false
                this.attr("abs:$attr").matches(regex) -> true
                else -> false
            }
        }

        return when {
            element.hasValidAttr("data-original") -> element.attr("abs:data-original")
            element.hasValidAttr("data-src") -> element.attr("abs:data-src")
            element.hasValidAttr("src") -> element.attr("abs:src")
            else -> null
        }
    }

    open val pageListSelector = "div.page-chapter > img, li.blocks-gallery-item img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapNotNull { img -> imageOrNull(img) }
            .distinct()
            .mapIndexed { i, image -> Page(i, "", image) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters
    protected class StatusFilter(name: String, pairs: List<Pair<String?, String>>) : UriPartFilter(name, pairs)

    protected class GenreFilter(name: String, pairs: List<Pair<String?, String>>) : UriPartFilter(name, pairs)

    protected open fun getStatusList(): List<Pair<String?, String>> =
        listOf(
            Pair(null, intl["STATUS_ALL"]),
            Pair("1", intl["STATUS_ONGOING"]),
            Pair("2", intl["STATUS_COMPLETED"]),
        )

    protected var genreList: List<Pair<String?, String>> = emptyList()

    private val scope = CoroutineScope(Dispatchers.IO)

    protected fun launchIO(block: () -> Unit) = scope.launch { block() }

    private var fetchGenresAttempts: Int = 0

    protected fun fetchGenres() {
        if (fetchGenresAttempts < 3 && genreList.isEmpty()) {
            try {
                genreList =
                    client.newCall(genresRequest()).execute()
                        .asJsoup()
                        .let(::parseGenres)
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    protected open fun genresRequest() = GET("$baseUrl/$searchPath", headers)

    protected open val genresSelector = ".genres ul.nav li:not(.active) a"

    protected open val genresUrlDelimiter = "/"

    protected open fun parseGenres(document: Document): List<Pair<String?, String>> {
        val items = document.select(genresSelector)
        return buildList(items.size + 1) {
            add(Pair(null, intl["STATUS_ALL"]))
            items.mapTo(this) {
                Pair(
                    it.attr("href")
                        .removeSuffix("/")
                        .substringAfterLast(genresUrlDelimiter),
                    it.text(),
                )
            }
        }
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            StatusFilter(intl["STATUS"], getStatusList()),
            if (genreList.isEmpty()) {
                Filter.Header(intl["GENRES_RESET"])
            } else {
                GenreFilter(intl["GENRE"], genreList)
            },
        )
    }

    protected open class UriPartFilter(displayName: String, private val pairs: List<Pair<String?, String>>) :
        Filter.Select<String>(displayName, pairs.map { it.second }.toTypedArray()) {
        fun toUriPart() = pairs[state].first
    }
}
