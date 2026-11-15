package eu.kanade.tachiyomi.extension.all.naisho

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

class CategoryFilter :
    Filter.Group<CategoryCheckBox>(
        "Categories",
        listOf(
            "Doujinshi", "Manga", "Artist CG", "Game CG", "Western", "Non-H", "Image Set", "Cosplay", "Misc", "Asian Porn",
        ).mapIndexed { index, name -> CategoryCheckBox(name, index + 1) },
    ) {
    val selected get() = state.filter { it.state }.map { it.id }
}

class LanguageFilter :
    Filter.Select<String>(
        "Language",
        arrayOf("All") + LANGUAGES.map { it.first },
    ) {
    val selected get() = LANGUAGES.getOrNull(state - 1)?.second

    companion object {
        // The site's codes are those of country flags
        private val LANGUAGES = listOf(
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
            "Albanian" to "al",
            "Burmese" to "mm",
            "Catalan" to "es-ct",
            "Cebuano" to "ceb",
            "Danish" to "dk",
            "Estonian" to "ee",
            "Hindi" to "in",
            "Icelandic" to "is",
            "Javanese" to "jv",
            "Khmer" to "kh",
            "Slovenian" to "si",
        )
    }
}

class TagsFilter : Filter.Text("Tags")

fun getFilters() = FilterList(
    CategoryFilter(),
    LanguageFilter(),
    Filter.Separator(),
    Filter.Header("Tags with their namespace, separated by commas; a leading - excludes one (female:glasses, -male:yaoi)"),
    TagsFilter(),
)
