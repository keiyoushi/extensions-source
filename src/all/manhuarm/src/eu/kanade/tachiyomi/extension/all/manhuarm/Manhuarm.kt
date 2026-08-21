package eu.kanade.tachiyomi.extension.all.manhuarm

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.CloudflareWarmupInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.OcrRequest
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.OcrUrlInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.interceptors.TranslationInterceptor
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing.BingTranslator
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.google.GoogleTranslator
import eu.kanade.tachiyomi.multisrc.machinetranslations.translator.TranslatorEngine
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manhuarm :
    KeiSource(),
    ConfigurableSource {
    private val language: Language by lazy {
        when (lang) {
            "all" -> Language(lang, "en")
            "ar" -> Language(lang, disableFontSettings = true)
            "fr", "id" -> Language(lang, supportNativeTranslation = true)
            "pt-BR" -> Language(lang, "pt", supportNativeTranslation = true)
            else -> Language(lang)
        }
    }

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var fontSize: Int
        get() = (preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE) ?: DEFAULT_FONT_SIZE).toInt()
        set(value) {
            preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()
        }

    private var dialogBoxScale: Float
        get() =
            (
                preferences.getString(DIALOG_BOX_SCALE_PREF, language.dialogBoxScale.toString())
                    ?: language.dialogBoxScale.toString()
                ).toFloat()
        set(value) {
            preferences.edit().putString(DIALOG_BOX_SCALE_PREF, value.toString()).apply()
        }

    private var fontName: String
        get() = preferences.getString(FONT_NAME_PREF, language.fontName) ?: language.fontName
        set(value) {
            preferences.edit().putString(FONT_NAME_PREF, value).apply()
        }

    private var disableWordBreak: Boolean
        get() = preferences.getBoolean(DISABLE_WORD_BREAK_PREF, language.disableWordBreak)
        set(value) {
            preferences.edit().putBoolean(DISABLE_WORD_BREAK_PREF, value).apply()
        }

    private var disableTranslator: Boolean
        get() = preferences.getBoolean(DISABLE_TRANSLATOR_PREF, language.disableTranslator)
        set(value) {
            preferences.edit().putBoolean(DISABLE_TRANSLATOR_PREF, value).apply()
        }

    private var translateSynopsis: Boolean
        get() = preferences.getBoolean(TRANSLATE_SYNOPSIS_PREF, language.translateSynopsis)
        set(value) {
            preferences.edit().putBoolean(TRANSLATE_SYNOPSIS_PREF, value).apply()
        }

    private var customUserAgent: String
        get() = preferences.getString(CUSTOM_UA_PREF, "") ?: ""
        set(value) {
            preferences.edit().putString(CUSTOM_UA_PREF, value).apply()
        }

    private val settings
        get() =
            language.copy(
                fontSize = fontSize,
                fontName = fontName,
                dialogBoxScale = dialogBoxScale,
                disableWordBreak = disableWordBreak,
                disableTranslator = disableTranslator,
                translateSynopsis = translateSynopsis,
                disableFontSettings = fontName == DEVICE_FONT,
            )

    private val translators = arrayOf("Bing", "Google")

    private val provider: String
        get() = preferences.getString(TRANSLATOR_PROVIDER_PREF, translators.first()) ?: translators.first()

    // Cookie jar to persist Cloudflare cf_clearance cookies across requests.
    // Cookies are keyed by (name, domain, path) so refreshed values replace stale
    // ones without collapsing distinct scoped cookies. A single flat store is used
    // because matches(url) already scopes cookies by domain/path/secure.
    private val cookieJar =
        object : CookieJar {
            private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, Cookie>()

            override fun saveFromResponse(
                url: HttpUrl,
                cookies: List<Cookie>,
            ) {
                if (cookies.isEmpty()) return
                val now = System.currentTimeMillis()
                cookies.forEach { cookie ->
                    if (cookie.expiresAt > now) {
                        cookieStore[cookie.key()] = cookie
                    } else {
                        cookieStore.remove(cookie.key())
                    }
                }
                dropExpired(now)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                dropExpired(System.currentTimeMillis())
                return cookieStore.values.filter { it.matches(url) }
            }

            private fun dropExpired(now: Long) {
                cookieStore.entries.removeAll { (_, cookie) -> cookie.expiresAt <= now }
            }

            private fun Cookie.key(): String = "$name|$domain|$path"
        }

    private val warmupInterceptor by lazy { CloudflareWarmupInterceptor(baseUrl, headers) }
    private val ocrUrlInterceptor by lazy { OcrUrlInterceptor(headers) }

    private val clientUtils by lazy {
        network.client.newBuilder()
            .rateLimit(3, 2.seconds)
            .build()
    }

    private val translator: TranslatorEngine by lazy {
        when (provider) {
            "Google" -> GoogleTranslator(clientUtils, headers)
            else -> BingTranslator(clientUtils, headers)
        }
    }

    // =========================== Client / Headers ================================

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .addInterceptor(warmupInterceptor)
        .addInterceptorIf(
            !disableTranslator && language.target != language.origin,
            TranslationInterceptor(::settings, translator),
        )
        .addInterceptor(ComposedImageInterceptor(::settings))
        .rateLimit(2, 1.seconds)

    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        val ua =
            customUserAgent.trim().ifEmpty {
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            }
        return set("User-Agent", ua)
            .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .set("Accept-Language", "en-US,en;q=0.9")
            .set("Referer", "$baseUrl/")
    }

    private fun OkHttpClient.Builder.addInterceptorIf(
        condition: Boolean,
        interceptor: Interceptor,
    ): OkHttpClient.Builder = if (condition) {
        addInterceptor(interceptor)
    } else {
        this
    }

    // =========================== URL Helpers =====================================

    override fun getMangaUrl(manga: SManga): String = when {
        manga.url.startsWith("http") -> manga.url
        manga.url.startsWith("/") -> baseUrl + manga.url
        else -> "$baseUrl/manga/${manga.url.trim('/')}/"
    }

    override fun getChapterUrl(chapter: SChapter): String = when {
        chapter.url.startsWith("http") -> chapter.url
        chapter.url.startsWith("/") -> baseUrl + chapter.url
        else -> "$baseUrl/${chapter.url.trim('/')}/"
    }

    // =========================== Popular / Latest / Search =======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url =
            if (page == 1) {
                "$baseUrl/manga/?m_orderby=trending"
            } else {
                "$baseUrl/manga/page/$page/?m_orderby=trending"
            }
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url =
            if (page == 1) {
                "$baseUrl/manga/?m_orderby=latest"
            } else {
                "$baseUrl/manga/page/$page/?m_orderby=latest"
            }
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url =
            "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
                addQueryParameter("s", query)
                addQueryParameter("post_type", "wp-manga")
                if (page > 1) {
                    addQueryParameter("pg", page.toString())
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
            }.build()
        return parseMangaList(client.get(url).asJsoup())
    }

    private fun parseMangaList(doc: Document): MangasPage {
        // Cloudflare challenge pages check
        if (
            doc.selectFirst(
                "#challenge-form, .cf-browser-verification, title:contains(Just a moment), " +
                    "title:contains(Attention Required)",
            ) != null
        ) {
            return MangasPage(emptyList(), false)
        }

        val mangas = doc.select(
            "li.mrm-r-item, .page-item-detail, .manga-card, .c-tabs-item__content, " +
                ".row > div.col-6 > div.item, .page-listing-item, .manga, " +
                ".badge-pos-2, .item-summary, div.post-title",
        ).mapNotNull { el -> mangaFromElement(el) }.distinctBy { it.url }

        val hasNext = doc.selectFirst(
            "a.mrm-pager__btn[rel=\"next\"], a.next, a.nextpostslink, .pagination a.next, " +
                ".next.page-numbers, .navigation-ajax #navigation-ajax, div.nav-previous a",
        ) != null

        return MangasPage(mangas, hasNext)
    }

    private fun mangaFromElement(el: Element): SManga? {
        val titleEl = el.selectFirst(
            ".mrm-r-item__link, .post-title a, .manga-title a, h3 a, h4 a, h5 a, " +
                ".item-summary a, a[href*=/manga/]",
        ) ?: el.closest("a") ?: return null

        val href = titleEl.attr("abs:href").ifBlank { titleEl.attr("href") }
        if (href.isBlank() || !href.contains("/manga/")) return null

        val thumbEl = el.selectFirst(".mrm-r-item__art img, .item-thumb img, .manga-thumb img, img")
        val title = el.selectFirst(".mrm-r-item__title")?.text()?.trim()?.ifBlank {
            titleEl.attr("title").ifBlank { titleEl.text().trim() }
        } ?: titleEl.attr("title").ifBlank { titleEl.text().trim() }

        return SManga.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            if (title.isBlank()) return null
            thumbnail_url = thumbEl.extractCoverUrl()
        }
    }

    // =========================== Details + Chapters ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        val details = if (fetchDetails) parseDetails(doc, manga) else manga
        val chapterList = if (fetchChapters) parseChapters(doc) else chapters
        return SMangaUpdate(details, chapterList)
    }

    private fun parseDetails(
        doc: Document,
        manga: SManga,
    ): SManga = manga.apply {
        title = doc.selectFirst("h1, .post-title h1")?.text()?.trim() ?: title
        author =
            doc.selectFirst(".author-content a, .manga-author a, .author-content")
                ?.text()?.trim()
        artist =
            doc.selectFirst(".artist-content a, .manga-artist a, .artist-content")
                ?.text()?.trim()
        genre = doc.select(".mrm-genres__list a, .genres-content a, .tags a, .genres a").joinToString { it.text() }
        status =
            parseStatus(
                doc.selectFirst(
                    ".mrm-hero__status .summary-content, .status-content, " +
                        ".post-status .summary-content, .manga-status",
                )?.text().orEmpty(),
            )
        description =
            doc.selectFirst(
                ".description-summary .summary__content, .summary__content, " +
                    ".description-summary, .manga-summary",
            )?.text()?.trim()
        thumbnail_url =
            doc.selectFirst(
                ".mrm-hero__cover img, div.summary_image img, .wp-post-image, " +
                    ".item-thumb img, .manga-thumb img, img.wp-post-image",
            ).extractCoverUrl() ?: thumbnail_url
    }.also { m ->
        if (translateSynopsis && language.target != language.origin &&
            !m.description.isNullOrBlank()
        ) {
            m.description =
                translator.translate(
                    language.origin,
                    language.target,
                    m.description!!,
                )
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        status.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        status.contains("cancelled", ignoreCase = true) ||
            status.contains("canceled", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(doc: Document): List<SChapter> = doc.select(
        "ul.main li.wp-manga-chapter, .chapter-list li, li.wp-manga-chapter, .version-chap li",
    ).mapNotNull { el ->
        val a = el.selectFirst("a") ?: return@mapNotNull null
        SChapter.create().apply {
            name = a.text().trim()
            setUrlWithoutDomain(a.attr("href"))
            date_upload =
                parseDate(
                    el.selectFirst(".chapter-release-date, span.date, span i")?.text().orEmpty(),
                )
        }
    }

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val text = raw.trim()
        return try {
            LocalDate.parse(text, DATE_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDate.parse(text, DATE_FORMAT_ALT).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    // =========================== Pages ===========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = mutableListOf<Page>()

        doc.select(
            "div.reading-content img.wp-manga-chapter-img, " +
                "#readerarea img, .page-break img, img.wp-manga-chapter-img",
        ).forEach { img ->
            val url =
                img.absUrl("data-src").ifEmpty {
                    img.absUrl("data-cdn").ifEmpty {
                        img.absUrl("src").ifEmpty { img.absUrl("data-lazy-src") }
                    }
                }
            if (url.isNotEmpty() && "data:image" !in url) {
                pages.add(Page(pages.size, imageUrl = url))
            }
        }

        if (pages.isEmpty()) {
            val jsonPages = parsePagesFromJson(doc)
            if (jsonPages.isNotEmpty()) return jsonPages
        }

        val chapterUrl =
            doc.location().toHttpUrl().newBuilder()
                .removeAllQueryParameters("style")
                .build()

        val ocrRequest = ocrUrlInterceptor.getOcrRequest(chapterUrl.toString()) ?: return pages

        // Prefer the page's own captured OCR response; only replay the request when missing.
        val dialogues =
            ocrRequest.responseText?.takeIf { it.isNotBlank() }?.let(::parseOcrResponse)
                ?.ifEmpty { fetchOcrJson(ocrRequest, chapterUrl) }
                ?: fetchOcrJson(ocrRequest, chapterUrl)

        if (dialogues.isEmpty()) return pages

        return pages.mapIndexed { index, page ->
            val dto =
                dialogues.firstOrNull {
                    page.imageUrl?.contains(it.imageUrl, ignoreCase = true) == true
                } ?: return@mapIndexed page

            val dialogueTexts = dto.dialogues.filter { it.getTextBy(language).isNotBlank() }
            if (dialogueTexts.isEmpty()) return@mapIndexed page

            val fragment = Json.encodeToString(dialogueTexts)
            val encoded = java.net.URLEncoder.encode(fragment, Charsets.UTF_8.name()).replace("+", "%20")
            Page(index, imageUrl = "${page.imageUrl}#$encoded")
        }
    }

    private fun parseOcrResponse(raw: String): List<PageDto> {
        val parsed = try {
            raw.parseAs<List<PageDto>>()
        } catch (_: SerializationException) {
            emptyList()
        }
        return parsed.ifEmpty { OcrNormalizer.normalize(raw) }
    }

    private suspend fun fetchOcrJson(
        ocrRequest: OcrRequest,
        chapterUrl: HttpUrl,
    ): List<PageDto> {
        val jsonHeaders =
            Headers.Builder().apply {
                add("Referer", chapterUrl.toString())
                add("Accept", "*/*")
                ocrRequest.interceptedHeaders.forEach { (name, value) -> set(name, value) }
            }.build()

        return try {
            val resolvedUrl = chapterUrl.resolve(ocrRequest.url) ?: return emptyList()
            val ocrResponse =
                client.post(
                    resolvedUrl,
                    headers = jsonHeaders,
                    body = ocrRequest.body.toRequestBody(
                        "application/json; charset=utf-8".toMediaType(),
                    ),
                    ensureSuccess = false,
                )
            if (!ocrResponse.isSuccessful) {
                ocrResponse.close()
                emptyList()
            } else {
                ocrResponse.use { parseOcrResponse(it.body.string()) }
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private suspend fun parsePagesFromJson(doc: Document): List<Page> {
        val script = doc.select("script:containsData(ts_ajax)").firstOrNull() ?: return emptyList()
        val content = script.html()
        val ajaxUrl =
            Regex("""ajax_url["']?\s*[:=]\s*["']([^"']+)["']""")
                .find(content)?.groupValues?.get(1) ?: return emptyList()
        val nonce =
            Regex("""wp_nonce["']?\s*[:=]\s*["']([^"']+)["']""")
                .find(content)?.groupValues?.get(1) ?: return emptyList()
        val postId =
            Regex("""post_id["']?\s*[:=]\s*["']?(\d+)["']?""")
                .find(content)?.groupValues?.get(1) ?: return emptyList()

        return try {
            val body =
                FormBody.Builder()
                    .add("action", "manga_get_chapter_images")
                    .add("post_id", postId)
                    .add("nonce", nonce)
                    .build()
            val resolvedUrl = doc.location().toHttpUrl().resolve(ajaxUrl) ?: return emptyList()
            val res = client.post(resolvedUrl.toString(), body = body).use { it.body.string() }
            val jsonResponse = res.parseAs<JsonObject>()
            val images = jsonResponse["images"]?.jsonArray ?: return emptyList()
            val pages = mutableListOf<Page>()
            images.forEach { item ->
                val url =
                    when (item) {
                        is JsonPrimitive -> item.content
                        is JsonObject -> (item["src"] as? JsonPrimitive)?.content.orEmpty()
                        else -> ""
                    }.trim()
                if (url.isNotEmpty()) {
                    pages.add(Page(pages.size, url))
                }
            }
            pages
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =========================== Utils ===========================================

    private fun Element?.extractCoverUrl(): String? {
        if (this == null) return null
        absUrl("data-src")
            .takeIf { it.isNotBlank() && "data:image" !in it }
            ?.let { return it }
        absUrl("src")
            .takeIf { it.isNotBlank() && "data:image" !in it && "placeholder" !in it }
            ?.let { return it }
        attr("srcset").takeIf { it.isNotBlank() }?.let { srcset ->
            srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()?.let { url ->
                if (url.startsWith("http")) return url
                absUrl(url).takeIf { it.isNotBlank() && "data:image" !in it }?.let { return it }
            }
        }
        return null
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Use site search for advanced filters"),
    )

    // =========================== Preferences =====================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val sizes =
            arrayOf(
                "12", "13", "14", "15", "16", "18", "20", "21", "22", "24", "26", "28",
                "32", "36", "40", "42", "44", "48", "54", "60", "72", "80", "88", "96",
            )
        val scale = (0..10).map { 1f + it / 10f }.toTypedArray()
        val fonts =
            arrayOf(
                "Device font" to DEVICE_FONT,
                "Comic Neue" to "ComicNeue-Bold",
                "Bubblegum Sans" to "BubblegumSans-Regular",
                "Gloria Hallelujah" to "GloriaHallelujah",
                "Boogaloo" to "Boogaloo-Regular",
                "Creepster" to "Creepster-Regular",
                "Fredoka One" to "FredokaOne-Regular",
            )

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = "Font size"
            entries =
                sizes.map {
                    "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - Default" else ""
                }.toTypedArray()
            entryValues = sizes
            summary =
                buildString {
                    appendLine("Dialog text size")
                    append("\t* %s")
                }
            setDefaultValue(fontSize.toString())
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entries[index] as String
                fontSize = selected.toInt()
                Toast.makeText(
                    screen.context,
                    "Font size set to $entry",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = DIALOG_BOX_SCALE_PREF
            title = "Dialog box scale"
            entries =
                scale.map {
                    "${it}x" + if (it == 1f) " - Default" else ""
                }.toTypedArray()
            entryValues = scale.map(Float::toString).toTypedArray()
            summary =
                buildString {
                    appendLine("Scale of OCR dialog boxes")
                    append("\t* %s")
                }
            setDefaultValue(dialogBoxScale.toString())
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entries[index] as String
                dialogBoxScale = selected.toFloat()
                Toast.makeText(
                    screen.context,
                    "Scale set to $entry",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.also(screen::addPreference)

        if (!language.disableFontSettings) {
            ListPreference(screen.context).apply {
                key = FONT_NAME_PREF
                title = "Font"
                entries =
                    fonts.map {
                        it.first + if (it.second.isBlank()) " - Default" else ""
                    }.toTypedArray()
                entryValues = fonts.map { it.second }.toTypedArray()
                summary =
                    buildString {
                        appendLine("Font used for dialog text")
                        append("\t* %s")
                    }
                setDefaultValue(fontName)
                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entries[index] as String
                    fontName = selected
                    Toast.makeText(
                        screen.context,
                        "Font set to $entry",
                        Toast.LENGTH_LONG,
                    ).show()
                    true
                }
            }.also(screen::addPreference)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = DISABLE_WORD_BREAK_PREF
            title = "⚠ Disable word break"
            summary = "Prevent breaking words in dialogs"
            setDefaultValue(language.disableWordBreak)
            setOnPreferenceChangeListener { _, newValue ->
                disableWordBreak = newValue as Boolean
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = CUSTOM_UA_PREF
            title = "Custom User-Agent"
            summary = "Leave empty for default"
            setDefaultValue(customUserAgent)
            setOnPreferenceChangeListener { _, newValue ->
                customUserAgent = (newValue as String).trim()
                true
            }
        }.also(screen::addPreference)

        if (language.target == language.origin) return

        if (language.supportNativeTranslation) {
            SwitchPreferenceCompat(screen.context).apply {
                key = DISABLE_TRANSLATOR_PREF
                title = "⚠ Disable translator"
                summary = "Use site text without machine translation"
                setDefaultValue(language.disableTranslator)
                setOnPreferenceChangeListener { _, newValue ->
                    disableTranslator = newValue as Boolean
                    true
                }
            }.also(screen::addPreference)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = TRANSLATE_SYNOPSIS_PREF
            title = "Translate synopsis"
            summary = "Translate manga description"
            setDefaultValue(language.translateSynopsis)
            setOnPreferenceChangeListener { _, newValue ->
                translateSynopsis = newValue as Boolean
                true
            }
        }.also(screen::addPreference)

        if (!disableTranslator || translateSynopsis) {
            ListPreference(screen.context).apply {
                key = TRANSLATOR_PROVIDER_PREF
                title = "Translator provider"
                entries = translators
                entryValues = translators
                summary =
                    buildString {
                        appendLine("Machine translation engine")
                        append("\t* %s")
                    }
                setDefaultValue(translators.first())
                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entries[index] as String
                    Toast.makeText(
                        screen.context,
                        "Translator: $entry",
                        Toast.LENGTH_LONG,
                    ).show()
                    true
                }
            }.also(screen::addPreference)
        }
    }

    companion object {
        val PAGE_REGEX =
            Regex(
                """.*?\.(webp|png|jpg|jpeg)(?:\?.*)?(?:#.*)?$""",
                RegexOption.IGNORE_CASE,
            )

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US)
        private val DATE_FORMAT_ALT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

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
