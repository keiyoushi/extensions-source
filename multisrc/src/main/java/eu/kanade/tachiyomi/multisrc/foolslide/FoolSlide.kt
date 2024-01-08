package eu.kanade.tachiyomi.multisrc.foolslide

import android.app.Application
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

abstract class FoolSlide(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    open val urlModifier: String = "",
) : ConfigurableSource, ParsedHttpSource() {

    override val supportsLatest = true

    private val json by lazy { Injekt.get<Json>() }

    override fun popularMangaSelector() = "div.group"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/directory/$page/", headers)
    }

    private val latestUpdatesUrls = mutableSetOf<String>()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mp = super.latestUpdatesParse(response)
        return mp.copy(
            mp.mangas.distinctBy { it.url }.filter {
                latestUpdatesUrls.add(it.url)
            },
        )
    }

    override fun latestUpdatesSelector() = "div.group"

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) latestUpdatesUrls.clear()
        return GET("$baseUrl$urlModifier/latest/$page/")
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a[title]").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        element.select("img").first()?.let {
            thumbnail_url = it.absUrl("src").replace("/thumb_", "/")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a[title]").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    override fun popularMangaNextPageSelector() = "div.next"

    override fun latestUpdatesNextPageSelector(): String? = "div.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchHeaders = headersBuilder().add("Content-Type", "application/x-www-form-urlencoded").build()
        val form = FormBody.Builder().add("search", query).build()
        return POST("$baseUrl$urlModifier/search/", searchHeaders, form)
    }

    override fun searchMangaSelector() = "div.group"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[title]").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsRequest(manga: SManga) = allowAdult(super.mangaDetailsRequest(manga))

    protected open val mangaDetailsInfoSelector = "div.info"

    // if there's no image on the details page, get the first page of the first chapter
    protected fun getDetailsThumbnail(document: Document, urlSelector: String = chapterUrlSelector): String? {
        return document.select("div.thumbnail img, table.thumb img").firstOrNull()?.attr("abs:src")
            ?: document.select(chapterListSelector()).last()!!.select(urlSelector).attr("abs:href")
                .let { url -> client.newCall(allowAdult(GET(url))).execute() }
                .let { response -> pageListParse(response).first().imageUrl }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select(mangaDetailsInfoSelector).firstOrNull()?.html()?.let { infoHtml ->
            author = Regex("""(?i)(Author|Autore)</b>:\s?([^\n<]*)[\n<]""").find(infoHtml)?.groupValues?.get(2)
            artist = Regex("""Artist</b>:\s?([^\n<]*)[\n<]""").find(infoHtml)?.groupValues?.get(1)
            description = Regex("""(?i)(Synopsis|Description|Trama)</b>:\s?([^\n<]*)[\n<]""").find(infoHtml)?.groupValues?.get(2)
        }
        thumbnail_url = getDetailsThumbnail(document)
    }

    protected open val allowAdult: Boolean
        get() = preferences.getBoolean("adult", true)

    private fun allowAdult(request: Request): Request {
        val form = FormBody.Builder().add("adult", allowAdult.toString()).build()
        return POST(request.url.toString(), headers, form)
    }

    override fun chapterListRequest(manga: SManga) = allowAdult(super.chapterListRequest(manga))

    override fun chapterListSelector() = "div.group div.element, div.list div.element"

    protected open val chapterDateSelector = "div.meta_r"

    protected open val chapterUrlSelector = "a[title]"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElement = element.select(chapterUrlSelector).first()!!
        val dateElement = element.select(chapterDateSelector).first()!!
        setUrlWithoutDomain(urlElement.attr("href"))
        name = urlElement.text()
        date_upload = parseChapterDate(dateElement.text().substringAfter(", ")) ?: 0
    }

    protected open fun parseChapterDate(date: String): Long? {
        val lcDate = date.lowercase(Locale.ROOT)
        if (lcDate.endsWith(" ago")) {
            parseRelativeDate(lcDate)?.let { return it }
        }

        // Handle 'yesterday' and 'today', using midnight
        var relativeDate: Calendar? = null
        // Result parsed but no year, copy current year over
        when {
            lcDate.startsWith("yesterday") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.add(Calendar.DAY_OF_MONTH, -1) // yesterday
                relativeDate.set(Calendar.HOUR_OF_DAY, 0)
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
            lcDate.startsWith("today") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.set(Calendar.HOUR_OF_DAY, 0)
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
            lcDate.startsWith("tomorrow") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.add(Calendar.DAY_OF_MONTH, +1) // tomorrow
                relativeDate.set(Calendar.HOUR_OF_DAY, 0)
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
        }

        relativeDate?.timeInMillis?.let {
            return it
        }

        var result = DATE_FORMAT_1.parseOrNull(date)

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES) {
            if (result == null) {
                result = dateFormat.parseOrNull(date)
            } else {
                break
            }
        }

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR) {
            if (result == null) {
                result = dateFormat.parseOrNull(date)

                if (result != null) {
                    // Result parsed but no year, copy current year over
                    result = Calendar.getInstance().apply {
                        time = result!!
                        set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    }.time
                }
            } else {
                break
            }
        }

        return result?.time ?: 0L
    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return null

        val number = trimmedDate[0].toIntOrNull() ?: return null
        val unit = trimmedDate[1].removeSuffix("s") // Remove 's' suffix

        val now = Calendar.getInstance()

        // Map English unit to Java unit
        val javaUnit = when (unit) {
            "year", "yr" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week", "wk" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour", "hr" -> Calendar.HOUR
            "minute", "min" -> Calendar.MINUTE
            "second", "sec" -> Calendar.SECOND
            else -> return null
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    private fun SimpleDateFormat.parseOrNull(string: String): Date? {
        return try {
            parse(string)
        } catch (e: ParseException) {
            null
        }
    }

    override fun pageListRequest(chapter: SChapter) = allowAdult(super.pageListRequest(chapter))

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val jsonStr = doc.substringAfter("var pages = ").substringBefore(";")
        val pages = json.parseToJsonElement(jsonStr).jsonArray
        return pages.mapIndexed { i, jsonEl ->
            // Create dummy element to resolve relative URL
            val absUrl = document.createElement("a")
                .attr("href", jsonEl.jsonObject["url"]!!.jsonPrimitive.content)
                .absUrl("href")
            Page(i, "", absUrl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    protected val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = "adult"
            summary = "Show adult content"
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.let(screen::addPreference)
    }

    companion object {
        private val ORDINAL_SUFFIXES = listOf("st", "nd", "rd", "th")
        private val DATE_FORMAT_1 = SimpleDateFormat("yyyy.MM.dd", Locale.US)
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("dd'$it' MMMM, yyyy", Locale.US)
        }
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("dd'$it' MMMM", Locale.US)
        }
    }
}
