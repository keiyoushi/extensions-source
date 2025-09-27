package eu.kanade.tachiyomi.extension.all.yabai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SelectFilter("Category", categories.keys.toList()),
        SelectFilter("Language", languages.keys.toList()),
    )
}

internal open class SelectFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state)

val categories = mapOf(
    "All" to "",
    "Doujinshi" to 1,
    "Manga" to 2,
    "Artist CG" to 3,
    "Game CG" to 4,
    "Western" to 5,
    "Non-H" to 6,
    "Image Set" to 7,
    "Cosplay" to 8,
    "Misc" to 9,
    "Asian Porn" to 10,
    "Private" to 11,
)

val languages = mapOf(
    "All" to "",
    "Japanese" to "jp",
    "English" to "gb",
    "Korean" to "kr",
    "Russian" to "ru",
    "Chinese" to "cn",
    "French" to "fr",
    "Italian" to "it",
    "Spanish" to "es",
    "Portuguese" to "pt",
    "German" to "de",
    "Thai" to "th",
    "Arabic" to "sa",
    "Turkish" to "tr",
    "Hebrew" to "il",
    "Tagalog" to "ph",
    "Ukrainian" to "ua",
    "Bulgarian" to "bg",
    "Dutch" to "nl",
    "Mongolian" to "mn",
    "Vietnamese" to "vn",
    "Macedonian" to "mk",
    "Polish" to "pl",
    "Hungarian" to "hu",
    "Norwegian" to "no",
    "Indonesian" to "id",
    "Lithuanian" to "lt",
    "Serbian" to "rs",
    "Persian" to "ir",
    "Croatian" to "hr",
    "Czech" to "cz",
    "Slovak" to "sk",
    "Romanian" to "ro",
    "Finnish" to "fi",
    "Greek" to "gr",
    "Swedish" to "se",
    "Latin" to "va",
)
