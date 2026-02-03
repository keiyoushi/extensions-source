package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaDistrict :
    Madara(
        "Manga District",
        "https://mangadistrict.com",
        "en",
    ),
    ConfigurableSource {

    override val mangaSubString = "title"

    private val preferences: SharedPreferences by getPreferencesLazy {
        try {
            val oldTagSet = getStringSet(TAG_LIST_PREF, emptySet())!!
            edit()
                .remove(TAG_LIST_PREF)
                .putString(TAG_LIST_PREF, oldTagSet.joinToString("%"))
                .apply()
        } catch (_: Exception) {}
    }

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).cleanTitleIfNeeded()

    override fun popularMangaNextPageSelector() = "div[role=navigation] span.current + a.page"

    override fun latestUpdatesFromElement(element: Element): SManga = super.latestUpdatesFromElement(element).cleanTitleIfNeeded()

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.select(mangaDetailsSelectorTag).mapNotNull { element ->
            element.ownText() to element.attr("href")
                .removeSuffix("/").substringAfterLast('/')
        }
        tagList = tagList.plus(tags)

        return super.mangaDetailsParse(document)
            .cleanTitleIfNeeded()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        return when (getImgRes()) {
            IMG_RES_HIGH -> chapters.filterNot { it.url.contains("/v2-full-quality") }
            IMG_RES_FULL -> chapters.filterNot { it.url.contains("/v1-high-quality") }
            else -> chapters
        }
    }

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        val urlKey = url.urlKey()
        val dates = preferences.dates
        dates[urlKey]?.also {
            if (date_upload == 0L) {
                // If date_upload is not set (due to NEW tag), try to get it from the page lists
                date_upload = it
            } else {
                dates.remove(urlKey)
                preferences.dates = dates
            }
        }
    }

    private val pageListDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override val pageListParseSelector = "div.page-break img:not(#image-99999)"

    override fun pageListParse(document: Document): List<Page> {
        try {
            pageListDate.parse(
                document.selectFirst("meta[property=og:updated_time]")!!
                    .attr("content").substringBeforeLast("+"),
            )!!.time.also {
                val dates = preferences.dates
                val urlKey = document.location().urlKey()
                dates[urlKey] = it
                preferences.dates = dates
            }
        } catch (_: Exception) {}

        return super.pageListParse(document)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val tagFilter = filters.filterIsInstance<TagList>().firstOrNull()
        if (tagFilter != null && tagFilter.state > 0) {
            val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            urlBuilder.addPathSegment("publication-tag")
            urlBuilder.addPathSegment(tagFilter.toUriPart())
            urlBuilder.addPathSegments("page/$page")
            return client.newCall(GET(urlBuilder.build(), headers))
                .asObservableSuccess().map { response ->
                    popularMangaParse(response)
                }
        } else {
            return super.fetchSearchManga(page, query, filters)
        }
    }

    private fun loadTagListFromPreferences(): Set<Pair<String, String>> = preferences.getString(TAG_LIST_PREF, "")
        ?.let {
            it.split('%').mapNotNull { tag ->
                tag.split('|')
                    .let { splits ->
                        if (splits.size == 2) splits[0] to splits[1] else null
                    }
            }
        }
        ?.toSet()
        // Create at least 1 tag to avoid excessively reading preferences
        .let { if (it.isNullOrEmpty()) setOf("Manhwa" to "manhwa") else it }

    private var tagList: Set<Pair<String, String>> = loadTagListFromPreferences()
        set(value) {
            preferences.edit().putString(
                TAG_LIST_PREF,
                value.joinToString("%") { "${it.first}|${it.second}" },
            ).apply()
            field = value
        }

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().list.toMutableList()
        if (tagList.isNotEmpty()) {
            filters += Filter.Separator()
            filters += Filter.Header("Tag browse will ignore other filters")
            filters += TagList("Tag browse", listOf(Pair("<Browse tag>", "")) + tagList.toList())
        }
        return FilterList(filters)
    }

    private class TagList(title: String, options: List<Pair<String, String>>, state: Int = 0) : UriPartFilter(title, options.toTypedArray(), state)

    private fun String.urlKey(): String = toHttpUrl().pathSegments.let { path ->
        "${path[1]}/${path[2]}"
    }

    private fun SManga.cleanTitleIfNeeded() = apply {
        title = title.let { originalTitle ->
            var tempTitle = originalTitle
            customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
                runCatching {
                    tempTitle = tempTitle.replace(Regex(customRegex), "")
                }
            }
            if (isRemoveTitleVersion()) {
                tempTitle = tempTitle.replace(titleRegex, "")
            }
            tempTitle.trim()
        }
    }

    // console.log([...document.querySelectorAll("div.checkbox-group .checkbox")].map((el) => `Genre("${el.querySelector("label").innerText.trim()}", "${el.querySelector("input").getAttribute('value')}"),`).join('\n'))
    override var genresList = listOf(
        Genre("3D", "3d"),
        Genre("Action", "action"),
        Genre("Adapted to Anime", "adapted-to-anime"),
        Genre("Adventure", "adventure"),
        Genre("Aliens", "aliens"),
        Genre("Animal Characteristics", "animal-characteristics"),
        Genre("Based on Another Work", "based-on-another-work"),
        Genre("BL", "bl"),
        Genre("BL Uncensored", "bl-uncensored"),
        Genre("Borderline H", "borderline-h"),
        Genre("Cohabitation", "cohabitation"),
        Genre("Collection of Stories", "collection-of-stories"),
        Genre("Comedy", "comedy"),
        Genre("Comics", "comics"),
        Genre("Cooking", "cooking"),
        Genre("Coworkers", "coworkers"),
        Genre("Crime", "crime"),
        Genre("Crossdressing", "crossdressing"),
        Genre("Delinquents", "delinquents"),
        Genre("Demons", "demons"),
        Genre("Detectives", "detectives"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Explicit Sex", "explicit-sex"),
        Genre("Fantasy", "fantasy"),
        Genre("Fetish", "fetish"),
        Genre("Full Color", "full-color"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Ghosts", "ghosts"),
        Genre("GL", "gl"),
        Genre("Gyaru", "gyaru"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Incest", "incest"),
        Genre("Isekai", "isekai"),
        Genre("Japanese Webtoons", "japanese-webtoons"),
        Genre("Josei", "josei"),
        Genre("Light Novels", "light-novels"),
        Genre("Mafia", "mafia"),
        Genre("Magic", "magic"),
        Genre("Magical Girl", "magical-girl"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature Romance", "mature-romance"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Monster Girls", "monster-girls"),
        Genre("Monsters", "monsters"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Ninja", "ninja"),
        Genre("Nudity", "nudity"),
        Genre("One Shot", "one-shot"),
        Genre("Person in a Strange World", "person-in-a-strange-world"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Reverse Harem", "reverse-harem"),
        Genre("Romance", "romance"),
        Genre("Salaryman", "salaryman"),
        Genre("Samurai", "samurai"),
        Genre("School Life", "school-life"),
        Genre("Sci Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Sexual Abuse", "sexual-abuse"),
        Genre("Sexual Content", "sexual-content"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo-ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen-ai", "shounen-ai"),
        Genre("Siblings", "siblings"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Summoned Into Another World", "summoned-into-another-world"),
        Genre("Superheroes", "superheroes"),
        Genre("Supernatural", "supernatural"),
        Genre("Survival", "survival"),
        Genre("Thriller", "thriller"),
        Genre("Time Travel", "time-travel"),
        Genre("Transfer Students", "transfer-students"),
        Genre("Uncensored", "uncensored"),
        Genre("Vampires", "vampires"),
        Genre("Violence", "violence"),
        Genre("Virtual Reality", "virtual-reality"),
        Genre("Web Novels", "web-novels"),
        Genre("Webtoons", "webtoons"),
        Genre("Western", "western"),
        Genre("Work Life", "work-life"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
        Genre("Zombies", "zombies"),
    )

    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!
    private fun getImgRes() = preferences.getString(IMG_RES_PREF, IMG_RES_DEFAULT)!!

    private var SharedPreferences.dates: MutableMap<String, Long>
        get() = try {
            json.decodeFromString(getString(DATE_MAP, "{}") ?: "{}")
        } catch (_: Exception) {
            mutableMapOf()
        }

        @SuppressLint("ApplySharedPref")
        set(newVal) {
            edit().putString(DATE_MAP, json.encodeToString(newVal)).commit()
        }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like “(Official)” or “(Doujinshi)” from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "${REMOVE_TITLE_CUSTOM_PREF}_$lang"
            title = "Custom regex to be removed from title"
            summary = customRemoveTitle()
            setDefaultValue("")

            val validate = { str: String ->
                runCatching { Regex(str) }
                    .map { true to "" }
                    .getOrElse { false to it.message }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid.first) valid.second else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val (isValid, message) = validate(newValue as String)
                if (isValid) {
                    summary = newValue
                } else {
                    Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                }
                isValid
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMG_RES_PREF
            title = "Image quality"
            entries = arrayOf("All", "High quality", "Full quality")
            entryValues = arrayOf(IMG_RES_ALL, IMG_RES_HIGH, IMG_RES_FULL)
            summary = "%s\nRefresh entry to update the chapter list."
            setDefaultValue(IMG_RES_DEFAULT)
        }.let(screen::addPreference)
    }

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-wpfc-original-src") -> element.attr("abs:data-wpfc-original-src")
        else -> super.imageFromElement(element)
    }

    companion object {
        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)

        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val TAG_LIST_PREF = "TAG_LIST"

        private const val IMG_RES_PREF = "IMG_RES"
        private const val IMG_RES_ALL = "all"
        private const val IMG_RES_HIGH = "high"
        private const val IMG_RES_FULL = "full"
        private const val IMG_RES_DEFAULT = IMG_RES_ALL

        private const val DATE_MAP = "date_saved"
    }
}
