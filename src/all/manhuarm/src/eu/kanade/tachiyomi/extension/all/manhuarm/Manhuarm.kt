package eu.kanade.tachiyomi.extension.all.manhuarm

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing.BingTranslator
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.google.GoogleTranslator
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.lib.i18n.Intl.Companion.createDefaultMessageFileName
import eu.kanade.tachiyomi.multisrc.machinetranslations.translator.TranslatorEngine
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
class Manhuarm(
    private val language: Language,
) : Madara(
    "Manhuarm",
    "https://manhuarm.com",
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

    private var fontName: String
        get() = preferences.getString(FONT_NAME_PREF, language.fontName)!!
        set(value) = preferences.edit().putString(FONT_NAME_PREF, value).apply()

    private var disableWordBreak: Boolean
        get() = preferences.getBoolean(DISABLE_WORD_BREAK_PREF, language.disableWordBreak)
        set(value) = preferences.edit().putBoolean(DISABLE_WORD_BREAK_PREF, value).apply()

    private var disableTranslator: Boolean
        get() = preferences.getBoolean(DISABLE_TRANSLATOR_PREF, language.disableTranslator)
        set(value) = preferences.edit().putBoolean(DISABLE_TRANSLATOR_PREF, value).apply()

    private val i18n = Intl(
        language = language.lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es", "fr", "id", "it", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { createDefaultMessageFileName("${name.lowercase()}_${language.lang}") },
    )

    private val settings get() = language.copy(
        fontSize = this@Manhuarm.fontSize,
        fontName = this@Manhuarm.fontName,
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

    /**
     * This ensures that the `OkHttpClient` instance is only created when required, and it is rebuilt
     * when there are configuration changes to ensure that the client uses the most up-to-date settings.
     */
    private var clientInstance: OkHttpClient? = null
        get() {
            if (field == null || isSettingsChanged) {
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
            .rateLimit(3)
            .addInterceptorIf(
                !disableTranslator && language.lang != language.origin,
                TranslationInterceptor(settings, translator),
            )
            .addInterceptor(ComposedImageInterceptor(settings))
    }

    private fun OkHttpClient.Builder.addInterceptorIf(condition: Boolean, interceptor: Interceptor): OkHttpClient.Builder {
        return this.takeIf { condition.not() } ?: this.addInterceptor(interceptor)
    }

    private val translationAvailability = Calendar.getInstance().apply {
        set(2025, Calendar.SEPTEMBER, 9, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).filter {
            language.target == language.origin || Date(it.date_upload).after(translationAvailability.time)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
        val content = document.selectFirst("meta[name=description]")
            ?.attr("content")
            ?.fixJsonFormat()
            ?: return pages.takeIf { language.target == language.origin } ?: throw Exception(i18n["chapter_unavailable_message"])

        val dialog = content.parseAs<List<PageDto>>()

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

    private fun String.fixJsonFormat(): String {
        return JSON_FORMAT_REGEX.replace(this) { matchResult ->
            val content = matchResult.groupValues.last()
            val modifiedContent = content.replace("\"", "'")
            """"text": "${modifiedContent.trimIndent()}", "box""""
        }
    }

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
        val JSON_FORMAT_REGEX = """(?:"text":\s+?".*?)([\s\S]*?)(?:",\s+?"box")""".toRegex()

        const val DEVICE_FONT = "device:"
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val FONT_NAME_PREF = "fontNamePref"
        private const val DISABLE_WORD_BREAK_PREF = "disableWordBreakPref"
        private const val DISABLE_TRANSLATOR_PREF = "disableTranslatorPref"
        private const val TRANSLATOR_PROVIDER_PREF = "translatorProviderPref"
        private const val DEFAULT_FONT_SIZE = "28"
    }
}
