package eu.kanade.tachiyomi.extension.en.mangahot

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

open class UriPartFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun key() = options[state].second
}

class SortFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options)

class ContentRatingFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options)

class TextFilter(name: String, val param: String) : Filter.Text(name)

class StatusTag(name: String, val slug: String) : Filter.CheckBox(name)

class StatusFilter(name: String, options: List<Pair<String, String>>) : Filter.Group<StatusTag>(name, options.map { StatusTag(it.first, it.second) })

class MatchAllGenresFilter : Filter.CheckBox("Match all selected genres", true)

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreList(name: String, genres: List<GenreDto>) : Filter.Group<GenreCheckBox>(name, genres.map { GenreCheckBox(it.name, it.slug) })
