package eu.kanade.tachiyomi.multisrc.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US),
    private val gmtOffset: String? = "+0500",
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0")
        .add("Referer", baseUrl)

    private fun List<String>.doesInclude(thisWord: String): Boolean = this.any { it.contains(thisWord, ignoreCase = true) }

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }
        return if (filterList.isEmpty()) {
            GET("$baseUrl/?s=$query&post_type=comics&page=$page")
        } else {
            val url = "$baseUrl/$searchPath".toHttpUrlOrNull()!!.newBuilder()

            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                    is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                    else -> {}
                }
            }

            url.apply {
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
                addQueryParameter("sort", "0")
            }

            GET(url.toString().replace("/tim-truyen?status=2&", "/truyen-full?"), headers)
        }
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
                description = info.select("div.detail-content p").text()
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }

    open fun String?.toStatus(): Int {
        val ongoingWords = listOf("Ongoing", "Updating", "Đang tiến hành")
        val completedWords = listOf("Complete", "Hoàn thành")
        return when {
            this == null -> SManga.UNKNOWN
            ongoingWords.doesInclude(this) -> SManga.ONGOING
            completedWords.doesInclude(this) -> SManga.COMPLETED
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

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    protected fun String?.toDate(): Long {
        this ?: return 0

        val secondWords = listOf("second", "giây")
        val minuteWords = listOf("minute", "phút")
        val hourWords = listOf("hour", "giờ")
        val dayWords = listOf("day", "ngày")
        val agoWords = listOf("ago", "trước")

        return try {
            if (agoWords.any { this.contains(it, ignoreCase = true) }) {
                val trimmedDate = this.substringBefore(" ago").removeSuffix("s").split(" ")
                val calendar = Calendar.getInstance()

                when {
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
                        dateFormat.parse(it)?.time ?: 0
                    } else {
                        // MangaSum - timestamp sometimes doesn't have year (current year implied)
                        dateFormat.parse("$it/$currentYear")?.time ?: 0
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    // sources sometimes have an image element with an empty attr that isn't really an image
    open fun imageOrNull(element: Element): String? {
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

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    protected class StatusFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Status", vals)
    protected class GenreFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Genre", vals)

    protected open fun getStatusList(): Array<Pair<String?, String>> = arrayOf(
        Pair(null, "Tất cả"),
        Pair("1", "Đang tiến hành"),
        Pair("2", "Đã hoàn thành"),
        Pair("3", "Tạm ngừng"),
    )
    protected open fun getGenreList(): Array<Pair<String?, String>> = arrayOf(
        null to "Tất cả",
        "action" to "Action",
        "adult" to "Adult",
        "adventure" to "Adventure",
        "anime" to "Anime",
        "chuyen-sinh" to "Chuyển Sinh",
        "comedy" to "Comedy",
        "comic" to "Comic",
        "cooking" to "Cooking",
        "co-dai" to "Cổ Đại",
        "doujinshi" to "Doujinshi",
        "drama" to "Drama",
        "dam-my" to "Đam Mỹ",
        "ecchi" to "Ecchi",
        "fantasy" to "Fantasy",
        "gender-bender" to "Gender Bender",
        "harem" to "Harem",
        "historical" to "Historical",
        "horror" to "Horror",
        "josei" to "Josei",
        "live-action" to "Live action",
        "manga" to "Manga",
        "manhua" to "Manhua",
        "manhwa" to "Manhwa",
        "martial-arts" to "Martial Arts",
        "mature" to "Mature",
        "mecha" to "Mecha",
        "mystery" to "Mystery",
        "ngon-tinh" to "Ngôn Tình",
        "one-shot" to "One shot",
        "psychological" to "Psychological",
        "romance" to "Romance",
        "school-life" to "School Life",
        "sci-fi" to "Sci-fi",
        "seinen" to "Seinen",
        "shoujo" to "Shoujo",
        "shoujo-ai" to "Shoujo Ai",
        "shounen" to "Shounen",
        "shounen-ai" to "Shounen Ai",
        "slice-of-life" to "Slice of Life",
        "smut" to "Smut",
        "soft-yaoi" to "Soft Yaoi",
        "soft-yuri" to "Soft Yuri",
        "sports" to "Sports",
        "supernatural" to "Supernatural",
        "thieu-nhi" to "Thiếu Nhi",
        "tragedy" to "Tragedy",
        "trinh-tham" to "Trinh Thám",
        "truyen-scan" to "Truyện scan",
        "truyen-mau" to "Truyện Màu",
        "webtoon" to "Webtoon",
        "xuyen-khong" to "Xuyên Không",
    )

    protected open class UriPartFilter(displayName: String, val vals: Array<Pair<String?, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }
}
