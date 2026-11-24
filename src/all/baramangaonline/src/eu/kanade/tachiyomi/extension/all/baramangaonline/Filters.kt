package eu.kanade.tachiyomi.extension.all.baramangaonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class CategoryFilter(name: String, private val options: List<Pair<String, Int?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val id get() = options[state].second
}

class LanguageFilter : CategoryFilter("Language", listOf("All" to null) + LANGUAGES)

class TypeFilter : CategoryFilter("Category", listOf("All" to null) + TYPES)

fun getFilters() = FilterList(
    LanguageFilter(),
    TypeFilter(),
)

// The site's category ids, the languages without the ones whose posts are all videos
private val LANGUAGES = listOf(
    "Chinese" to 36,
    "English" to 3,
    "Filipino" to 716,
    "French" to 63,
    "Hungarian" to 786,
    "Indonesian" to 471,
    "Italian" to 502,
    "Japanese" to 4,
    "Korean" to 71,
    "Portuguese" to 188,
    "Russian" to 400,
    "Spanish" to 61,
    "Swedish" to 493,
    "Thai" to 73,
    "Vietnamese" to 127,
)

val TYPES = listOf(
    "3D" to 151,
    "AI" to 929,
    "Bisexual" to 300,
    "CG/Art" to 20,
    "Furry" to 33,
    "Short Story" to 213,
    "Softcore" to 56,
    "Tentacles" to 14,
)
