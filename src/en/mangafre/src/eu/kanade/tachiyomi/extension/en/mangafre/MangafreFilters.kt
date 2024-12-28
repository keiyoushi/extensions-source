package eu.kanade.tachiyomi.extension.en.mangafre

import eu.kanade.tachiyomi.source.model.Filter

object Note : Filter.Header("NOTE: Ignored if using text search!")

class SelectFilterOption(val name: String, val value: String)

abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
    val selected: String
        get() = options[state].value
}

class GenreFilter(
    options: List<SelectFilterOption>,
    default: Int,
) : SelectFilter("Genre", options, default)

fun getGenreFilter(lang: String): List<SelectFilterOption> {
    val allGenres: Map<String, List<SelectFilterOption>> = mapOf(
        "en" to listOf(
            SelectFilterOption("All", ""),
            SelectFilterOption("Romance", "169"),
            SelectFilterOption("Action", "170"),
            SelectFilterOption("Comedy", "171"),
        ),
        "id" to emptyList(),
        "zh" to emptyList(),
    )
    return allGenres[lang] ?: emptyList()
}
