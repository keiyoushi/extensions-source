package eu.kanade.tachiyomi.multisrc.wpcomics

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class WPComics : KeiSource() {

    protected open val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy Z", Locale.US)

    protected open val gmtOffset: String? = "+0500"

    protected open val dateZone: ZoneId = ZoneId.systemDefault()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .removeAll("Origin")

    open val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "vi", "ja"),
        classLoader = this::class.java.classLoader!!,
    )

    protected fun List<String>.doesInclude(thisWord: String): Boolean = this.any { it.contains(thisWord, ignoreCase = true) }

    // ============================== Common ======================================

    protected fun parseMangaPage(response: Response, selector: String, fromElement: (Element) -> SManga): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(selector).map(fromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ======================================

    open val popularPath = "hot"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/$popularPath" + if (page > 1) "?page=$page" else ""
        return parseMangaPage(client.get(url), popularMangaSelector(), ::popularMangaFromElement)
    }

    protected open fun popularMangaSelector() = "div.items div.item"

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = imageOrNull(element.select("div.image:first-of-type img").first()!!)
    }

    protected open fun popularMangaNextPageSelector() = "a.next-page, a[rel=next]"

    // ============================== Latest ======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl + if (page > 1) "?page=$page" else ""
        return parseMangaPage(client.get(url), latestUpdatesSelector(), ::latestUpdatesFromElement)
    }

    protected open fun latestUpdatesSelector() = popularMangaSelector()

    protected open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // ============================== Search ======================================

    protected open val searchPath = "tim-truyen"
    protected open val queryParam = "keyword"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addPathSegment(it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    else -> {}
                }
            }
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "0")
        }.build()

        return parseMangaPage(client.get(url), searchMangaSelector(), ::searchMangaFromElement)
    }

    protected open fun searchMangaSelector() = "div.items div.item"

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = imageOrNull(element.select("div.image a img").first()!!)
    }

    // ============================== Details ======================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = mangaUpdateParse(client.get(getMangaUrl(manga)), manga, chapters)

    protected open suspend fun mangaUpdateParse(response: Response, manga: SManga, chapters: List<SChapter>): SMangaUpdate {
        val document = response.asJsoup()
        val updatedManga = mangaDetailsParse(document)
        val updatedChapters = document.select(chapterListSelector()).map(::chapterFromElement)

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.selectFirst("article#item-detail")?.let { info ->
            author = info.select("li.author p.col-xs-8").text()
            status = info.select("li.status p.col-xs-8").text().toStatus()
            genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
            thumbnail_url = imageOrNull(info.selectFirst("div.col-image img")!!)
            val otherName = info.select("h2.other-name").text()
            description = info.select("div.detail-content p").joinToString("\n") { it.wholeText().trim() } +
                if (otherName.isNotBlank()) "\n\n${intl["OTHER_NAME"]}: $otherName" else ""
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

    // ============================== Chapters ======================================

    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.select("a").let {
            name = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        date_upload = element.select("div.col-xs-4").text().toDate()
    }

    protected open fun chapterListSelector() = "div.list-chapter li.row:not(.heading)"

    protected open fun String?.toDate(): Long {
        this ?: return 0L

        val secondWords = listOf("second", "giây")
        val minuteWords = listOf("minute", "phút", "分")
        val hourWords = listOf("hour", "giờ", "時間")
        val dayWords = listOf("day", "ngày", "日")
        val weekWords = listOf("week", "tuần", "週間")
        val monthWords = listOf("month", "tháng", "月")
        val yearWords = listOf("year", "năm")
        val agoWords = listOf("ago", "trước", "前")

        return try {
            if (agoWords.any { this.contains(it, ignoreCase = true) }) {
                val amount = Regex("""(\d+)""").find(this)?.groupValues?.get(1)?.toLong() ?: return 0L
                val now = ZonedDateTime.now(dateZone)

                val dateTime = when {
                    yearWords.any { this.contains(it, ignoreCase = true) } -> now.minusYears(amount)
                    monthWords.any { this.contains(it, ignoreCase = true) } -> now.minusMonths(amount)
                    dayWords.any { this.contains(it, ignoreCase = true) } -> now.minusDays(amount)
                    weekWords.any { this.contains(it, ignoreCase = true) } -> now.minusWeeks(amount)
                    hourWords.any { this.contains(it, ignoreCase = true) } -> now.minusHours(amount)
                    minuteWords.any { this.contains(it, ignoreCase = true) } -> now.minusMinutes(amount)
                    secondWords.any { this.contains(it, ignoreCase = true) } -> now.minusSeconds(amount)
                    else -> now
                }

                dateTime.toInstant().toEpochMilli()
            } else {
                (if (gmtOffset == null) this.substringAfterLast(" ") else "$this $gmtOffset").let {
                    // timestamp has year
                    if (Regex("""\d+/\d+/\d\d""").find(it)?.value != null) {
                        parseDate(it)
                    } else {
                        // MangaSum - timestamp sometimes doesn't have year (current year implied)
                        parseDate("$it/${LocalDateTime.now().year % 100}")
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    protected open fun parseDate(date: String): Long = dateFormat.tryParseZonedDateTime(date)
        .takeIf { it != 0L }
        ?: dateFormat.tryParseDateTime(date, dateZone)
            .takeIf { it != 0L }
        ?: dateFormat.tryParseDate(date, dateZone)

    // ============================== Pages ======================================

    open fun imageOrNull(element: Element): String? {
        // sources sometimes have an image element with an empty attr that isn't really an image
        fun Element.hasValidAttr(attr: String): Boolean {
            val regex = Regex("""https?://.*""", RegexOption.IGNORE_CASE)
            return when {
                this.attr(attr).isBlank() -> false
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = parsePageList(client.get(getChapterUrl(chapter)))

    protected open suspend fun parsePageList(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector).mapNotNull { img -> imageOrNull(img) }
            .distinct()
            .mapIndexed { i, image -> Page(i, "", image) }
    }

    // ============================== Filters ======================================

    protected class StatusFilter(name: String, pairs: List<Pair<String?, String>>) : UriPartFilter(name, pairs)

    protected class GenreFilter(name: String, pairs: List<Pair<String?, String>>) : UriPartFilter(name, pairs)

    protected open fun getStatusList(): List<Pair<String?, String>> = listOf(
        Pair(null, intl["STATUS_ALL"]),
        Pair("1", intl["STATUS_ONGOING"]),
        Pair("2", intl["STATUS_COMPLETED"]),
    )

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/$searchPath").asJsoup().let { document ->
        parseGenres(document).toJsonElement()
    }

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

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String?, String>>>() ?: emptyList()
        return getFilterList(genres)
    }

    protected open fun getFilterList(genres: List<Pair<String?, String>>): FilterList = FilterList(
        buildList {
            add(StatusFilter(intl["STATUS"], getStatusList()))
            if (genres.isNotEmpty()) {
                add(GenreFilter(intl["GENRE"], genres))
            }
        },
    )

    protected open class UriPartFilter(displayName: String, private val pairs: List<Pair<String?, String>>) : Filter.Select<String>(displayName, pairs.map { it.second }.toTypedArray()) {
        fun toUriPart() = pairs[state].first
    }
}
