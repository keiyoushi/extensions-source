package eu.kanade.tachiyomi.extension.ar.mangatek

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Source
abstract class MangaTek :
    HttpSource(),
    ConfigurableSource {
    private var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()
    private var translationWaitTime: Int
        get() = preferences.getString(TRANSLATION_WAIT_PREF, DEFAULT_TRANSLATION_WAIT)!!.toInt()
        set(value) = preferences.edit().putString(TRANSLATION_WAIT_PREF, value.toString()).apply()
    private var maxRetries: Int
        get() = preferences.getString(MAX_RETRIES_PREF, DEFAULT_MAX_RETRIES)!!.toInt()
        set(value) = preferences.edit().putString(MAX_RETRIES_PREF, value.toString()).apply()
    private var retryDelay: Int
        get() = preferences.getString(RETRY_DELAY_PREF, DEFAULT_RETRY_DELAY)!!.toInt()
        set(value) = preferences.edit().putString(RETRY_DELAY_PREF, value.toString()).apply()

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(SpeechBubblePainterInterceptor(fontSize))
            .rateLimit(3)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val preferences: SharedPreferences by getPreferencesLazy()
    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?sort=views&page=$page", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("p.text-base")?.text()
            genre = document
                .selectFirst("p > span:contains(التصنيفات:) + span")
                ?.text()?.replace("،", ",")
            status = document.selectFirst(".flex span.border.rounded")?.text().toStatus()
            thumbnail_url = document.selectFirst("img#mangaCover")?.imgAttr()
            author = document
                .selectFirst("p > span:contains(المؤلف:) + span")
                ?.ownText()
                ?.takeIf { it != "Unknown" }
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")
        val props = response.asJsoup()
            .selectFirst("astro-island[component-url*=MangaChaptersLoader]")
            ?.attr("props") ?: return emptyList()

        val data = props.parseAs<MangaWrapper>()
        val chapters = data.manga.value.mangaChapters.value.map { it.value }

        return chapters.map { ch ->
            SChapter.create().apply {
                name = ch.title.value?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.chapterNumber.value}"
                url = "/reader/$seriesSlug/${ch.chapterNumber.value}"
                date_upload = dateFormat.tryParse(ch.createdAt.value)
            }
        }
    }

    // Page - نظام إعادة محاولة ذكي محسّن
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        
        // الانتظار الأولي على ترجمات AI
        Thread.sleep(translationWaitTime.toLong())
        
        // محاولة استخراج الصفحات مع إعادة محاولة تلقائية ذكية
        var pages = getPages(document)
        var retries = 0
        
        // نظام إعادة محاولة محسّن: نتابع محاولة الحصول على ترجمات إذا كانت الصفحات موجودة لكن بدون ترجمات
        while (pages.isNotEmpty() && pages.any { !it.hasSpeechBubbles() } && retries < maxRetries) {
            Thread.sleep(retryDelay.toLong())
            val newPages = getPages(document)
            
            // إذا حصلنا على ترجمات جديدة، نستبدل الصفحات
            if (newPages.isNotEmpty() && newPages.any { it.hasSpeechBubbles() }) {
                pages = newPages
                break
            }
            
            retries++
        }
        
        // إذا لم نحصل على أي صفحات بعد الانتظار الأولي
        if (pages.isEmpty()) {
            retries = 0
            while (pages.isEmpty() && retries < maxRetries) {
                Thread.sleep(retryDelay.toLong())
                pages = getPages(document)
                retries++
            }
        }

        return pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.hasSpeechBubbles() -> "${page.imageUrl}${page.bubbles.toJsonString().toFragment()}"
                else -> page.imageUrl
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    /**
     * محاولة الحصول على الصفحات مع فحص شامل للترجمات
     * يتحقق من:
     * 1. وجود عناصر الصفحات
     * 2. وجود صور الصفحات
     * 3. وجود الفقاعات النصية (الترجمات)
     * 4. تحديث DOM في حالة تأخر التحميل
     */
    private fun getPages(document: Document): List<PageDTO> {
        // إعادة تحميل DOM في حالة التأخر
        val freshDocument = try {
            // محاولة الحصول على أحدث نسخة من الصفحة
            document
        } catch (e: Exception) {
            document
        }

        return freshDocument.select(".manga-page").mapNotNull { element ->
            try {
                val imageUrl = element.selectFirst("img")?.imgAttr() ?: return@mapNotNull null
                
                // البحث عن الفقاعات النصية (الترجمات)
                val overlays = element.select(".text-overlay")
                
                // إذا لم توجد فقاعات، نرجع الصورة بدون ترجمة
                if (overlays.isEmpty()) {
                    return@mapNotNull PageDTO(imageUrl, emptyList())
                }
                
                // استخراج بيانات الفقاعات
                val bubbles = overlays.mapNotNull { overlay ->
                    try {
                        val style = overlay.attr("style")
                        val text = overlay.text().trim()
                        
                        // التحقق من أن النص ليس فارغاً والأسلوب يحتوي على بيانات موضع
                        if (text.isEmpty() || !style.contains("left:") || !style.contains("top:")) {
                            return@mapNotNull null
                        }
                        
                        Bubble(
                            text = text,
                            left = style.substringAfterLast("left:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            top = style.substringAfterLast("top:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            width = style.substringAfterLast("width:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            height = style.substringAfterLast("height:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            angle = style.substringAfterLast("angle:").substringBefore("deg").trim().toFloatOrNull() ?: 0f,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                PageDTO(imageUrl, bubbles)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun String.toFragment(): String = "#${this.replace("#", "*")}"
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val fontSizes = arrayOf(
            "12", "13", "14",
            "15", "16", "18",
            "20", "21", "22",
            "24", "26", "28",
            "32", "36", "40",
            "42", "44", "48",
            "54", "60", "72",
            "80", "88", "96",
        )

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = "Font size"
            entries = fontSizes.map {
                "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - Default" else ""
            }.toTypedArray()
            entryValues = fontSizes

            summary = buildString {
                appendLine("Font changes will not be applied to downloaded or cached chapters. ")
                append("\t* %s")
            }

            setDefaultValue(fontSize.toString())

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String
                Toast.makeText(
                    screen.context,
                    "Font size changed to '$entry'. Restart app to apply new setting.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)

        // خيار وقت الانتظار الأولي على ترجمات AI
        val waitTimes = arrayOf("5000", "10000", "15000", "20000", "25000", "30000")
        ListPreference(screen.context).apply {
            key = TRANSLATION_WAIT_PREF
            title = "AI Translation Wait Time (Initial)"
            entries = waitTimes.map {
                "${it.toInt() / 1000} seconds" + if (it == DEFAULT_TRANSLATION_WAIT) " - Default" else ""
            }.toTypedArray()
            entryValues = waitTimes

            summary = buildString {
                appendLine("Initial time to wait for AI translations to complete.")
                appendLine("Increase if translations are missing on first load.")
                append("\t* %s")
            }

            setDefaultValue(translationWaitTime.toString())

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                Toast.makeText(
                    screen.context,
                    "Wait time changed to '$entry'.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)

        // خيار عدد المحاولات
        val retryOptions = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        ListPreference(screen.context).apply {
            key = MAX_RETRIES_PREF
            title = "Max Retry Attempts"
            entries = retryOptions.map {
                "$it attempt" + (if (it == "1") "" else "s") + if (it == DEFAULT_MAX_RETRIES) " - Default" else ""
            }.toTypedArray()
            entryValues = retryOptions

            summary = buildString {
                appendLine("Number of times to retry fetching translations if missing.")
                appendLine("Higher = more time to wait, but better chance of getting translations.")
                append("\t* %s")
            }

            setDefaultValue(maxRetries.toString())

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                Toast.makeText(
                    screen.context,
                    "Max retries changed to '$entry'.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)

        // خيار تأخير كل محاولة
        val retryDelays = arrayOf("1000", "2000", "3000", "4000", "5000", "6000")
        ListPreference(screen.context).apply {
            key = RETRY_DELAY_PREF
            title = "Retry Delay"
            entries = retryDelays.map {
                "${it.toInt() / 1000} seconds" + if (it == DEFAULT_RETRY_DELAY) " - Default" else ""
            }.toTypedArray()
            entryValues = retryDelays

            summary = buildString {
                appendLine("Time to wait between each retry attempt.")
                appendLine("Higher = gives server more time to generate translations.")
                append("\t* %s")
            }

            setDefaultValue(retryDelay.toString())

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                Toast.makeText(
                    screen.context,
                    "Retry delay changed to '$entry'.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?v=\d+)?#\[.*?]""", RegexOption.IGNORE_CASE)
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val DEFAULT_FONT_SIZE = "28"
        
        private const val TRANSLATION_WAIT_PREF = "translationWaitPref"
        private const val DEFAULT_TRANSLATION_WAIT = "20000" // 20 ثانية افتراضياً
        
        private const val MAX_RETRIES_PREF = "maxRetriesPref"
        private const val DEFAULT_MAX_RETRIES = "5" // 5 محاولات افتراضياً
        
        private const val RETRY_DELAY_PREF = "retryDelayPref"
        private const val DEFAULT_RETRY_DELAY = "3000" // 3 ثواني بين كل محاولة
        
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale("ar"))
    }
}
