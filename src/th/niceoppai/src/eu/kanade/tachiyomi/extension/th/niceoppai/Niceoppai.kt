package eu.kanade.tachiyomi.extension.th.niceoppai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class Niceoppai : ParsedHttpSource() {

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    override val baseUrl: String = "https://www.niceoppai.net"

    override val lang: String = "th"
    override val name: String = "Niceoppai"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga_list/all/any/most-popular-monthly/$page", headers)
    }
    override fun popularMangaSelector() = "div.nde"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.det a")!!.text()
        element.select("div.cvr").let {
            setUrlWithoutDomain(it.select("a").attr("href"))
            thumbnail_url = it.select("img").attr("abs:src")
        }
    }
    override fun popularMangaNextPageSelector() = "ul.pgg li a"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga_list/all/any/last-updated/$page", headers)
    }
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val isOrderByFilter = filters.list.first().state != 0
        val orderByState = if (filters.list.first().state != null) filters.first().state.toString().toInt() else 0
        val orderByString = orderByFilterOptionsValues[orderByState]

        return if (isOrderByFilter) {
            GET("$baseUrl/manga_list/all/any/$orderByString/$page", headers)
        } else {
            GET("$baseUrl/manga_list/search/$query/$orderByString/$page", headers)
        }
    }
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map {
                val document = it.asJsoup()
                val mangas: List<SManga> =
                    document.select(searchMangaSelector()).map { element ->
                        searchMangaFromElement(element)
                    }

                MangasPage(mangas, false)
            }
    }

    // Manga summary page
    private fun getStatus(status: String) = when (status) {
        "ยังไม่จบ" -> SManga.ONGOING
        "จบแล้ว" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.det").first()!!
        val titleElement = document.select("h1.ttl").first()!!

        return SManga.create().apply {
            title = titleElement.text()
            author = infoElement.select("p")[2].select("a").text()
            artist = author
            status = getStatus(infoElement.select("p")[9].ownText().replace(": ", ""))
            genre = infoElement.select("p")[5].select("a").joinToString { it.text() }
            description = infoElement.select("p").first()!!.ownText().replace(": ", "")
            thumbnail_url = document.select("div.mng_ifo div.cvr_ara img").first()!!.attr("abs:src")
            initialized = true
        }
    }

    // Chapters
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            // Handle 'yesterday' and 'today', using midnight
            WordSet("yesterday", "يوم واحد").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1) // yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("today").startsWith(date) -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("يومين").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("ago", "atrás", "önce", "قبل").endsWith(date) -> {
                parseRelativeDate(date)
            }

            date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                date.split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { dateFormat.tryParse(it.joinToString(" ")) }
            }
            else -> dateFormat.tryParse(date)
        }
    }

    // Parses dates in this form:
    // 21 horas ago
    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("detik", "segundo", "second", "วินาที").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("week").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("month").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }
    override fun chapterListSelector() = "ul.lst li.lng_"
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Unused")
    private fun chapterFromElementWithIndex(element: Element, idx: Int, manga: SManga): SChapter {
        val chapter = SChapter.create()

        val btn = element.select("a.lst")
        chapter.setUrlWithoutDomain(btn.attr("href"))
        chapter.name = btn.select("b.val").text()
        val dateText = btn.select("b.dte").text()
        chapter.date_upload = parseChapterDate(dateText)

        if (chapter.name.isEmpty()) {
            chapter.chapter_number = 0.0f
        } else {
            val wordsChapter = chapter.name.replace("ตอนที่. ", "").split(" - ")
            try {
                chapter.chapter_number = wordsChapter[0].toFloat()
            } catch (ex: NumberFormatException) {
                chapter.chapter_number = (idx + 1).toFloat()
            }
        }

        return chapter
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val listPage = document.select("ul.pgg li a").toList()
            .filter { it.text() != "Next" && it.text() != "Last" }
            .map { it.select("a").attr("href") }
            .distinct()

        val chList: MutableList<SChapter> = mutableListOf()
        if (listPage.isNotEmpty()) {
            listPage.forEach { urlPage ->
                val res: Response = client.newCall(GET(urlPage, headers)).execute()
                val mangaDocument = res.asJsoup()
                if (mangaDocument.select(chapterListSelector()).isEmpty()) {
                    val createdChapter = SChapter.create().apply {
                        name = "Chapter 1"
                        chapter_number = 1.0f
                    }
                    chList += listOf(createdChapter)
                } else {
                    chList +=
                        mangaDocument.select(chapterListSelector()).mapIndexed { idx, Chapter ->
                            chapterFromElementWithIndex(Chapter, idx, SManga.create())
                        }
                }
            }
        } else {
            chList +=
                document.select(chapterListSelector()).mapIndexed { idx, Chapter ->
                    chapterFromElementWithIndex(Chapter, idx, SManga.create())
                }
        }
        return chList
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.mng_rdr div#image-container img").mapIndexed { i, img ->
            if (img.hasAttr("data-src")) {
                Page(i, "", img.attr("abs:data-src"))
            } else {
                Page(i, "", img.attr("abs:src"))
            }
        }
    }
    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // Filter
    private val orderByFilterTitle: String = "Order By เรียกตาม"
    private val orderByFilterOptions: Array<String> = arrayOf("Name (A-Z)", "Name (Z-A)", "Last Updated", "Oldest Updated", "Most Popular", "Most Popular (Weekly)", "Most Popular (Monthly)", "Least Popular", "Last Added", "Early Added", "Top Rating", "Lowest Rating")
    private val orderByFilterOptionsValues: Array<String> = arrayOf("name-az", "name-za", "last-updated", "oldest-updated", "most-popular", "most-popular-weekly", "most-popular-monthly", "least-popular", "last-added", "early-added", "top-rating", "lowest-rating")
    private class OrderByFilter(title: String, options: List<Pair<String, String>>, state: Int = 0) : UriPartFilter(title, options.toTypedArray(), state)
    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            OrderByFilter(
                orderByFilterTitle,
                orderByFilterOptions.zip(orderByFilterOptionsValues),
                0,
            ),
        )

        return FilterList(filters)
    }
    open class UriPartFilter(displayName: String, vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state)
}

class WordSet(private vararg val words: String) {
    fun anyWordIn(dateString: String): Boolean =
        words.any { dateString.contains(it, ignoreCase = true) }

    fun startsWith(dateString: String): Boolean =
        words.any { dateString.startsWith(it, ignoreCase = true) }

    fun endsWith(dateString: String): Boolean =
        words.any { dateString.endsWith(it, ignoreCase = true) }
}
