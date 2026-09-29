package eu.kanade.tachiyomi.extension.all.manhuarm

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.TranslatorEngine
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing.BingTranslator
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.google.GoogleTranslator
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.lib.i18n.Intl.Companion.createDefaultMessageFileName
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manhuarm :
    KeiSource(),
    ConfigurableSource {

    private val language: Language
        get() = when (lang) {
            "ar" -> Language(lang, disableFontSettings = true)
            "fr", "id" -> Language(lang, supportNativeTranslation = true)
            "pt-BR" -> Language(lang, "pt", supportNativeTranslation = true)
            else -> Language(lang)
        }

    private val preferences by getPreferencesLazy()

    private val fontSize get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
    private val dialogBoxScale get() = preferences.getString(DIALOG_BOX_SCALE_PREF, language.dialogBoxScale.toString())!!.toFloat()
    private val fontName get() = preferences.getString(FONT_NAME_PREF, language.fontName)!!
    private val disableWordBreak get() = preferences.getBoolean(DISABLE_WORD_BREAK_PREF, language.disableWordBreak)
    private val disableTranslator get() = preferences.getBoolean(DISABLE_TRANSLATOR_PREF, language.disableTranslator)
    private val translateSynopsis get() = preferences.getBoolean(TRANSLATE_SYNOPSIS_PREF, language.translateSynopsis)
    private val customUserAgent get() = preferences.getString(CUSTOM_UA_PREF, "")!!.trim()

    private val settings: Language
        get() = language.copy(
            fontSize = fontSize,
            fontName = fontName,
            dialogBoxScale = dialogBoxScale,
            disableWordBreak = disableWordBreak,
            disableTranslator = disableTranslator,
            translateSynopsis = translateSynopsis,
            disableFontSettings = fontName == DEVICE_FONT,
        )

    private val bingTranslator by lazy { BingTranslator(client) { headers } }
    private val googleTranslator by lazy { GoogleTranslator(client) { headers } }

    private val translator: TranslatorEngine
        get() = when (preferences.getString(TRANSLATOR_PROVIDER_PREF, TRANSLATORS.first())) {
            "Google" -> googleTranslator
            else -> bingTranslator
        }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(1.minutes)
        .readTimeout(2.minutes)
        .addInterceptor(TranslationInterceptor({ settings }, { translator }))
        .addInterceptor(ComposedImageInterceptor { settings })
        .rateLimit(3, 2.seconds) { it.host == BingTranslator.HOST || it.host == GoogleTranslator.HOST }
        .rateLimit(2)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        customUserAgent.takeIf(String::isNotEmpty)?.let { set("User-Agent", it) }
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = archive(page, "trending")

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = archive(page, "latest")

    private suspend fun archive(page: Int, sort: String): MangasPage {
        val path = if (page == 1) "/manga/" else "/manga/page/$page/"
        val document = client.get("$baseUrl$path?sort=$sort").asJsoup()
        return MangasPage(document.parseMangaList(), document.selectFirst("a.next") != null)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("pg", page.toString())
        }.build()
        val document = client.get(url).asJsoup()
        return MangasPage(document.parseMangaList(), document.selectFirst("a.mrm-pager__btn[rel=next]") != null)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.takeIf { it.size >= 2 && it[0] == "manga" }?.get(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return client.get(getMangaUrl(manga)).asJsoup().parseDetails().apply { this.url = manga.url }
    }

    private fun Document.parseMangaList(): List<SManga> = select(".mrm-results__grid .mrm-r-item").map { element ->
        val link = element.selectFirst("a.mrm-r-item__link")!!
        SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = element.selectFirst(".mrm-r-item__title")?.text() ?: link.attr("title")
            thumbnail_url = element.selectFirst(".mrm-r-item__art img")?.imageUrl()
        }
    }

    // =============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = document.parseDetails().apply { url = manga.url }

        val language = settings
        if (fetchDetails && language.translateSynopsis && language.target != language.origin) {
            details.description = details.description?.let { translator.translate(language.origin, language.target, it) }
        }

        val chapterList = document.select("li.wp-manga-chapter").map { element ->
            val link = element.selectFirst("a")!!
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                date_upload = parseChapterDate(element.selectFirst(".chapter-release-date")?.text())
            }
        }.filter { language.target == language.origin || it.date_upload > TRANSLATION_AVAILABILITY }

        return SMangaUpdate(details, chapterList)
    }

    private fun Document.parseDetails() = SManga.create().apply {
        title = selectFirst("h1.mrm-hero__title")!!.text()
        thumbnail_url = selectFirst(".mrm-hero__cover img")?.imageUrl()
        author = select(".author-content a").eachText().joinToString().ifEmpty { null }
        artist = select(".artist-content a").eachText().joinToString().ifEmpty { null }
        genre = buildList {
            addAll(select(".mrm-genres__list a").eachText())
            addAll(select(".tags-content a").eachText())
            summaryContent("Type")?.let(::add)
        }.distinctBy(String::lowercase).joinToString().ifEmpty { null }
        description = buildString {
            selectFirst(".summary__content")?.let { summary ->
                append(summary.select("p").takeIf { it.isNotEmpty() }?.joinToString("\n\n") { it.text() } ?: summary.text())
            }
            selectFirst("#mrm-hero-alt-text")?.text()?.takeIf(String::isNotEmpty)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative names: ", it)
            }
        }.ifEmpty { null }
        status = when (summaryContent("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "canceled" -> SManga.CANCELLED
            "on hold" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.summaryContent(heading: String): String? = select(".post-content_item")
        .firstOrNull { it.selectFirst(".summary-heading")?.text() == heading }
        ?.selectFirst(".summary-content")?.text()

    private fun parseChapterDate(date: String?): Long {
        val value = date?.lowercase() ?: return 0L
        if (value.endsWith(" ago")) {
            val amount = value.substringBefore(" ").toLongOrNull() ?: return 0L
            val unit = when {
                "min" in value -> ChronoUnit.MINUTES
                "hour" in value -> ChronoUnit.HOURS
                "day" in value -> ChronoUnit.DAYS
                "week" in value -> ChronoUnit.WEEKS
                "month" in value -> ChronoUnit.MONTHS
                "year" in value -> ChronoUnit.YEARS
                else -> ChronoUnit.SECONDS
            }
            return ZonedDateTime.now(ZoneOffset.UTC).minus(amount, unit).toInstant().toEpochMilli()
        }
        return DATE_FORMAT.tryParseDate(date)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        // The OCR credentials embedded in the page are single-use
        val document = client.get(chapterUrl, cacheControl = CacheControl.FORCE_NETWORK).asJsoup()
        val images = document.select("div.page-break img, img.wp-manga-chapter-img")
            .mapNotNull { it.imageUrl() }
            .distinct()

        val dialogues = fetchDialogues(document, chapterUrl).associateBy(PageDto::imageUrl)
        val language = settings

        return images.mapIndexed { index, imageUrl ->
            val dialogs = dialogues[imageUrl.substringAfterLast('/')]
                ?.dialogues
                ?.filter { it.getTextBy(language).isNotBlank() }
                .orEmpty()
            if (dialogs.isEmpty()) {
                Page(index, imageUrl = imageUrl)
            } else {
                // '#' would end the fragment early
                Page(index, imageUrl = "$imageUrl#${dialogs.toJsonString().replace("#", "*")}")
            }
        }
    }

    /**
     * The chapter page embeds the OCR endpoint and its gate credentials in a `_0xvault` array:
     * `[cid, token, timestamp, nonce, endpoint, ref]`.
     */
    private suspend fun fetchDialogues(document: Document, chapterUrl: String): List<PageDto> {
        val vault = document.select("script").firstNotNullOfOrNull { VAULT_REGEX.find(it.data()) }
            ?.groupValues?.get(1)
            ?.parseAs<List<JsonPrimitive>>()
            ?.map(JsonPrimitive::content)
            ?.takeIf { it.size > 5 && it[4].contains("fetch-ocr") }
            ?: return emptyList()

        val ocrHeaders = headers.newBuilder()
            .set("Referer", chapterUrl)
            .set("Accept", "*/*")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Cache-Control", "no-cache")
            .set("X-Gate-Token", vault[1])
            .set("X-Gate-Timestamp", vault[2])
            .set("X-Gate-Nonce", vault[3])
            .build()

        // Pages are still readable without the translation overlay
        return try {
            client.post(vault[4], ocrHeaders, OcrRequestDto(vault[0], vault[5]).toJsonRequestBody())
                .parseAs<List<PageDto>>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =============================== Utils ================================

    private fun Element.imageUrl(): String? {
        val key = listOf("data-src", "data-lazy-src", "src").firstOrNull { attr(it).isNotBlank() } ?: return null
        val value = attr(key).trim()
        return when {
            value.startsWith("data:") -> null
            value.startsWith("http") -> value
            else -> absUrl(key)
        }
    }

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val language = language
        val i18n = Intl(
            language = language.lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "es", "fr", "id", "it", "pt-BR"),
            classLoader = this::class.java.classLoader!!,
            createMessageFileName = { createDefaultMessageFileName("${name.lowercase()}_$it") },
        )

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

        val scale = (0..10).map { 1f + it / 10f }

        val fonts = arrayOf(
            i18n["font_name_device_title"] to DEVICE_FONT,
            "Anime Ace" to "animeace2_regular",
            "Comic Neue" to "comic_neue_bold",
            "Coming Soon" to "coming_soon_regular",
        )

        fun ListPreference.toastOnChange(message: (String) -> String) {
            setOnPreferenceChangeListener { _, newValue ->
                val entry = entries[findIndexOfValue(newValue as String)] as String
                Toast.makeText(screen.context, message(entry), Toast.LENGTH_LONG).show()
                true
            }
        }

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = i18n["font_size_title"]
            entries = sizes.map {
                "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - ${i18n["default_font_size"]}" else ""
            }.toTypedArray()
            entryValues = sizes
            summary = "${i18n["font_size_summary"]}\n\t* %s"
            setDefaultValue(DEFAULT_FONT_SIZE)
            toastOnChange { i18n["font_size_message"].format(it) }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = DIALOG_BOX_SCALE_PREF
            title = i18n["dialog_box_scale_title"]
            entries = scale.map {
                "${it}x" + if (it == 1f) " - ${i18n["dialog_box_scale_default"]}" else ""
            }.toTypedArray()
            entryValues = scale.map(Float::toString).toTypedArray()
            summary = "${i18n["dialog_box_scale_summary"]}\n\t* %s"
            setDefaultValue(language.dialogBoxScale.toString())
            toastOnChange { i18n["dialog_box_scale_message"].format(it) }
        }.also(screen::addPreference)

        if (!language.disableFontSettings) {
            ListPreference(screen.context).apply {
                key = FONT_NAME_PREF
                title = i18n["font_name_title"]
                entries = fonts.map {
                    it.first + if (it.second == language.fontName) " - ${i18n["default_font_name"]}" else ""
                }.toTypedArray()
                entryValues = fonts.map { it.second }.toTypedArray()
                summary = "${i18n["font_name_summary"]}\n\t* %s"
                setDefaultValue(language.fontName)
                toastOnChange { i18n["font_name_message"].format(it) }
            }.also(screen::addPreference)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = DISABLE_WORD_BREAK_PREF
            title = "⚠ ${i18n["disable_word_break_title"]}"
            summary = i18n["disable_word_break_summary"]
            setDefaultValue(language.disableWordBreak)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = CUSTOM_UA_PREF
            title = i18n["custom_user_agent_title"]
            summary = i18n["custom_user_agent_message"]
            setDefaultValue("")
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
            }.also(screen::addPreference)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = TRANSLATE_SYNOPSIS_PREF
            title = i18n["translate_synopsis_title"]
            summary = i18n["translate_synopsis_summary"]
            setDefaultValue(language.translateSynopsis)
        }.also(screen::addPreference)

        if (!disableTranslator || translateSynopsis) {
            ListPreference(screen.context).apply {
                key = TRANSLATOR_PROVIDER_PREF
                title = i18n["translate_dialog_box_title"]
                entries = TRANSLATORS
                entryValues = TRANSLATORS
                summary = "${i18n["translate_dialog_box_summary"]}\n\t* %s"
                setDefaultValue(TRANSLATORS.first())
                toastOnChange { "${i18n["translate_dialog_box_toast"]} '$it'" }
            }.also(screen::addPreference)
        }
    }

    companion object {
        val PAGE_REGEX = Regex(".*?\\.(webp|png|jpg|jpeg)#\\[.*?]", RegexOption.IGNORE_CASE)

        private val VAULT_REGEX = Regex("""_0xvault\s*=\s*(\[.*?])""", RegexOption.DOT_MATCHES_ALL)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

        // Machine translations are only available for chapters released after 2025-09-09
        private const val TRANSLATION_AVAILABILITY = 1757376000000L

        private val TRANSLATORS = arrayOf("Bing", "Google")

        const val DEVICE_FONT = "device:"
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val FONT_NAME_PREF = "fontNamePref"
        private const val DIALOG_BOX_SCALE_PREF = "dialogBoxScalePref"
        private const val DISABLE_WORD_BREAK_PREF = "disableWordBreakPref"
        private const val DISABLE_TRANSLATOR_PREF = "disableTranslatorPref"
        private const val TRANSLATE_SYNOPSIS_PREF = "translateSynopsisPref"
        private const val TRANSLATOR_PROVIDER_PREF = "translatorProviderPref"
        private const val CUSTOM_UA_PREF = "customUserAgentPref"
        private const val DEFAULT_FONT_SIZE = "28"
    }
}
