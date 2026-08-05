package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.Filter

internal class TextFilter(name: String, val taxonomy: String) : Filter.Text(name)

internal class StatusTag(name: String, val slug: String) : Filter.CheckBox(name)

internal class StatusFilter(name: String, options: List<Pair<String, String>>) : Filter.Group<StatusTag>(name, options.map { StatusTag(it.first, it.second) })

internal class SortFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun key() = options[state].second
}

internal class AdultFilter(name: String, options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray())

internal class GenreConditionFilter(name: String, options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray())

internal class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

internal class GenreList(name: String, genres: List<GenreRoute>) : Filter.Group<GenreCheckBox>(name, genres.map { GenreCheckBox(it.name, it.slug) })

internal class SingleGenreFilter(name: String, allLabel: String, private val genres: List<GenreRoute>) : Filter.Select<String>(name, arrayOf(allLabel) + genres.map(GenreRoute::name).toTypedArray()) {
    fun route() = genres.getOrNull(state - 1)
}
