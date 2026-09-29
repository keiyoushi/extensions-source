package eu.kanade.tachiyomi.extension.all.xkcd

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Xkcd :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(TextInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host != "thumbnail") return@addInterceptor chain.proceed(request)

            val image = this@Xkcd::class.java
                .getResourceAsStream("/assets/thumbnail.png")!!
                .readBytes()
            val responseBody = image.toResponseBody("image/png".toMediaType())
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }

    private val archive: String
        get() = when (lang) {
            "fr" -> "/tous-episodes.php"
            "ru" -> "/img"
            "zh" -> "/api/strips.json"
            else -> "/archive"
        }

    private val creator: String
        get() = when (lang) {
            "ru" -> "Рэндел Манро"
            "zh" -> "兰德尔·门罗"
            else -> "Randall Munroe"
        }

    private val synopsis: String
        get() = when (lang) {
            "es" -> "Un webcómic sobre romance, sarcasmo, mates y lenguaje."
            "fr" -> "Un webcomic sarcastique qui parle de romance, de maths et de langage."
            "ru" -> "о романтике, сарказме, математике и языке"
            "zh" -> "這裡翻譯某個關於浪漫、諷刺、數學、以及語言的漫畫"
            else -> "A webcomic of romance, sarcasm, math and language."
        }

    private val interactiveText: String
        get() = when (lang) {
            "es" -> "Para experimentar la versión interactiva de este cómic, ábralo en WebView/navegador."
            "zh" -> "要體驗本漫畫的互動版請在WebView/瀏覽器中打開。"
            "fr", "ru" -> throw UnsupportedOperationException()
            else -> "To experience the interactive version of this comic, open it in WebView/browser."
        }

    private val chapterListSelector: String
        get() = when (lang) {
            "es" -> ".archive-entry > a"
            "fr" -> "#content .s > a:not(:last-of-type)"
            "ru" -> ".main > a"
            "zh" -> throw UnsupportedOperationException()
            else -> "#middleContainer > a"
        }

    private val imageSelector: String
        get() = when (lang) {
            "es" -> "#middleContent .strip"
            "fr" -> "#content .s"
            "ru" -> ".main"
            "zh" -> "#content > img:not([id])"
            else -> "#comic > img"
        }

    private val defaultFallbackThumbnail = "https://thumbnail/xkcd.png"

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT)

    private fun String.timestamp(): Long = dateFormat.tryParseDate(this, ZoneOffset.UTC)

    private val chapterTitleFormatter: (Int, String) -> String = { number, text -> "$number: $text" }

    // Preferences
    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour
        private const val ENGLISH_BASE_URL = "https://xkcd.com"

        enum class OrganizationMethod(val key: String, val displayName: String) {
            SINGLE("SINGLE", "Single manga (all comics)"),
            BY_YEAR("BY_YEAR", "By year"),
            BY_YEAR_MONTH("BY_YEAR_MONTH", "By year-month"),
            ;

            companion object {
                const val PREF_KEY = "organization_method"
                val defaultOption = SINGLE
                fun fromKey(key: String) = entries.find { it.key == key } ?: defaultOption
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = OrganizationMethod.PREF_KEY
            title = "Organization Method"
            entries = OrganizationMethod.entries.map { it.displayName }.toTypedArray()
            entryValues = OrganizationMethod.entries.map { it.key }.toTypedArray()
            setDefaultValue(OrganizationMethod.defaultOption.key)
            summary = "Current: %s\n\n" +
                "Changing this will show comics grouped differently. " +
                "Your reading progress is tied to the organization method.\n\n" +
                "Note: You may need to exit and re-enter the extension for the manga list to update."
        }.let(screen::addPreference)
    }

    private fun getOrganizationMethod(): OrganizationMethod {
        val key = preferences.getString(OrganizationMethod.PREF_KEY, OrganizationMethod.defaultOption.key)!!
        return OrganizationMethod.fromKey(key)
    }

    // what key to use for grouping comics into mangas
    private fun getChapterToKey(): (SChapter) -> String = when (getOrganizationMethod()) {
        OrganizationMethod.SINGLE -> { _ -> "SINGLE" }
        OrganizationMethod.BY_YEAR -> { chapter ->
            yearFormat.format(Instant.ofEpochMilli(chapter.date_upload).atZone(ZoneId.systemDefault()))
        }
        OrganizationMethod.BY_YEAR_MONTH -> { chapter ->
            yearMonthFormat.format(Instant.ofEpochMilli(chapter.date_upload).atZone(ZoneId.systemDefault()))
        }
    }

    private val yearFormat = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT)
    private val yearMonthFormat = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT)

    private fun getKeyToTitleFormatter(): (String) -> String = when (getOrganizationMethod()) {
        OrganizationMethod.SINGLE -> { _ -> "xkcd" }
        OrganizationMethod.BY_YEAR -> { key -> "xkcd $key" }
        OrganizationMethod.BY_YEAR_MONTH -> { key -> "xkcd $key" }
    }

    // Archive caching

    private var comicDateMapping: Map<Int, String>? = null // Comic number -> date mapping from English
    private var comicDateMappingTime: Long = 0
    private var allChaptersCache: List<SChapter>? = null // Full chapter list with dates
    private var allChaptersCacheTime: Long = 0

    // some translations don't provide dates for their comics, but we can look up the dates
    // (of english publication) given the comic number which is shared across translations
    private suspend fun getComicDateMappingFromEnglishArchive(): Map<Int, String> {
        val now = System.currentTimeMillis()
        if (comicDateMapping == null || now - comicDateMappingTime > CACHE_EXPIRY_MS) {
            comicDateMapping = try {
                client.get("$ENGLISH_BASE_URL/archive/").asJsoup().select("#middleContainer > a").associate { element ->
                    val number = element.attr("href").removeSurrounding("/").toInt()
                    val date = element.attr("title") // "2026-1-9" format
                    number to date
                }
            } catch (_: Exception) {
                emptyMap()
            }
            comicDateMappingTime = now
        }
        return comicDateMapping ?: emptyMap()
    }

    private suspend fun getAllComicsAsChapters(): List<SChapter> {
        val now = System.currentTimeMillis()
        if (allChaptersCache == null || now - allChaptersCacheTime > CACHE_EXPIRY_MS) {
            allChaptersCache = chapterListParse(client.get(baseUrl + archive))
            allChaptersCacheTime = now
        }
        return allChaptersCache!!
    }

    private suspend fun getGroupedChapters(): Map<String, List<SChapter>> = getAllComicsAsChapters().groupBy(getChapterToKey())

    // organize chapters into mangas according to the chosen key
    private suspend fun makeGroupedManga(page: Int, perPage: Int = 10): MangasPage {
        val groupedChapters = getGroupedChapters()
        val allKeys = groupedChapters.keys.sortedDescending() // Newest first

        val startIndex = (page - 1) * perPage
        val endIndex = minOf(startIndex + perPage, allKeys.size)
        val hasNextPage = endIndex < allKeys.size

        if (startIndex >= allKeys.size) {
            return MangasPage(emptyList(), false)
        }

        val pageKeys = allKeys.subList(startIndex, endIndex)
        val mangas = pageKeys.map { key ->
            val chapters = groupedChapters[key]!!
            val firstChapter = chapters.firstOrNull()
            SManga.create().apply {
                title = getKeyToTitleFormatter()(key)
                url = key
                artist = creator
                author = creator
                description = synopsis
                status = SManga.ONGOING
                thumbnail_url = if (firstChapter != null && getOrganizationMethod() != OrganizationMethod.SINGLE) {
                    fetchThumbnailUrlForChapter(firstChapter)
                } else {
                    defaultFallbackThumbnail
                }
                initialized = true
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = makeGroupedManga(page)

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val newChapters = if (fetchChapters) getGroupedChapters()[manga.url] ?: emptyList() else chapters
        return SMangaUpdate(manga, newChapters)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl

    // ============================= Chapters ==============================

    private suspend fun chapterListParse(response: Response): List<SChapter> {
        val englishDates = getComicDateMappingFromEnglishArchive()

        if (lang == "zh") {
            return response.parseAs<Map<String, ZhStrip>>().values.map { strip ->
                val comicNumber = strip.id
                SChapter.create().apply {
                    url = "/$comicNumber"
                    name = chapterTitleFormatter(comicNumber, strip.title)
                    chapter_number = comicNumber.toFloat()
                    date_upload = if (englishDates.containsKey(comicNumber)) {
                        englishDates[comicNumber]!!.timestamp()
                    } else {
                        0L
                    }
                }
            }
        }

        var chapters = response.asJsoup().select(chapterListSelector).mapNotNull { element ->
            when (lang) {
                "es" -> {
                    val title = element.text()
                    val timeElement = element.parent()?.selectFirst("time") ?: return@mapNotNull null
                    val datePart = timeElement.text()

                    val spanishOverrides = mapOf("/strips/geografia/" to 1472)
                    val dateToNumber = englishDates.entries.associate { (number, date) ->
                        val parts = date.split("-")
                        val normalizedDate = if (parts.size == 3) {
                            "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
                        } else {
                            date
                        }
                        normalizedDate to number
                    }

                    val urlPath = element.absUrl("href").substringAfter(baseUrl)
                    val comicNumber = spanishOverrides[urlPath] ?: dateToNumber[datePart] ?: return@mapNotNull null

                    SChapter.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        name = chapterTitleFormatter(comicNumber, title)
                        chapter_number = comicNumber.toFloat()
                        date_upload = datePart.timestamp()
                    }
                }
                "fr" -> {
                    SChapter.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        val comicNumber = url.substringAfter('=').toIntOrNull()
                        name = chapterTitleFormatter(comicNumber ?: 0, element.text())
                        chapter_number = comicNumber?.toFloat() ?: 0f
                        date_upload = if (comicNumber != null && englishDates.containsKey(comicNumber)) {
                            englishDates[comicNumber]!!.timestamp()
                        } else {
                            0L
                        }
                    }
                }
                "ru" -> {
                    SChapter.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        val comicNumber = url.removeSurrounding("/").toIntOrNull()
                        val title = element.child(0).attr("alt")
                        name = chapterTitleFormatter(comicNumber ?: 0, title)
                        chapter_number = comicNumber?.toFloat() ?: 0f
                        date_upload = if (comicNumber != null && englishDates.containsKey(comicNumber)) {
                            englishDates[comicNumber]!!.timestamp()
                        } else {
                            0L
                        }
                    }
                }
                else -> { // en
                    SChapter.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        val comicNumber = url.removeSurrounding("/").toIntOrNull() ?: 0
                        name = chapterTitleFormatter(comicNumber, element.text())
                        chapter_number = comicNumber.toFloat()
                        date_upload = element.attr("title").timestamp()
                    }
                }
            }
        }

        if (lang == "fr") {
            chapters = chapters.reversed()
        }

        return chapters
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        val container = document.selectFirst(imageSelector)
            ?: error(interactiveText)

        val img = when (lang) {
            "fr" -> container.selectFirst("img[src^='strips/']")
            "ru" -> container.selectFirst("img[src*='/i/']")
            "zh" -> container
            else -> container.takeIf { it.nextElementSibling() == null }
        } ?: error(interactiveText)

        val image = when {
            lang == "fr" || lang == "ru" || lang == "zh" -> img.absUrl("src")
            !img.hasAttr("srcset") -> img.absUrl("src")
            else -> img.absUrl("srcset").substringBefore(' ')
        }

        val textParam1 = when (lang) {
            "fr" -> document.selectFirst("#content .s")?.selectFirst("div:not(.buttons)")?.text()?.takeIf { it.isNotEmpty() } ?: img.attr("alt")
            else -> img.attr("alt")
        }

        val textParam2 = when (lang) {
            "ru" -> document.selectFirst(".comics_text")?.text()?.takeIf { it.isNotEmpty() } ?: img.attr("alt")
            else -> img.attr("title")
        }

        val text = TextInterceptorHelper.createUrl(textParam1, textParam2)

        return listOf(Page(0, imageUrl = image), Page(1, imageUrl = text))
    }

    // ============================= Utilities =============================

    private fun extractImageFromContainer(container: Element): Element? = when (lang) {
        "fr" -> container.selectFirst("img[src^='strips/']")
        "ru" -> container.selectFirst("img[src*='/i/']")
        else -> container
    }

    private suspend fun fetchThumbnailUrlForChapter(chapter: SChapter): String {
        return try {
            client.get(baseUrl + chapter.url, ensureSuccess = false).use { response ->
                if (!response.isSuccessful) {
                    return defaultFallbackThumbnail
                }

                val doc = response.asJsoup()
                val container = doc.selectFirst(imageSelector)

                val img = container?.let { extractImageFromContainer(it) }
                    ?: doc.selectFirst("img[alt]")

                val url = img?.let {
                    when {
                        it.hasAttr("srcset") -> it.absUrl("srcset").substringBefore(' ')
                        it.hasAttr("src") -> it.absUrl("src")
                        else -> null
                    }
                }

                url?.takeIf { it.isNotEmpty() && !it.contains("thumbnail") } ?: defaultFallbackThumbnail
            }
        } catch (_: Exception) {
            defaultFallbackThumbnail
        }
    }
}

@Serializable
class ZhStrip(
    val id: Int,
    val title: String,
)
