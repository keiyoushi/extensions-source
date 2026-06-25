package eu.kanade.tachiyomi.extension.id.cosmicscansid

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class CosmicScansID :
    MangaThemesia(
        "CosmicScans.id",
        "https://lc2.cosmicscans.to",
        "id",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
    ),
    ConfigurableSource {

    private val defaultBaseUrl: String = super.baseUrl

    private val preferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, defaultBaseUrl).let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    override val hasProjectPage = true

    private var cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (cachedBaseUrl == null) {
                cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return cachedBaseUrl!!
        }
        set(value) {
            cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        return GET(url.build(), headers)
    }

    override val seriesThumbnailSelector = listOf(
        ".thumb img",
        ".infomanga img",
        ".bigcover img",
        ".poster img",
        ".manga-thumb img",
        "meta[property=og:image]",
        "meta[name=twitter:image]",
    ).joinToString()

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url?.takeIf { it.isNotBlank() }
            ?: document.select(seriesThumbnailSelector).imgAttr()
    }

    override fun chapterListSelector() = listOf(
        "div.bxcl li:has(a[href])",
        "div.cl li:has(a[href])",
        "#chapterlist li:has(a[href])",
        "div.eplister li:has(a[href])",
        "ul li:has(div.chbox):has(div.eph-num):has(a[href])",
    ).joinToString()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        countViews(document)

        val normalChapters = document.select(chapterListSelector()).map(::chapterFromElement)
        val dateByChapter = normalChapters
            .filter { it.chapter_number >= 0F && it.date_upload > 0L }
            .groupBy { it.chapter_number }
            .mapValues { (_, chapters) -> chapters.maxOf { it.date_upload } }

        val ajaxChapters = fetchAjaxChapters(document, normalChapters.firstOrNull())
        val chapters = if (ajaxChapters.isNotEmpty()) ajaxChapters else normalChapters

        return chapters
            .onEach { chapter ->
                if (chapter.date_upload == 0L) {
                    chapter.date_upload = dateByChapter[chapter.chapter_number] ?: 0L
                }
            }
            .groupBy { it.chapterKey() }
            .map { (_, chapters) -> chapters.pickBestChapter() }
            .sortedWith(
                compareByDescending<SChapter> { it.chapter_number >= 0F }
                    .thenByDescending { it.chapter_number }
                    .thenByDescending { it.date_upload },
            )
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElement = element.selectFirst("a[href]")!!
        val chapterUrl = urlElement.attr("href")
        val chapterText = element.text()

        setUrlWithoutDomain(chapterUrl.normalizeChapterUrl())
        chapter_number = chapterNumberFrom(chapterUrl, chapterText)
        name = chapterNameFrom(chapterUrl, chapterText)
        date_upload = element.selectFirst(".chapterdate, .epl-date, time")
            ?.let { dateElement ->
                dateElement.attr("datetime").ifBlank { dateElement.text() }.parseChapterDate()
            }
            .takeIf { it != 0L }
            ?: chapterText.parseChapterDate()
    }

    private fun fetchAjaxChapters(document: Document, latestChapter: SChapter?): List<SChapter> {
        val ajaxDocument = document.takeIf { it.extractMangaId() != null }
            ?: latestChapter?.let { chapter ->
                runCatching {
                    client.newCall(GET("$baseUrl${chapter.url}", headers)).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.asJsoup().takeIf { it.extractMangaId() != null }
                    }
                }.getOrNull()
            }
            ?: return emptyList()

        val mangaId = ajaxDocument.extractMangaId() ?: return emptyList()
        val formBody = FormBody.Builder()
            .add("action", "get_chapters")
            .add("id", mangaId)
            .build()

        val ajaxHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", ajaxDocument.location())
            .build()

        return try {
            client.newCall(POST(ajaxDocument.extractAjaxUrl(), ajaxHeaders, formBody)).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                parseAjaxChapters(response.body.string())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseAjaxChapters(response: String): List<SChapter> {
        val document = Jsoup.parse(response, baseUrl)

        return document.select("option[value]:not([value=''])").map { option ->
            SChapter.create().apply {
                val chapterUrl = option.attr("value")
                val chapterText = option.text()

                setUrlWithoutDomain(chapterUrl.normalizeChapterUrl())
                chapter_number = chapterNumberFrom(chapterUrl, chapterText)
                name = chapterNameFrom(chapterUrl, chapterText)
            }
        }
    }

    private fun Document.extractAjaxUrl(): String = AJAX_URL_REGEX.find(toString())
        ?.groupValues
        ?.get(1)
        ?.replace("\\/", "/")
        ?: "$baseUrl/wp-admin/admin-ajax.php"

    private fun Document.extractMangaId(): String? {
        val html = toString()

        MANGA_ID_REGEXES.forEach { regex ->
            regex.find(html)?.groupValues?.get(1)?.let { return it }
        }

        return null
    }

    private fun List<SChapter>.pickBestChapter(): SChapter = sortedWith(
        compareByDescending<SChapter> { it.url.hasLeadingZeroChapter() }
            .thenByDescending { it.date_upload },
    ).first()

    private fun SChapter.chapterKey(): String {
        val suffix = if (name.contains("[Novel]", ignoreCase = true)) "-novel" else ""

        return if (chapter_number >= 0F) {
            "chapter-$chapter_number$suffix"
        } else {
            url.normalizeChapterUrl()
        }
    }

    private fun chapterNameFrom(url: String, text: String): String {
        val chapter = chapterStringFrom(url, text)
        if (chapter != null) {
            val suffix = if (isNovelChapter(url, text)) " [Novel]" else ""
            return "Chapter $chapter$suffix"
        }

        return url.normalizeChapterUrl()
            .removeSuffix("/")
            .substringAfterLast("/")
            .replace('-', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun chapterNumberFrom(url: String, text: String = ""): Float = chapterStringFrom(url, text)?.toFloatOrNull() ?: -1F

    private fun chapterStringFrom(url: String, text: String): String? {
        val slug = url.normalizeChapterUrl().removeSuffix("/").substringAfterLast("/")

        return CHAPTER_NUMBER_REGEX.find(slug)?.groupValues?.get(1)
            ?: CHAPTER_NUMBER_REGEX.find(text)?.groupValues?.get(1)
    }

    private fun String.hasLeadingZeroChapter(): Boolean {
        val slug = normalizeChapterUrl().removeSuffix("/").substringAfterLast("/")
        return LEADING_ZERO_CHAPTER_REGEX.containsMatchIn(slug)
    }

    private fun isNovelChapter(url: String, text: String): Boolean = url.contains("novel", ignoreCase = true) || text.contains("novel", ignoreCase = true)

    private fun String.normalizeChapterUrl(): String {
        val path = substringBefore("?")
            .substringBefore("#")
            .removeSuffix("/")
            .let { url ->
                if (url.startsWith("http")) {
                    "/" + url.substringAfter("://").substringAfter("/")
                } else {
                    url
                }
            }

        return if (path.startsWith("/")) "$path/" else "/$path/"
    }

    override fun String?.parseChapterDate(): Long {
        if (this.isNullOrBlank()) return 0L

        val date = trim()
        val lowerDate = date.lowercase(Locale.ROOT)
        val amount = DATE_NUMBER_REGEX.find(lowerDate)?.groupValues?.get(1)?.toIntOrNull()

        if (lowerDate.contains("hari ini") || lowerDate.contains("today")) {
            return System.currentTimeMillis()
        }

        if (amount != null) {
            val calendar = Calendar.getInstance()
            when {
                lowerDate.contains("menit") || lowerDate.contains("minute") -> calendar.add(Calendar.MINUTE, -amount)
                lowerDate.contains("jam") || lowerDate.contains("hour") -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
                lowerDate.contains("hari") || lowerDate.contains("day") -> calendar.add(Calendar.DATE, -amount)
                lowerDate.contains("minggu") || lowerDate.contains("week") -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
                lowerDate.contains("bulan") || lowerDate.contains("month") -> calendar.add(Calendar.MONTH, -amount)
                lowerDate.contains("tahun") || lowerDate.contains("year") -> calendar.add(Calendar.YEAR, -amount)
                else -> return parseSimpleDate(date)
            }
            return calendar.timeInMillis
        }

        return parseSimpleDate(DATE_REGEX.find(date)?.value ?: date)
    }

    private fun parseSimpleDate(date: String): Long {
        DATE_FORMATS.forEach { format ->
            try {
                return format.parse(date)?.time ?: 0L
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    override fun Element.imgAttr(): String = sequenceOf("content", "data-lazy-src", "data-src", "data-cfsrc", "src")
        .map { attr("abs:$it").ifBlank { attr(it) } }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
        .orEmpty()

    override fun Elements.imgAttr(): String = firstOrNull()?.imgAttr().orEmpty()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Edit source URL"
            summary = "For temporary use, if the extension is updated the change will be lost."
            dialogTitle = title
            dialogMessage = "Default URL:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the application to apply the changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"

        private val DATE_FORMATS = listOf(
            SimpleDateFormat("MMMM d, yyyy", Locale("id")),
            SimpleDateFormat("MMMM d, yyyy", Locale.US),
            SimpleDateFormat("dd/MM/yy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
        ).onEach { it.isLenient = false }

        private val DATE_NUMBER_REGEX = """(\d+)""".toRegex()

        private val DATE_REGEX = (
            """(?:Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember|""" +
                """January|February|March|May|June|July|August|October|December)\s+\d{1,2},\s+\d{4}"""
            ).toRegex(RegexOption.IGNORE_CASE)

        private val CHAPTER_NUMBER_REGEX = """(?:chapter|ch\.?|bab)-?\s*(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)

        private val LEADING_ZERO_CHAPTER_REGEX = """(?:chapter|ch\.?|bab)-?0\d+(?:\.\d+)?""".toRegex(RegexOption.IGNORE_CASE)

        private val AJAX_URL_REGEX = """["']ajaxurl["']\s*:\s*["']([^"']+admin-ajax\.php)["']""".toRegex()

        private val MANGA_ID_REGEXES = listOf(
            """["']mid["']\s*:\s*(\d+)""".toRegex(),
            """post_id\s*=\s*["']?(\d+)""".toRegex(),
            """postid-(\d+)""".toRegex(),
        )
    }
}
