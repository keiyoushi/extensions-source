package eu.kanade.tachiyomi.multisrc.foolslide

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

abstract class FoolSlide :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = true

    protected open val urlModifier = ""

    override suspend fun getPopularManga(page: Int): MangasPage {
        val request = GET("$baseUrl$urlModifier/directory/$page/", headers)
        val document = client.newCall(request).await().asJsoup()
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

    protected val latestUpdatesUrls = mutableSetOf<String>()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) latestUpdatesUrls.clear()
        val request = GET("$baseUrl$urlModifier/latest/$page/", headers)
        val document = client.newCall(request).await().asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector -> document.select(selector).first() != null } ?: false
        val newMangas = mangas.distinctBy { it.url }.filter { latestUpdatesUrls.add(it.url) }
        return MangasPage(newMangas, hasNextPage)
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
        val searchHeaders = headersBuilder().add("Content-Type", "application/x-www-form-urlencoded").build()
        val form = FormBody.Builder().add("search", query).build()
        val request = POST("$baseUrl$urlModifier/search/", searchHeaders, form)
        val document = client.newCall(request).await().asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector().let { selector -> document.select(selector).first() != null }
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

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
        val document: Document? = if (fetchDetails || fetchChapters) {
            val request = allowAdult(GET(baseUrl + manga.url, headers))
            client.newCall(request).await().asJsoup()
        } else {
            null
        }

        val sManga = if (fetchDetails) {
            mangaDetailsParse(document!!).apply { url = manga.url }
        } else {
            manga
        }

        val sChapters = if (fetchChapters) {
            document!!.select(chapterListSelector()).map { chapterFromElement(it) }
        } else {
            chapters
        }

        return SMangaUpdate(sManga, sChapters)
    }

    protected open val mangaDetailsInfoSelector = "div.info"

    // if there's no image on the details page, get the first page of the first chapter
    protected suspend fun getDetailsThumbnail(document: Document, urlSelector: String = chapterUrlSelector): String? = document.select("div.thumbnail img, table.thumb img").firstOrNull()?.attr("abs:src")
        ?: document.select(chapterListSelector()).lastOrNull()?.select(urlSelector)?.attr("abs:href")
            ?.let { url -> client.newCall(allowAdult(GET(url, headers))).await() }
            ?.use { response -> pageListParse(response.asJsoup()).firstOrNull()?.imageUrl }

    open suspend fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select(mangaDetailsInfoSelector).firstOrNull()?.html()?.let { infoHtml ->
            author = Regex("(?i)(Author|Autore)</b>:\\s?([^\\n<]*)[\\n<]").find(infoHtml)?.groupValues?.get(2)
            artist = Regex("Artist</b>:\\s?([^\\n<]*)[\\n<]").find(infoHtml)?.groupValues?.get(1)
            description = Regex("(?i)(Synopsis|Description|Trama)</b>:\\s?([^\\n<]*)[\\n<]").find(infoHtml)?.groupValues?.get(2)
        }
        thumbnail_url = getDetailsThumbnail(document)
    }

    protected open val allowAdult: Boolean
        get() = preferences.getBoolean("adult", true)

    protected open fun allowAdult(request: Request): Request {
        val form = FormBody.Builder().add("adult", allowAdult.toString()).build()
        return POST(request.url.toString(), headers, form)
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

        relativeDate?.timeInMillis?.let { return it }

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
                        time = result
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
        val time = this.tryParse(string)
        return if (time == 0L) null else Date(time)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val request = allowAdult(GET(baseUrl + chapter.url, headers))
        val document = client.newCall(request).await().asJsoup()
        return pageListParse(document)
    }

    open fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val jsonStr = doc.substringAfter("var pages = ").substringBefore(";")
        val pages = jsonInstance.parseToJsonElement(jsonStr).jsonArray
        return pages.mapIndexed { i, jsonEl ->
            // Create dummy element to resolve relative URL
            val absUrl = document.createElement("a")
                .attr("href", jsonEl.jsonObject["url"]!!.jsonPrimitive.content)
                .absUrl("href")
            Page(i, "", absUrl)
        }
    }

    protected val preferences by getPreferencesLazy()

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
