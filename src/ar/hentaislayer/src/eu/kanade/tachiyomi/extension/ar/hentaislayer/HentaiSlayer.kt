package eu.kanade.tachiyomi.extension.ar.hentaislayer

import android.app.Application
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

class HentaiSlayer : ParsedHttpSource(), ConfigurableSource {

    override val name = "هنتاي سلاير"

    override val baseUrl = "https://hentaislayer.net"

    override val lang = "ar"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaSelector() = "div > div:has(div#card-real)"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("div#card-real a")?.run {
            setUrlWithoutDomain(absUrl("href"))
            selectFirst("figure")?.run {
                selectFirst("img.object-cover")?.run {
                    thumbnail_url = imgAttr()
                    title = attr("alt")
                }
                genre = select("span p.drop-shadow-sm").text()
            }
        }
        genre = element.select("span a[href*='?genre=']")
            .map { it.text() }
            .let { listOf(genre) + it }
            .joinToString()
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:last-child:not(.pagination-disabled)"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest-${getLatestTypes()}?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga?title=$query".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> url.addQueryParameter("type", filter.toUriPart())
                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                is GenresFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.uriPart) }
                else -> {}
            }
        }

        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst("main section")?.run {
            selectFirst("img#manga-cover")?.run {
                thumbnail_url = imgAttr()
                title = attr("alt")
            }
            selectFirst("section > div:nth-child(1) > div:nth-child(1) > div:nth-child(2) > div:nth-child(2)")?.run {
                status = parseStatus(select("a[href*='?status=']").text())
                genre = select("a[href*='?type=']").text()
                author = select("p:has(span:contains(المؤلف)) span:nth-child(2)").text()
                artist = select("p:has(span:contains(الرسام)) span:nth-child(2)").text()
            }
            selectFirst("section > div:nth-child(1) > div:nth-child(2)")?.run {
                select("h1").text().takeIf { it.isNotEmpty() }?.let {
                    title = it
                }
                genre = select("a[href*='?genre=']")
                    .map { it.text() }
                    .let {
                        listOf(genre) + it
                    }
                    .joinToString()
                select("h2").text().takeIf { it.isNotEmpty() }?.let {
                    description = "Alternative name: $it\n"
                }
            }
            description += select("#description").text()
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("مستمر") -> SManga.ONGOING
        status.contains("متوقف") -> SManga.CANCELLED
        status.contains("مكتمل") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "main section #chapters-list a#chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = "\u061C" + element.select("#item-title").text() // Add unicode ARABIC LETTER MARK to ensure all titles are right to left

        date_upload = parseRelativeDate(element.select("#item-title + span").text()) ?: Calendar.getInstance().timeInMillis
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

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.chapter-image").mapIndexed { index, item ->
            Page(index = index, imageUrl = item.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String? {
        return when {
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            else -> attr("abs:src")
        }
    }

    override fun getFilterList() = FilterList(
        GenresFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    // ============================== Settings ==============================
    companion object {
        private const val LATEST_PREF = "LatestType"
        private val LATEST_PREF_ENTRIES get() = arrayOf(
            "مانجا",
            "مانهوا",
            "كوميكس",
        )
        private val LATEST_PREF_ENTRY_VALUES get() = arrayOf(
            "manga",
            "manhwa",
            "comics",
        )
        private val LATEST_PREF_DEFAULT = LATEST_PREF_ENTRY_VALUES[0]
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = LATEST_PREF
            title = "نوع القائمة الأحدث"
            summary = "حدد نوع الإدخالات التي سيتم الاستعلام عنها لأحدث قائمة. الأنواع الأخرى متوفرة في الشائع/التصفح أو البحث"
            entries = LATEST_PREF_ENTRIES
            entryValues = LATEST_PREF_ENTRY_VALUES
            setDefaultValue(LATEST_PREF_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(
                    screen.context,
                    ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)
    }

    private fun getLatestTypes(): String = preferences.getString(LATEST_PREF, LATEST_PREF_DEFAULT)!!
}
