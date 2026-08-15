package eu.kanade.tachiyomi.multisrc.foolslide

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

abstract class FoolSlide :
    KeiSource(),
    ConfigurableSource {

    protected open val urlModifier = ""

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl$urlModifier/directory/$page/").asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector().let { selector -> document.select(selector).first() != null }
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.group"

    open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a[title]").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        element.select("img").first()?.let {
            thumbnail_url = it.absUrl("src").replace("/thumb_", "/")
        }
    }

    open fun popularMangaNextPageSelector() = "div.next"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl$urlModifier/latest/$page/").asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector -> document.select(selector).first() != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesSelector() = "div.group"

    open fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a[title]").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    open fun latestUpdatesNextPageSelector(): String? = "div.next"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val form = FormBody.Builder().add("search", query).build()
        val document = client.post("$baseUrl$urlModifier/search/", headers, form).asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector().let { selector -> document.select(selector).first() != null }
        return MangasPage(mangas, hasNextPage)
    }

    open fun searchMangaSelector() = "div.group"

    open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("a[title]").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    open fun searchMangaNextPageSelector() = "a:has(span.next)"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, adultHeaders).asJsoup()

        val sManga = mangaDetailsParse(document).apply { url = manga.url }
        val sChapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        return SMangaUpdate(sManga, sChapters)
    }

    protected open val mangaDetailsInfoSelector = "div.info"

    // if there's no image on the details page, get the first page of the first chapter
    protected suspend fun getDetailsThumbnail(document: Document, urlSelector: String = chapterUrlSelector): String? = document.select("div.thumbnail img, table.thumb img").firstOrNull()?.attr("abs:src")
        ?: document.select(chapterListSelector()).lastOrNull()?.select(urlSelector)?.attr("abs:href")
            ?.let { url -> client.get(url, adultHeaders).asJsoup() }
            ?.let { doc -> pageListParse(doc).firstOrNull()?.imageUrl }

    open suspend fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst(mangaDetailsInfoSelector)?.let { infoElement ->
            infoElement.select("b").forEach { b ->
                val text = b.text().lowercase(Locale.ROOT)
                val next = b.nextSibling()
                val value = if (next is org.jsoup.nodes.TextNode) {
                    next.text().trim().removePrefix(":").trim()
                } else {
                    ""
                }

                if (value.isEmpty()) return@forEach

                when {
                    "author" in text || "autore" in text -> author = value
                    "artist" in text -> artist = value
                    "synopsis" in text || "description" in text || "trama" in text -> description = value
                }
            }
        }
        thumbnail_url = getDetailsThumbnail(document)
    }

    protected val preferences by getPreferencesLazy()

    protected open val allowAdult: Boolean
        get() = preferences.getBoolean("adult", true)

    protected val adultHeaders: Headers get() = headersBuilder().add("Adult", "true").build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        if (request.header("Adult") == "true") {
            val newRequest = if (allowAdult) {
                val form = FormBody.Builder().add("adult", "true").build()
                request.newBuilder()
                    .removeHeader("Adult")
                    .method("POST", form)
                    .build()
            } else {
                request.newBuilder()
                    .removeHeader("Adult")
                    .build()
            }
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }

    open fun chapterListSelector() = "div.group div.element, div.list div.element"

    protected open val chapterDateSelector = "div.meta_r"

    protected open val chapterUrlSelector = "a[title]"

    open fun chapterFromElement(element: Element) = SChapter.create().apply {
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
        var relativeDate: ZonedDateTime? = null
        when {
            lcDate.startsWith("yesterday") -> {
                relativeDate = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS)
            }
            lcDate.startsWith("today") -> {
                relativeDate = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)
            }
            lcDate.startsWith("tomorrow") -> {
                relativeDate = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS)
            }
        }

        relativeDate?.toInstant()?.toEpochMilli()?.let { return it }

        var result: Long? = null
        result = runCatching { LocalDate.parse(date, DATE_FORMAT_1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES) {
            if (result == null) {
                result = runCatching { LocalDate.parse(date, dateFormat).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            } else {
                break
            }
        }

        for (dateFormat in DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR) {
            if (result == null) {
                result = runCatching { LocalDate.parse(date, dateFormat).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            } else {
                break
            }
        }

        return result ?: 0L
    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")

        if (trimmedDate.size < 3 || trimmedDate[2] != "ago") return null

        val number = trimmedDate[0].toLongOrNull() ?: return null
        val unit = trimmedDate[1].removeSuffix("s") // Remove 's' suffix
        val now = ZonedDateTime.now()

        val parsed = when (unit) {
            "year", "yr" -> now.minusYears(number)
            "month" -> now.minusMonths(number)
            "week", "wk" -> now.minusWeeks(number)
            "day" -> now.minusDays(number)
            "hour", "hr" -> now.minusHours(number)
            "minute", "min" -> now.minusMinutes(number)
            "second", "sec" -> now.minusSeconds(number)
            else -> return null
        }
        return parsed.toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url, adultHeaders).asJsoup()
        return pageListParse(document)
    }

    open fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val jsonStr = doc.substringAfter("var pages = ").substringBefore(";")
        val pages = jsonStr.parseAs<JsonArray>()
        return pages.mapIndexed { i, jsonEl ->
            // Create dummy element to resolve relative URL
            val absUrl = document.createElement("a")
                .attr("href", jsonEl.jsonObject["url"]!!.jsonPrimitive.content)
                .absUrl("href")
            Page(i, "", absUrl)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = "adult"
            summary = "Show adult content"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        private val ORDINAL_SUFFIXES = listOf("st", "nd", "rd", "th")
        private val DATE_FORMAT_1 = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US)
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES = ORDINAL_SUFFIXES.map {
            DateTimeFormatter.ofPattern("dd'$it' MMMM, yyyy", Locale.US)
        }
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES_NO_YEAR = ORDINAL_SUFFIXES.map {
            DateTimeFormatterBuilder().appendPattern("dd'$it' MMMM")
                .parseDefaulting(ChronoField.YEAR, LocalDate.now().year.toLong())
                .toFormatter(Locale.US)
        }
    }
}
