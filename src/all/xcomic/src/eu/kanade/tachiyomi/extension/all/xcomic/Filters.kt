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

class LetterFilter :
    SelectFilter(
        name = "Letter matching mode (Slow)",
        options = listOf("Disabled" to "disabled", "Enabled" to "enabled"),
    )

class SortFilter(
    options: List<Pair<String, String>> = sortOptions,
    defaultIndex: Int = 0,
) : SelectFilter(name = "Order by", options = options, default = defaultIndex)

class OriginalStatusFilter(
    options: List<Pair<String, String>> = uploadStatus,
) : SelectFilter(name = "Original Work Status", options = options)

class UploadStatusFilter(
    options: List<Pair<String, String>> = uploadStatus,
) : SelectFilter(name = "Upload Status", options = options)

class ChapterCountFilter(
    options: List<Pair<String, String>> = chapterCountOptions,
) : SelectFilter(name = "Chapter Count", options = options)

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
    "Abkhazian" to "ab",
    "Afrikaans" to "af",
    "Armenian" to "hy",
    "Arabic" to "ar",
    "Albanian" to "sq",
    "Azerbaijani" to "az",
    "Belarusian" to "be",
    "Bengali" to "bn",
    "Burmese" to "my",
    "Bulgarian" to "bg",
    "Bosnian" to "bs",
    "Cambodian" to "km",
    "Catalan" to "ca",
    "Cebuano" to "ceb",
    "Czech" to "cs",
    "Croatian" to "hr",
    "Chuvash" to "cv",
    "Danish" to "da",
    "Dutch" to "nl",
    "Estonian" to "et",
    "Esperanto" to "eo",
    "Basque" to "eu",
    "Filipino" to "fil",
    "Finnish" to "fi",
    "German" to "de",
    "Georgian" to "ka",
    "Greek" to "el",
    "Guarani" to "gn",
    "Gujarati" to "gu",
    "Hindi" to "hi",
    "Hebrew" to "he",
    "Haitian Creole" to "ht",
    "Hungarian" to "hu",
    "Icelandic" to "is",
    "Igbo" to "ig",
    "Galician" to "gl",
    "Irish" to "ga",
    "Italian" to "it",
    "Kazakh" to "kk",
    "Kyrgyz" to "ky",
    "Lithuanian" to "lt",
    "Latin" to "la",
    "Laothian" to "lo",
    "Kurdish" to "ku",
    "Javanese" to "jv",
    "Malagasy" to "mg",
    "Latvian" to "lv",
    "Malay" to "ms",
    "Malayalam" to "ml",
    "Maltese" to "mt",
    "Moldavian" to "mo",
    "Marathi" to "mr",
    "Maori" to "mi",
    "Mongolian" to "mn",
    "Nyanja" to "ny",
    "Nepali" to "ne",
    "Pashto" to "ps",
    "Norwegian" to "no",
    "Persian" to "fa",
    "Portuguese (BR)" to "pt_br",
    "Serbian" to "sr",
    "Sesotho" to "st",
    "Russian" to "ru",
    "Romanian" to "ro",
    "Polish" to "pl",
    "Serbo-Croatian" to "sh",
    "Sinhalese" to "si",
    "Somali" to "so",
    "Swedish" to "sv",
    "Thai" to "th",
    "Turkish" to "tr",
    "Swati" to "ss",
    "Slovak" to "sk",
    "Spanish" to "es",
    "Tigrinya" to "ti",
    "Tamil" to "ta",
    "Turkmen" to "tk",
    "Ukrainian" to "uk",
    "Tonga" to "to",
    "Telugu" to "te",
    "Spanish (LA)" to "es_419",
    "Slovenian" to "sl",
    "Vietnamese" to "vi",
    "Other" to "_t",
    "Uzbek" to "uz",
    "Zulu" to "zu",
)

val formatOptions = listOf(
    "4 Koma" to "4_koma",
    "Adaptation" to "adaptation",
    "Anthology" to "anthology",
    "Award Winning" to "award_winning",
    "Doujinshi" to "doujinshi",
    "Fan Colored" to "fan_colored",
    "Full Color" to "full_color",
    "Long Strip" to "long_strip",
    "Official Colored" to "official_colored",
    "Oneshot" to "oneshot",
    "Web Comic" to "web_comic",
    "Webtoon" to "webtoon",
)

val uploadStatus = listOf(
    "All" to "",
    "Pending" to "pending",
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
)

val chapterCountOptions = listOf(
    "Any" to "", "0" to "0", "1+" to "1", "10+" to "10", "20+" to "20", "30+" to "30",
    "40+" to "40", "50+" to "50", "60+" to "60", "70+" to "70", "80+" to "80", "90+" to "90",
    "100+" to "100", "200+" to "200", "300+" to "300", "1~9" to "1-9", "10~19" to "10-19",
    "20~29" to "20-29", "30~39" to "30-39", "40~49" to "40-49", "50~59" to "50-59",
    "60~69" to "60-69", "70~79" to "70-79", "80~89" to "80-89", "90~99" to "90-99",
    "100~199" to "100-199", "200~299" to "200-299",
)

val sortOptions = listOf(
    "Rating Score" to "field_score", "Latest Update" to "field_update", "Recently Added" to "field_create",
    "Name A-Z" to "field_name_asc", "Name Z-A" to "field_name_desc", "Most Chapters" to "field_chapter",
    "Most Follows" to "field_follow", "Most Reviews" to "field_review", "Most Comments" to "field_comment",
    "Most Views (Total)" to "views_d000", "Most Views (360 days)" to "views_d360",
    "Most Views (180 days)" to "views_d180", "Most Views (90 days)" to "views_d090",
    "Most Views (30 days)" to "views_d030", "Most Views (7 days)" to "views_d007",
    "Most Views (24 hours)" to "views_h024", "Most Views (12 hours)" to "views_h012",
    "Most Views (6 hours)" to "views_h006", "Most Views (1 hour)" to "views_h001",
    "User Status (Plan to Read)" to "status_wish", "User Status (Reading)" to "status_doing",
    "User Status (Completed)" to "status_completed", "User Status (On Hold)" to "status_on_hold",
    "User Status (Dropped)" to "status_dropped", "User Status (Re-reading)" to "status_repeat",
)
