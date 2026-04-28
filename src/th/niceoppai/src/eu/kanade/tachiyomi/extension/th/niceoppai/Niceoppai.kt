package eu.kanade.tachiyomi.extension.th.niceoppai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class Niceoppai : HttpSource() {
    override val baseUrl: String = "https://www.niceoppai.net"
    override val lang: String = "th"
    override val name: String = "Niceoppai"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga_list/all/any/most-popular-monthly/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.nde").mapNotNull { element ->
            SManga.create().apply {
                title = element.selectFirst("div.det a")?.text() ?: return@mapNotNull null
                element.selectFirst("div.cvr a")?.let {
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.selectFirst("div.cvr img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.select("ul.pgg li a").last()?.text() == "Next"
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga_list/all/any/last-updated/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val orderByFilter = filters.firstInstanceOrNull<OrderByFilter>()
        val orderByState = orderByFilter?.state ?: 0
        val orderByString = ORDER_BY_FILTER_OPTIONS_VALUES[orderByState]

        return if (orderByState != 0) {
            GET("$baseUrl/manga_list/all/any/$orderByString/$page", headers)
        } else {
            GET("$baseUrl/manga_list/search/$query/$orderByString/$page", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun getStatus(status: String) = when (status) {
        "ยังไม่จบ" -> SManga.ONGOING
        "จบแล้ว" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.det") ?: return SManga.create()
        val titleElement = document.selectFirst("h1.ttl") ?: return SManga.create()

        return SManga.create().apply {
            title = titleElement.text()
            author = infoElement.select("p").getOrNull(2)?.selectFirst("a")?.text()
            artist = author
            status = infoElement.select("p").getOrNull(9)?.ownText()?.replace(": ", " ")?.let { getStatus(it) } ?: SManga.UNKNOWN
            genre = infoElement.select("p").getOrNull(5)?.select("a")?.joinToString { it.text() }
            description = infoElement.select("p").firstOrNull()?.ownText()?.replace(": ", " ")
            thumbnail_url = document.selectFirst("div.mng_ifo div.cvr_ara img")?.attr("abs:src")
            initialized = true
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val pageUrls = document.select("ul.pgg li a")
            .filter { it.text() != "Next" && it.text() != "Last" }
            .map { it.attr("abs:href") }
            .distinct()

        val chList = mutableListOf<SChapter>()
        if (pageUrls.isNotEmpty()) {
            pageUrls.forEach { urlPage ->
                client.newCall(GET(urlPage, headers)).execute().use { res ->
                    chList += parseChaptersFromDocument(res.asJsoup(), chList.size)
                }
            }
        } else {
            chList += parseChaptersFromDocument(document)
        }
        return chList
    }

    private fun parseChaptersFromDocument(document: org.jsoup.nodes.Document, startIdx: Int = 0): List<SChapter> {
        val elements = document.select("ul.lst li.lng_")
        if (elements.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    name = "Chapter 1"
                    chapter_number = 1.0f
                },
            )
        }
        return elements.mapIndexed { idx, chapter ->
            val parsedChapter = SChapter.create()
            val btn = chapter.selectFirst("a.lst")
            btn?.let {
                parsedChapter.setUrlWithoutDomain(it.attr("abs:href"))
                parsedChapter.name = it.selectFirst("b.val")?.text() ?: ""
                parsedChapter.date_upload = parseChapterDate(it.selectFirst("b.dte")?.text())
            }

            if (parsedChapter.name.isEmpty()) {
                parsedChapter.chapter_number = 0.0f
            } else {
                val wordsChapter = parsedChapter.name.replace("ตอนที่. ", "").split(" - ")
                parsedChapter.chapter_number = wordsChapter.firstOrNull()?.toFloatOrNull() ?: (startIdx + idx + 1).toFloat()
            }
            parsedChapter
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#image-container > center > img").mapIndexed { i, img ->
            Page(i, imageUrl = if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        OrderByFilter(
            ORDER_BY_FILTER_TITLE,
            ORDER_BY_FILTER_OPTIONS.zip(ORDER_BY_FILTER_OPTIONS_VALUES).toList(),
            0,
        ),
    )

    private fun parseChapterDate(date: String?): Long {
        if (date == null) return 0L

        return when {
            WordSet("yesterday", "يوم واحد").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1)
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
                    add(Calendar.DAY_OF_MONTH, -2)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("ago", "atrás", "önce", "قبل").endsWith(date) -> parseRelativeDate(date)
            ordinalRegex.containsMatchIn(date) -> {
                val cleanedDate = date.split(" ").joinToString(" ") {
                    if (ordinalRegex.containsMatchIn(it)) it.replace(ordinalRegex, "") else it
                }
                dateFormat.tryParse(cleanedDate)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = relativeDateRegex.find(date)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        return when {
            WordSet("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("detik", "segundo", "second", "วินาที").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("week").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("month").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0L
        }
    }

    companion object {
        private val dateFormat: SimpleDateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.US)
        }
        private val relativeDateRegex = Regex("""(\d+)""")
        private val ordinalRegex = Regex("""\d(st|nd|rd|th)""")
    }
}

class WordSet(private vararg val words: String) {
    fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
    fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
}
