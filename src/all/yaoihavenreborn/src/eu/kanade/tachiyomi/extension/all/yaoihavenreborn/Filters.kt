package eu.kanade.tachiyomi.extension.all.yaoihavenreborn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class UriPartFilter(
    displayName: String,
    private val parameter: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    val queryParameter get() = parameter to options[state].second
}

class LanguageFilter :
    UriPartFilter(
        "Language",
        "language",
        arrayOf(
            "All" to "",
            "English" to "ENG",
            "Japanese" to "JPN",
            "Chinese" to "CHI",
            "Korean" to "KOR",
            "Spanish" to "SPA",
            "French" to "FRE",
            "German" to "GER",
            "Portuguese" to "POR",
            "Italian" to "ITA",
            "Russian" to "RUS",
            "No Language" to "ZZZ",
        ),
    )

class ColorFilter : UriPartFilter("Color", "color", arrayOf("All" to "", "Color only" to "1", "Black and white only" to "0"))

class TagMatchFilter : UriPartFilter("Included tags", "tagsearchtype", arrayOf("All of them" to "all", "Any of them" to "any"))

class TagFilter(name: String) : Filter.TriState(name)

class TagGroupFilter(name: String, tags: List<String>) : Filter.Group<TagFilter>(name, tags.map(::TagFilter))

fun getFilters(categories: List<CategoryDto>?) = FilterList(
    listOf(LanguageFilter(), ColorFilter(), TagMatchFilter()) +
        (categories?.map { TagGroupFilter(it.name, it.tagNames) } ?: listOf(Filter.Header("Press Reset to load the tags"))),
)
