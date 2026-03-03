package eu.kanade.tachiyomi.extension.all.manhuarm

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.CloudflareWarmupInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing.BingTranslator
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.google.GoogleTranslator
import eu.kanade.tachiyomi.multisrc.machinetranslations.translator.TranslatorEngine
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.lib.i18n.Intl.Companion.createDefaultMessageFileName
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
class Manhuarm(
    private val language: Language,
) : Madara(
    "Manhuarm",
    "https://manhuarmtl.com",
    language.lang,
),
    ConfigurableSource {

    override val useNewChapterEndpoint: Boolean = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    /**
     * A flag that tracks whether the settings have been changed. It is used to indicate if
     * any configuration change has occurred. Once the value is accessed, it resets to `false`.
     * This is useful for tracking whether a preference has been modified, and ensures that
     * the change status is cleared after it has been accessed, to prevent multiple triggers.
     */
    private var isSettingsChanged: Boolean = false
        get() {
            val current = field
            field = false
            return current
        }

    private var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()

    private var dialogBoxScale: Float
        get() = preferences.getString(DIALOG_BOX_SCALE_PREF, language.dialogBoxScale.toString())!!.toFloat()
        set(value) = preferences.edit().putString(DIALOG_BOX_SCALE_PREF, value.toString()).apply()

    private var fontName: String
        get() = preferences.getString(FONT_NAME_PREF, language.fontName)!!
        set(value) = preferences.edit().putString(FONT_NAME_PREF, value).apply()

    private var disableWordBreak: Boolean
        get() = preferences.getBoolean(DISABLE_WORD_BREAK_PREF, language.disableWordBreak)
        set(value) = preferences.edit().putBoolean(DISABLE_WORD_BREAK_PREF, value).apply()

    private var disableTranslator: Boolean
        get() = preferences.getBoolean(DISABLE_TRANSLATOR_PREF, language.disableTranslator)
        set(value) = preferences.edit().putBoolean(DISABLE_TRANSLATOR_PREF, value).apply()

    private var customUserAgent: String
        get() = preferences.getString(CUSTOM_UA_PREF, "")!!
        set(value) = preferences.edit().putString(CUSTOM_UA_PREF, value).apply()

    private val i18n = Intl(
        language = language.lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es", "fr", "id", "it", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { createDefaultMessageFileName("${name.lowercase()}_$it") },
    )

    private val settings get() = language.copy(
        fontSize = this@Manhuarm.fontSize,
        fontName = this@Manhuarm.fontName,
        dialogBoxScale = this@Manhuarm.dialogBoxScale,
        disableWordBreak = this@Manhuarm.disableWordBreak,
        disableTranslator = this@Manhuarm.disableTranslator,
        disableFontSettings = this@Manhuarm.fontName == DEVICE_FONT,
    )

    override val client: OkHttpClient get() = clientInstance!!

    private val translators = arrayOf(
        "Bing",
        "Google",
    )

    private val provider: String get() =
        preferences.getString(TRANSLATOR_PROVIDER_PREF, translators.first())!!

    private val warmupInterceptor = CloudflareWarmupInterceptor(baseUrl, headers)

    /**
     * This ensures that the `OkHttpClient` instance is only created when required, and it is rebuilt
     * when there are configuration changes to ensure that the client uses the most up-to-date settings.
     */
    private var clientInstance: OkHttpClient? = null
        get() {
            if (field == null || isSettingsChanged) {
                warmupInterceptor.reset()
                field = clientBuilder().build()
            }
            return field
        }

    private val clientUtils = network.cloudflareClient.newBuilder()
        .rateLimit(3, 2, TimeUnit.SECONDS)
        .build()

    private lateinit var translator: TranslatorEngine

    private fun clientBuilder(): OkHttpClient.Builder {
        translator = when (provider) {
            "Google" -> GoogleTranslator(clientUtils, headers)
            else -> BingTranslator(clientUtils, headers)
        }

        return network.cloudflareClient.newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .rateLimit(2, 1)
            // Fix disk cache / decompression issues
            .apply {
                val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
                if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
            }
            .addInterceptor(warmupInterceptor)
            .addInterceptorIf(
                !disableTranslator && language.lang != language.origin,
                TranslationInterceptor(settings, translator),
            )
            .addInterceptor(ComposedImageInterceptor(settings))
    }

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
        val ua = customUserAgent.trim()
        if (ua.isNotEmpty()) {
            builder["User-Agent"] = ua
        }
        return builder
    }

    private fun OkHttpClient.Builder.addInterceptorIf(condition: Boolean, interceptor: Interceptor): OkHttpClient.Builder = this.takeIf { condition.not() } ?: this.addInterceptor(interceptor)

    private val translationAvailability = Calendar.getInstance().apply {
        set(2025, Calendar.SEPTEMBER, 9, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // =========================== Popular ==========================================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/manga/?m_orderby=trending"
        } else {
            "$baseUrl/manga/page/$page/?m_orderby=trending"
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector(): String = ".page-item-detail, .manga-card"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleEl = element.selectFirst(".post-title a, .manga-title a")
        val thumbEl = element.selectFirst(".item-thumb img, .manga-thumb img, img")
        manga.setUrlWithoutDomain(titleEl!!.attr("href"))
        manga.title = titleEl.text()
        manga.thumbnail_url = thumbEl?.extractCoverUrl()
        return manga
    }

    override fun popularMangaNextPageSelector(): String = "a.next, a.nextpostslink, .pagination a.next, .navigation-ajax #navigation-ajax"

    // =========================== Latest ==========================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/manga/?m_orderby=latest"
        } else {
            "$baseUrl/manga/page/$page/?m_orderby=latest"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleEl = element.selectFirst(".manga-title a")
            ?: element.selectFirst(".post-title a, h3.h5 a, .post-title h3 a")
        val thumbEl = element.selectFirst(".manga-thumb img")
            ?: element.selectFirst(".item-thumb img, img")
        manga.setUrlWithoutDomain(titleEl!!.attr("href"))
        manga.title = titleEl.text()
        manga.thumbnail_url = thumbEl?.extractCoverUrl()
        return manga
    }

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // =========================== Details ==========================================

    /**
     * Extracts the cover image URL from an image element, checking multiple attributes
     * to handle lazy loading and different image formats.
     */
    private fun Element?.extractCoverUrl(): String? {
        if (this == null) return null

        // Try data-src first (lazy loading)
        absUrl("data-src").takeIf { it.isNotBlank() && !it.contains("data:image") }?.let { return it }

        // Try src attribute
        absUrl("src").takeIf { it.isNotBlank() && !it.contains("data:image") && !it.contains("placeholder") }?.let { return it }

        // Try srcset attribute (parse first URL)
        attr("srcset").takeIf { it.isNotBlank() }?.let { srcset ->
            srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()?.let { url ->
                if (url.startsWith("http")) {
                    return url
                } else {
                    absUrl(url).takeIf { it.isNotBlank() && !it.contains("data:image") }?.let { return it }
                }
            }
        }

        return null
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        // Ensure cover is always set from detail page if it wasn't set from listing
        if (manga.thumbnail_url.isNullOrBlank()) {
            val coverEl = document.selectFirst(".summary_image img, .wp-post-image, .item-thumb img, .manga-thumb img, img.wp-post-image")
            manga.thumbnail_url = coverEl?.extractCoverUrl()
        } else {
            // Even if cover was set, try to get a better quality version from detail page
            val coverEl = document.selectFirst(".summary_image img, .wp-post-image, .item-thumb img, .manga-thumb img, img.wp-post-image")
            coverEl?.extractCoverUrl()?.let {
                if (it.isNotBlank() && !it.contains("placeholder")) {
                    manga.thumbnail_url = it
                }
            }
        }

        return manga
    }

    // =========================== Chapters =======================================

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).filter {
        language.target == language.origin || Date(it.date_upload).after(translationAvailability.time)
    }

    // =========================== Pages ==========================================

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
        val chapterUrl = document.location().toHttpUrl().newBuilder()
            .removeAllQueryParameters("style")
            .build()

        // Use minimal headers for JSON request - Cloudflare may be blocking complex requests
        val jsonHeaders = Headers.Builder()
            .add("Referer", chapterUrl.toString())
            .add("Accept", "*/*")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Cache-Control", "no-cache")
            .build()

        val ocrData = Regex("""_0xdata\s*=\s*(\{.*?\});""")
            .find(document.html())
            ?.groupValues
            ?.get(1)
            ?.parseAs<OcrDataDto>()
            ?: return pages

        val ocrUrl = ocrData.let {
            val ch = String(Base64.getDecoder().decode(it.a))
            it.e.toHttpUrl().newBuilder()
                .addQueryParameter("ch", ch)
                .addQueryParameter("tk", it.b)
                .addQueryParameter("ts", it.c.toString())
                .addQueryParameter("nc", it.d)
                .build().toString()
        }

        val dialog = try {
            val response = client.newCall(GET(ocrUrl, jsonHeaders)).execute()

            // If server returns error (403, etc), skip translations
            if (!response.isSuccessful) {
                response.close()
                emptyList()
            } else {
                response.parseAs<List<PageDto>>()
            }
        } catch (_: Exception) {
            // If JSON parsing fails, skip translations
            emptyList()
        }

        if (dialog.isEmpty()) {
            return pages
        }

        return dialog.mapIndexed { index, dto ->
            val page = pages.first { it.imageUrl?.contains(dto.imageUrl, true)!! }
            val fragment = json.encodeToString<List<Dialog>>(
                dto.dialogues.filter { it.getTextBy(language).isNotBlank() },
            )
            if (dto.dialogues.isEmpty()) {
                return@mapIndexed page
            }

            Page(index, imageUrl = "${page.imageUrl}${fragment.toFragment()}")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
            .set("Referer", "$baseUrl/")
            .set("Connection", "keep-alive")
            .set("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
            .set("Accept-Encoding", "gzip, deflate, br, zstd")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "cross-site")
            .set("Sec-Fetch-Storage-Access", "none")
            .set("Priority", "u=5, i")
            .set("TE", "trailers")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // ================================ Utils ============================================

    // Prevent bad fragments
    fun String.toFragment(): String = "#${this.replace("#", "*")}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Some libreoffice font sizes
        val sizes = arrayOf(
            "12", "13", "14",
            "15", "16", "18",
            "20", "21", "22",
            "24", "26", "28",
            "32", "36", "40",
            "42", "44", "48",
            "54", "60", "72",
            "80", "88", "96",
        )

        val scale = (0..10).map { 1f + it / 10f }.toTypedArray()

        val fonts = arrayOf(
            i18n["font_name_device_title"] to DEVICE_FONT,
            "Anime Ace" to "animeace2_regular",
            "Comic Neue" to "comic_neue_bold",
            "Coming Soon" to "coming_soon_regular",
        )

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = i18n["font_size_title"]
            entries = sizes.map {
                "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - ${i18n["default_font_size"]}" else ""
            }.toTypedArray()
            entryValues = sizes

            summary = buildString {
                appendLine(i18n["font_size_summary"])
                append("\t* %s")
            }

            setDefaultValue(fontSize.toString())

            setOnPreferenceChange { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                fontSize = selected.toInt()

                Toast.makeText(
                    screen.context,
                    i18n["font_size_message"].format(entry),
                    Toast.LENGTH_LONG,
                ).show()

                true // It's necessary to update the user interface
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = DIALOG_BOX_SCALE_PREF
            title = i18n["dialog_box_scale_title"]
            entries = scale.map {
                "${it}x" + if (it == 1f) " - ${i18n["dialog_box_scale_default"]}" else ""
            }.toTypedArray()
            entryValues = scale.map(Float::toString).toTypedArray()

            summary = buildString {
                appendLine(i18n["dialog_box_scale_summary"])
                append("\t* %s")
            }

            setDefaultValue(dialogBoxScale.toString())

            setOnPreferenceChange { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                dialogBoxScale = selected.toFloat()

                Toast.makeText(
                    screen.context,
                    i18n["dialog_box_scale_message"].format(entry),
                    Toast.LENGTH_LONG,
                ).show()

                true // It's necessary to update the user interface
            }
        }.also(screen::addPreference)

        if (!language.disableFontSettings) {
            ListPreference(screen.context).apply {
                key = FONT_NAME_PREF
                title = i18n["font_name_title"]
                entries = fonts.map {
                    it.first + if (it.second.isBlank()) " - ${i18n["default_font_name"]}" else ""
                }.toTypedArray()
                entryValues = fonts.map { it.second }.toTypedArray()
                summary = buildString {
                    appendLine(i18n["font_name_summary"])
                    append("\t* %s")
                }

                setDefaultValue(fontName)

                setOnPreferenceChange { _, newValue ->
                    val selected = newValue as String
                    val index = this.findIndexOfValue(selected)
                    val entry = entries[index] as String

                    fontName = selected

                    Toast.makeText(
                        screen.context,
                        i18n["font_name_message"].format(entry),
                        Toast.LENGTH_LONG,
                    ).show()

                    true // It's necessary to update the user interface
                }
            }.also(screen::addPreference)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = DISABLE_WORD_BREAK_PREF
            title = "⚠ ${i18n["disable_word_break_title"]}"
            summary = i18n["disable_word_break_summary"]
            setDefaultValue(language.disableWordBreak)
            setOnPreferenceChange { _, newValue ->
                disableWordBreak = newValue as Boolean
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = CUSTOM_UA_PREF
            title = i18n["custom_user_agent_title"]
            summary = i18n["custom_user_agent_message"]
            setDefaultValue(customUserAgent)
            setOnPreferenceChange { _, newValue ->
                customUserAgent = (newValue as String).trim()
                true
            }
        }.also(screen::addPreference)

        if (language.target == language.origin) {
            return
        }

        if (language.supportNativeTranslation) {
            SwitchPreferenceCompat(screen.context).apply {
                key = DISABLE_TRANSLATOR_PREF
                title = "⚠ ${i18n["disable_translator_title"]}"
                summary = i18n["disable_translator_summary"]
                setDefaultValue(language.disableTranslator)
                setOnPreferenceChange { _, newValue ->
                    disableTranslator = newValue as Boolean
                    true
                }
            }.also(screen::addPreference)
        }

        if (!disableTranslator) {
            ListPreference(screen.context).apply {
                key = TRANSLATOR_PROVIDER_PREF
                title = i18n["translate_dialog_box_title"]
                entries = translators
                entryValues = translators
                summary = buildString {
                    appendLine(i18n["translate_dialog_box_summary"])
                    append("\t* %s")
                }

                setDefaultValue(translators.first())

                setOnPreferenceChange { _, newValue ->
                    val selected = newValue as String
                    val index = this.findIndexOfValue(selected)
                    val entry = entries[index] as String

                    Toast.makeText(
                        screen.context,
                        "${i18n["translate_dialog_box_toast"]} '$entry'",
                        Toast.LENGTH_LONG,
                    ).show()

                    true
                }
            }.also(screen::addPreference)
        }
    }

    /**
     * Sets an `OnPreferenceChangeListener` for the preference, and before triggering the original listener,
     * marks that the configuration has changed by setting `isSettingsChanged` to `true`.
     * This behavior is useful for applying runtime configurations in the HTTP client,
     * ensuring that the preference change is registered before invoking the original listener.
     */
    private fun Preference.setOnPreferenceChange(onPreferenceChangeListener: Preference.OnPreferenceChangeListener) {
        setOnPreferenceChangeListener { preference, newValue ->
            isSettingsChanged = true
            onPreferenceChangeListener.onPreferenceChange(preference, newValue)
        }
    }

    companion object {
        val PAGE_REGEX = Regex(".*?\\.(webp|png|jpg|jpeg)#\\[.*?]", RegexOption.IGNORE_CASE)

        const val DEVICE_FONT = "device:"
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val FONT_NAME_PREF = "fontNamePref"
        private const val DIALOG_BOX_SCALE_PREF = "dialogBoxScalePref"
        private const val DISABLE_WORD_BREAK_PREF = "disableWordBreakPref"
        private const val DISABLE_TRANSLATOR_PREF = "disableTranslatorPref"
        private const val TRANSLATOR_PROVIDER_PREF = "translatorProviderPref"
        private const val CUSTOM_UA_PREF = "customUserAgentPref"
        private const val DEFAULT_FONT_SIZE = "28"
    }
}
