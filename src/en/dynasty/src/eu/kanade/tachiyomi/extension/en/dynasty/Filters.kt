package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

class SortFilter : Filter.Select<String>(
    name = "Sort",
    values = selectOptions.map { it.first }.toTypedArray(),
) {
    val sort get() = selectOptions[state].second
}

private val selectOptions = listOf(
    "Best Match" to "",
    "Alphabetical" to "name",
    "Date Added" to "created_at",
    "Release Date" to "released_on",
)

class TypeOption(name: String) : Filter.CheckBox(name, true)

class TypeFilter : Filter.Group<TypeOption>(
    name = "Type",
    state = typeOptions.map { TypeOption(it) },
) {
    val checked get() = state.filter { it.state }.map { it.name }
}

private val typeOptions = listOf(
    "Series",
    "Chapter",
    "Anthology",
    "Doujin",
    "Issue",
)

@Serializable
class GenreTag(
    private val id: Int,
    private val name: String,
    private val permalink: String,
) {
    val checkBoxOption get() = GenreCheckBox(id, name, permalink)
}

class GenreCheckBox(
    val id: Int,
    name: String,
    val permalink: String,
) : Filter.TriState(name)

class GenreFilter(
    tags: List<GenreTag>,
) : Filter.Group<GenreCheckBox>(
    name = "Tags",
    state = tags.map(GenreTag::checkBoxOption),
) {
    val included get() = state.filter { it.isIncluded() }
    val excluded get() = state.filter { it.isExcluded() }

    fun isEmpty() = included.isEmpty() && excluded.isEmpty()
}

abstract class TextFilter(name: String) : Filter.Text(name) {
    val values get() = state
        .split(",")
        .map(String::trim)
        .filterNot(String::isBlank)
}

class AuthorFilter : TextFilter("Author")

class ScanlatorFilter : TextFilter("Scanlator")
