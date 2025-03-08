package eu.kanade.tachiyomi.multisrc.machinetranslations

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.multisrc.machinetranslations.interceptors.ComposedImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.O)
abstract class MachineTranslations(
    override val name: String,
    override val baseUrl: String,
    private val language: Language,
) : ParsedHttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val lang = language.lang

    protected val preferences: SharedPreferences by getPreferencesLazy()

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

    protected var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()

    protected var disableSourceSettings: Boolean
        get() = preferences.getBoolean(DISABLE_SOURCE_SETTINGS_PREF, language.disableSourceSettings)
        set(value) = preferences.edit().putBoolean(DISABLE_SOURCE_SETTINGS_PREF, value).apply()

    private val intl = Intl(
        language = language.lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es", "fr", "id", "it", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    private val settings get() = language.apply {
        fontSize = this@MachineTranslations.fontSize
    }

    open val useDefaultComposedImageInterceptor: Boolean = true

    override val client: OkHttpClient get() = clientInstance!!

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

    protected open fun clientBuilder() = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .addInterceptorIf(useDefaultComposedImageInterceptor, ComposedImageInterceptor(baseUrl, settings))

    private fun OkHttpClient.Builder.addInterceptorIf(condition: Boolean, interceptor: Interceptor): OkHttpClient.Builder {
        return this.takeIf { condition.not() } ?: this.addInterceptor(interceptor)
    }

    // ============================== Popular ===============================

    private val popularFilter = FilterList(SelectionList("", listOf(Option(value = "views", query = "sort_by"))))

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // =============================== Latest ===============================

    private val latestFilter = FilterList(SelectionList("", listOf(Option(value = "recent", query = "sort_by"))))

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // =========================== Search ============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectionList -> {
                    val selected = filter.selected()
                    if (selected.value.isBlank()) {
                        return@forEach
                    }
                    url.addQueryParameter(selected.query, selected.value)
                }
                is GenreList -> {
                    filter.state.filter(GenreCheckBox::state).forEach { genre ->
                        url.addQueryParameter("genres", genre.id)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/comics/$slug" }).map { manga ->
                MangasPage(listOf(manga), false)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = "section h2 + div > div"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "a[href*=search]:contains(Next)"

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("p:has(span:contains(Synopsis))")?.ownText()
        author = document.selectFirst("p:has(span:contains(Author))")?.ownText()
        genre = document.select("h2:contains(Genres) + div span").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.object-cover")?.absUrl("src")
        document.selectFirst("p:has(span:contains(Status))")?.ownText()?.let {
            status = when (it.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "section li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            name = it.ownText()
            setUrlWithoutDomain(it.absUrl("href"))
        }
        date_upload = parseChapterDate(element.selectFirst("span")?.text())
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.selectFirst("div#json-data")
            ?.ownText()?.parseAs<List<PageDto>>()
            ?: throw Exception("Pages not found")

        return pages.mapIndexed { index, dto ->
            val imageUrl = when {
                dto.imageUrl.startsWith("http") -> dto.imageUrl
                else -> "https://${dto.imageUrl}"
            }
            val fragment = json.encodeToString<List<Dialog>>(
                dto.dialogues.filter { it.getTextBy(language).isNotBlank() },
            )
            Page(index, imageUrl = "$imageUrl#$fragment")
        }
    }

    override fun imageUrlParse(document: Document): String = ""

    // ============================= Utilities ==============================

    private fun parseChapterDate(date: String?): Long {
        date ?: return 0
        return try { dateFormat.parse(date)!!.time } catch (_: Exception) { parseRelativeDate(date) }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("day", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour", true) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute", true) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second", true) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            date.contains("week", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            else -> 0
        }
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    // =============================== Filters ================================

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SelectionList("Sort", sortByList),
            Filter.Separator(),
            GenreList(title = "Genres", genres = genreList),
        )

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Some libreoffice font sizes
        val sizes = arrayOf(
            "24", "26", "28",
            "32", "36", "40",
            "42", "44", "48",
            "54", "60", "72",
            "80", "88", "96",
        )

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = intl["font_size_title"]
            entries = sizes.map {
                "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - ${intl["default_font_size"]}" else ""
            }.toTypedArray()
            entryValues = sizes
            summary = intl["font_size_summary"]

            setOnPreferenceChange { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                fontSize = selected.toInt()

                Toast.makeText(
                    screen.context,
                    intl["font_size_message"].format(entry),
                    Toast.LENGTH_LONG,
                ).show()

                true // It's necessary to update the user interface
            }
        }.also(screen::addPreference)

        if (language.disableSourceSettings.not()) {
            SwitchPreferenceCompat(screen.context).apply {
                key = DISABLE_SOURCE_SETTINGS_PREF
                title = "⚠ ${intl["disable_website_setting_title"]}"
                summary = intl["disable_website_setting_summary"]
                setDefaultValue(false)
                setOnPreferenceChange { _, newValue ->
                    disableSourceSettings = newValue as Boolean
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
    protected fun Preference.setOnPreferenceChange(onPreferenceChangeListener: Preference.OnPreferenceChangeListener) {
        setOnPreferenceChangeListener { preference, newValue ->
            isSettingsChanged = true
            onPreferenceChangeListener.onPreferenceChange(preference, newValue)
        }
    }

    companion object {
        val PAGE_REGEX = Regex(".*?\\.(webp|png|jpg|jpeg)#\\[.*?]", RegexOption.IGNORE_CASE)
        const val PREFIX_SEARCH = "id:"
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val DISABLE_SOURCE_SETTINGS_PREF = "disableSourceSettingsPref"
        private const val DEFAULT_FONT_SIZE = "24"

        private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.US)
    }
}
