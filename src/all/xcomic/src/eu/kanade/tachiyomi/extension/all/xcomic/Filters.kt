package eu.kanade.tachiyomi.extension.all.xcomic

import eu.kanade.tachiyomi.source.model.Filter

class CheckboxFilterOption(name: String, val value: String) : Filter.CheckBox(name)
class TriStateFilterOption(name: String, val value: String) : Filter.TriState(name)

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), default) {
    val selected: String
        get() = options[state].second
}

abstract class CheckboxGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckboxFilterOption>(name, options.map { CheckboxFilterOption(it.first, it.second) }) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }
}

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilterOption>(name, options.map { TriStateFilterOption(it.first, it.second) }) {
    val included: List<String>
        get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded: List<String>
        get() = state.filter { it.isExcluded() }.map { it.value }
}

class SortFilter(
    options: List<Pair<String, String>> = sortOptions,
    defaultIndex: Int = 0,
) : SelectFilter(name = "Order by", options = options, default = defaultIndex)

class OriginalStatusFilter(
    options: List<Pair<String, String>> = uploadStatus,
) : CheckboxGroupFilter(name = "Original Work Status", options = options)

class MinChapterFilter : Filter.Text("Minimum Chapter Count")
class MaxChapterFilter : Filter.Text("Maximum Chapter Count")

class OriginalLanguageFilter(
    options: List<Pair<String, String>> = languages,
) : CheckboxGroupFilter(name = "Original Work Language", options = options)

class TranslationLanguageFilter(
    options: List<Pair<String, String>> = languages,
) : CheckboxGroupFilter(name = "Translated Language", options = options)

class ContentRatingFilter(
    options: List<Pair<String, String>> = emptyList(),
) : CheckboxGroupFilter(name = "Content Rating", options = options)

class TypeFilter(
    options: List<Pair<String, String>> = emptyList(),
) : CheckboxGroupFilter(name = "Types", options = options)

class DemographicFilter(
    options: List<Pair<String, String>> = emptyList(),
) : CheckboxGroupFilter(name = "Demographics", options = options)

class FormatFilter(
    options: List<Pair<String, String>> = formatOptions,
) : TriStateGroupFilter(name = "Formats", options = options)

class GenreGroupFilter(
    name: String = "Genres",
    options: List<Pair<String, String>> = emptyList(),
) : TriStateGroupFilter(name, options)

class GenreInModeFilter : SelectFilter(name = "Include Mode", options = listOf("AND" to "and", "OR" to "or"), default = 0)

class GenreExModeFilter : SelectFilter(name = "Exclude Mode", options = listOf("AND" to "and", "OR" to "or"), default = 1)

class YearFilter : Filter.Text("Year (e.g. 2015 or 1901-2027)")

// --- Hardcoded Constants ---
val languages = listOf(
    "English" to "en",
    "French" to "fr",
    "Portuguese" to "pt",
    "Korean" to "ko",
    "Japanese" to "ja",
    "Indonesian" to "id",
    "Chinese" to "zh",
    "Chinese (Traditional)" to "zh_hk",
    "Afrikaans" to "af",
    "Albanian" to "sq",
    "Arabic" to "ar",
    "Azerbaijani" to "az",
    "Basque" to "eu",
    "Belarusian" to "be",
    "Bengali" to "bn",
    "Bulgarian" to "bg",
    "Burmese" to "my",
    "Cambodian" to "km",
    "Catalan" to "ca",
    "Cebuano" to "ceb",
    "Croatian" to "hr",
    "Chuvash" to "cv",
    "Czech" to "cs",
    "Danish" to "da",
    "Dutch" to "nl",
    "Esperanto" to "eo",
    "Estonian" to "et",
    "Filipino" to "fil",
    "Finnish" to "fi",
    "Galician" to "gl",
    "Georgian" to "ka",
    "German" to "de",
    "Greek" to "el",
    "Gujarati" to "gu",
    "Hebrew" to "he",
    "Hindi" to "hi",
    "Hungarian" to "hu",
    "Icelandic" to "is",
    "Irish" to "ga",
    "Italian" to "it",
    "Javanese" to "jv",
    "Kazakh" to "kk",
    "Latin" to "la",
    "Latvian" to "lv",
    "Lithuanian" to "lt",
    "Malay" to "ms",
    "Malayalam" to "ml",
    "Marathi" to "mr",
    "Moldavian" to "mo",
    "Mongolian" to "mn",
    "Nepali" to "ne",
    "Norwegian" to "no",
    "Persian" to "fa",
    "Polish" to "pl",
    "Portuguese (BR)" to "pt_br",
    "Romanian" to "ro",
    "Russian" to "ru",
    "Serbian" to "sr",
    "Serbo-Croatian" to "sh",
    "Sinhalese" to "si",
    "Slovak" to "sk",
    "Slovenian" to "sl",
    "Spanish" to "es",
    "Spanish (LA)" to "es_419",
    "Swati" to "ss",
    "Swedish" to "sv",
    "Tamil" to "ta",
    "Telugu" to "te",
    "Thai" to "th",
    "Turkish" to "tr",
    "Ukrainian" to "uk",
    "Urdu" to "ur",
    "Uzbek" to "uz",
    "Vietnamese" to "vi",
    "Zulu" to "zu",
    "Other" to "_t",
)

val formatOptions = listOf(
    "1-Koma" to "1_koma",
    "2-Koma" to "2_koma",
    "3-Koma" to "3_koma",
    "4-Koma" to "4_koma",
    "Adaptation" to "adaptation",
    "Anthology" to "anthology",
    "Artbook" to "artbook",
    "Award Winning" to "award_winning",
    "Doujinshi" to "doujinshi",
    "Fan Colored" to "fan_colored",
    "Fanbook" to "fanbook",
    "Fanwork" to "fanwork",
    "Full Color" to "full_color",
    "Guidebook" to "guidebook",
    "Illustbook" to "illustbook",
    "Illustration Book" to "illustration_book",
    "Japanese Novel" to "japanese_novel",
    "Light Novel" to "light_novel",
    "Long Strip" to "long_strip",
    "Longstrip" to "longstrip",
    "Novels" to "novels",
    "Official Colored" to "official_colored",
    "Oneshot" to "oneshot",
    "Original Doujinshi" to "original_doujinshi",
    "Partially Colored" to "partially_colored",
    "Partially Colored Webtoon" to "partially_colored_webtoon",
    "Web Comic" to "web_comic",
    "Web Novel" to "web_novel",
    "Webtoon" to "webtoon",
)

val uploadStatus = listOf(
    "Releasing" to "releasing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
    "Upcoming" to "upcoming",
    "Unknown" to "unknown",
)

val sortOptions = listOf(
    "Rating Score" to "field_score", "Latest Update" to "field_update", "Recently Added" to "field_create",
    "Name A-Z" to "field_name_asc", "Name Z-A" to "field_name_desc", "Most Follows" to "field_follow",
    "Most Reviews" to "field_review", "Most Comments" to "field_comment", "Most Chapters" to "field_chapter",
)
