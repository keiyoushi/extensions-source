package eu.kanade.tachiyomi.extension.all.luscious

import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

fun toLusLang(lang: String): String = when (lang) {
    "all" -> FILTER_VALUE_IGNORE
    "en" -> "1"
    "ja" -> "2"
    "es" -> "3"
    "it" -> "4"
    "de" -> "5"
    "fr" -> "6"
    "zh" -> "8"
    "ko" -> "9"
    "pt-BR" -> "100"
    "th" -> "101"
    else -> "99"
}

class TriStateFilterOption(name: String, val value: String) : eu.kanade.tachiyomi.source.model.Filter.TriState(name)
abstract class TriStateGroupFilter(name: String, options: List<TriStateFilterOption>) : eu.kanade.tachiyomi.source.model.Filter.Group<TriStateFilterOption>(name, options) {
    private val included: List<String>
        get() = state.filter { it.isIncluded() }.map { it.value }

    private val excluded: List<String>
        get() = state.filter { it.isExcluded() }.map { it.value }

    fun anyNotIgnored(): Boolean = state.any { !it.isIgnored() }

    override fun toString(): String = (included.map { "+$it" } + excluded.map { "-$it" }).joinToString("")
}

fun eu.kanade.tachiyomi.source.model.Filter<*>.toJsonObject(key: String): Filter {
    val value = this.toString()
    return Filter(
        name = key,
        value = value,
    )
}

open class TextFilter(name: String) : eu.kanade.tachiyomi.source.model.Filter.Text(name)

class GenreGroupFilter(filters: List<TriStateFilterOption>) : TriStateGroupFilter("Genres", filters)

class CheckboxFilterOption(name: String, val value: String, default: Boolean = true) : eu.kanade.tachiyomi.source.model.Filter.CheckBox(name, default)

abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : eu.kanade.tachiyomi.source.model.Filter.Group<CheckboxFilterOption>(name, options) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }

    override fun toString(): String = selected.joinToString("") { "+$it" }
}

class InterestGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Interests", options)
class LanguageGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Languages", options)

class SelectFilterOption(val name: String, val value: String)

abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : eu.kanade.tachiyomi.source.model.Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
    val selected: String
        get() = options[state].value

    override fun toString(): String = selected
}

class SortBySelectFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort By", options, default)
class AlbumTypeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Album Type", options)
class ContentTypeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Content Type", options)
class RestrictGenresSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Restrict Genres", options)
class SelectionSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Selection", options)
class AlbumSizeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Album Size", options)
class TagTextFilters : TextFilter("Tags")
class CreatorTextFilters : TextFilter("Uploader")
class FavoriteTextFilters : TextFilter("Favorite by User")

fun getSortFilters(sortState: Int, lusLang: String) = FilterList(
    SortBySelectFilter(getSortFilters(), sortState),
    AlbumTypeSelectFilter(getAlbumTypeFilters()),
    ContentTypeSelectFilter(getContentTypeFilters()),
    AlbumSizeSelectFilter(getAlbumSizeFilters()),
    SelectionSelectFilter(getSelectionFilters()),
    RestrictGenresSelectFilter(getRestrictGenresFilters()),
    InterestGroupFilter(getInterestFilters()),
    LanguageGroupFilter(getLanguageFilters(lusLang)),
    GenreGroupFilter(getGenreFilters()),
    eu.kanade.tachiyomi.source.model.Filter.Header("Separate tags with commas (,)"),
    eu.kanade.tachiyomi.source.model.Filter.Header("Prepend with dash (-) to exclude"),
    TagTextFilters(),
    eu.kanade.tachiyomi.source.model.Filter.Header("The following require username or ID"),
    CreatorTextFilters(),
    FavoriteTextFilters(),
)

fun getSortFilters(): List<SelectFilterOption> {
    val sortOptions = mutableListOf<SelectFilterOption>()
    listOf(
        SelectFilterOption("Rating - All Time", "rating_all_time"),
        SelectFilterOption("Rating - Last 7 Days", "rating_7_days"),
        SelectFilterOption("Rating - Last 14 Days", "rating_14_days"),
        SelectFilterOption("Rating - Last 30 Days", "rating_30_days"),
        SelectFilterOption("Rating - Last 90 Days", "rating_90_days"),
        SelectFilterOption("Rating - Last Year", "rating_1_year"),
        SelectFilterOption("Date - Newest First", "date_newest"),
        SelectFilterOption("Date - Oldest First", "date_oldest"),
        SelectFilterOption("Date - Upcoming", "date_upcoming"),
        SelectFilterOption("Date - Trending", "date_trending"),
        SelectFilterOption("Date - Featured", "date_featured"),
        SelectFilterOption("Date - Last Viewed", "date_last_interaction"),
        SelectFilterOption("Other - Search Score", "search_score"),
    ).forEach {
        sortOptions.add(it)
    }
    validYears().map {
        sortOptions.add(SelectFilterOption("Date - $it", "date_$it"))
    }
    listOf(
        SelectFilterOption("First Letter - Any", "alpha_any"),
        SelectFilterOption("First Letter - A", "alpha_a"),
        SelectFilterOption("First Letter - B", "alpha_b"),
        SelectFilterOption("First Letter - C", "alpha_c"),
        SelectFilterOption("First Letter - D", "alpha_d"),
        SelectFilterOption("First Letter - E", "alpha_e"),
        SelectFilterOption("First Letter - F", "alpha_f"),
        SelectFilterOption("First Letter - G", "alpha_g"),
        SelectFilterOption("First Letter - H", "alpha_h"),
        SelectFilterOption("First Letter - I", "alpha_i"),
        SelectFilterOption("First Letter - J", "alpha_j"),
        SelectFilterOption("First Letter - K", "alpha_k"),
        SelectFilterOption("First Letter - L", "alpha_l"),
        SelectFilterOption("First Letter - M", "alpha_m"),
        SelectFilterOption("First Letter - N", "alpha_n"),
        SelectFilterOption("First Letter - O", "alpha_o"),
        SelectFilterOption("First Letter - P", "alpha_p"),
        SelectFilterOption("First Letter - Q", "alpha_q"),
        SelectFilterOption("First Letter - R", "alpha_r"),
        SelectFilterOption("First Letter - S", "alpha_s"),
        SelectFilterOption("First Letter - T", "alpha_t"),
        SelectFilterOption("First Letter - U", "alpha_u"),
        SelectFilterOption("First Letter - V", "alpha_v"),
        SelectFilterOption("First Letter - W", "alpha_w"),
        SelectFilterOption("First Letter - X", "alpha_x"),
        SelectFilterOption("First Letter - Y", "alpha_y"),
        SelectFilterOption("First Letter - Z", "alpha_z"),
    ).forEach {
        sortOptions.add(it)
    }
    return sortOptions
}

fun getAlbumTypeFilters() = listOf(
    SelectFilterOption("All", FILTER_VALUE_IGNORE),
    SelectFilterOption("Manga", "manga"),
    SelectFilterOption("Pictures", "pictures"),
)

fun getRestrictGenresFilters() = listOf(
    SelectFilterOption("None", FILTER_VALUE_IGNORE),
    SelectFilterOption("Loose", "loose"),
    SelectFilterOption("Strict", "strict"),
)

fun getSelectionFilters() = listOf(
    SelectFilterOption("All", "all"),
    SelectFilterOption("No Votes", "not_voted"),
    SelectFilterOption("Downvoted", "downvoted"),
    SelectFilterOption("Animated", "animated"),
    SelectFilterOption("Banned", "banned"),
    SelectFilterOption("Made by People You Follow", "made_by_following"),
    SelectFilterOption("Faved by People You Follow", "faved_by_following"),

)

fun getContentTypeFilters() = listOf(
    SelectFilterOption("All", FILTER_VALUE_IGNORE),
    SelectFilterOption("Hentai", "2"),
    SelectFilterOption("Non-Erotic", "5"),
    SelectFilterOption("Real People", "6"),
)

fun getAlbumSizeFilters() = listOf(
    SelectFilterOption("All", FILTER_VALUE_IGNORE),
    SelectFilterOption("0-25", "0"),
    SelectFilterOption("0-50", "1"),
    SelectFilterOption("50-100", "2"),
    SelectFilterOption("100-200", "3"),
    SelectFilterOption("200-800", "4"),
    SelectFilterOption("800-3200", "5"),
    SelectFilterOption("3200-12800", "6"),
)

fun getInterestFilters() = listOf(
    CheckboxFilterOption("Straight Sex", "1"),
    CheckboxFilterOption("Trans x Girl", "10"),
    CheckboxFilterOption("Gay / Yaoi", "2"),
    CheckboxFilterOption("Lesbian / Yuri", "3"),
    CheckboxFilterOption("Trans", "5"),
    CheckboxFilterOption("Solo Girl", "6"),
    CheckboxFilterOption("Trans x Trans", "8"),
    CheckboxFilterOption("Trans x Guy", "9"),
)

fun getLanguageFilters(lusLang: Any) = listOf(
    CheckboxFilterOption("English", toLusLang("en"), false),
    CheckboxFilterOption("Japanese", toLusLang("ja"), false),
    CheckboxFilterOption("Spanish", toLusLang("es"), false),
    CheckboxFilterOption("Italian", toLusLang("it"), false),
    CheckboxFilterOption("German", toLusLang("de"), false),
    CheckboxFilterOption("French", toLusLang("fr"), false),
    CheckboxFilterOption("Chinese", toLusLang("zh"), false),
    CheckboxFilterOption("Korean", toLusLang("ko"), false),
    CheckboxFilterOption("Others", toLusLang("other"), false),
    CheckboxFilterOption("Portuguese", toLusLang("pt-BR"), false),
    CheckboxFilterOption("Thai", toLusLang("th"), false),
).filterNot { it.value == lusLang }

fun getGenreFilters() = listOf(
    TriStateFilterOption("3D / Digital Art", "25"),
    TriStateFilterOption("Amateurs", "20"),
    TriStateFilterOption("Artist Collection", "19"),
    TriStateFilterOption("Asian Girls", "12"),
    TriStateFilterOption("BDSM", "27"),
    TriStateFilterOption("Bestiality Hentai", "5"),
    TriStateFilterOption("Casting", "44"),
    TriStateFilterOption("Celebrity Fakes", "16"),
    TriStateFilterOption("Cosplay", "22"),
    TriStateFilterOption("Cross-Dressing", "30"),
    TriStateFilterOption("Cumshot", "26"),
    TriStateFilterOption("Defloration / First Time", "59"),
    TriStateFilterOption("Ebony Girls", "32"),
    TriStateFilterOption("European Girls", "46"),
    TriStateFilterOption("Extreme Gore", "60"),
    TriStateFilterOption("Extreme Scat", "61"),
    TriStateFilterOption("Fantasy / Monster Girls", "10"),
    TriStateFilterOption("Fetish", "2"),
    TriStateFilterOption("Furries", "8"),
    TriStateFilterOption("Futanari", "31"),
    TriStateFilterOption("Group Sex", "36"),
    TriStateFilterOption("Harem", "56"),
    TriStateFilterOption("Humor", "41"),
    TriStateFilterOption("Incest", "24"),
    TriStateFilterOption("Interracial", "28"),
    TriStateFilterOption("Kemonomimi / Animal Ears", "39"),
    TriStateFilterOption("Latina Girls", "33"),
    TriStateFilterOption("Lolicon", "3"),
    TriStateFilterOption("Mature", "13"),
    TriStateFilterOption("Members: Original Art", "18"),
    TriStateFilterOption("Members: Verified Selfies", "21"),
    TriStateFilterOption("Military", "48"),
    TriStateFilterOption("Mind Control", "34"),
    TriStateFilterOption("Monsters & Tentacles", "38"),
    TriStateFilterOption("Music", "45"),
    TriStateFilterOption("Netorare / Cheating", "40"),
    TriStateFilterOption("No Genre Given", "1"),
    TriStateFilterOption("Nonconsent / Reluctance", "37"),
    TriStateFilterOption("Other Ethnicity Girls", "57"),
    TriStateFilterOption("Private to Luscious", "55"),
    TriStateFilterOption("Public Sex", "43"),
    TriStateFilterOption("Romance", "42"),
    TriStateFilterOption("School / College", "35"),
    TriStateFilterOption("Sex Workers", "47"),
    TriStateFilterOption("SFW", "23"),
    TriStateFilterOption("Shotacon", "4"),
    TriStateFilterOption("Softcore / Ecchi", "9"),
    TriStateFilterOption("Superheroes", "17"),
    TriStateFilterOption("Swimsuit", "49"),
    TriStateFilterOption("Tankōbon", "45"),
    TriStateFilterOption("Trans", "14"),
    TriStateFilterOption("TV / Movies", "51"),
    TriStateFilterOption("Video Games", "15"),
    TriStateFilterOption("Vintage", "58"),
    TriStateFilterOption("Western", "11"),
    TriStateFilterOption("Workplace Sex", "50"),
)

inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

private fun validYears(): List<Int> {
    val years = mutableListOf<Int>()
    val current = Calendar.getInstance()
    val currentYear = current.get(Calendar.YEAR)
    var firstYear = 2013
    while (currentYear != firstYear - 1) {
        years.add(firstYear)
        firstYear++
    }
    return years.reversed()
}
