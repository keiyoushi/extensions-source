package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(
    genres: List<Pair<String, String>> = emptyList(),
    tags: List<Pair<String, String>> = emptyList(),
): FilterList = FilterList(
    listOf(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
    ) + when {
        genres.isEmpty() && tags.isEmpty() -> emptyList()
        else -> listOf(Filter.Separator())
    } + when {
        genres.isEmpty() -> emptyList()
        else -> listOf(GenreFilter(genres))
    } + when {
        tags.isEmpty() -> emptyList()
        else -> listOf(TagFilter(tags))
    },
)

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    val selected: String get() = SORT_OPTIONS[state?.index ?: 0].second
    val order: String get() = if (state?.ascending == true) "asc" else "desc"

    companion object {
        private val SORT_OPTIONS = listOf(
            "Lançamentos recentes" to "recent",
            "Melhor avaliados" to "rating",
            "Ordem alfabética" to "title",
        )
    }
}

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String? get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to null,
            "Manhwa" to "manhwa",
            "Mangá" to "manga",
            "Manhua" to "manhua",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to null,
            "Em lançamento" to "ongoing",
            "Concluído" to "completed",
            "Em hiato" to "hiatus",
        ),
    )

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<GenreCheckBox>(
        "Gêneros",
        genres.map { GenreCheckBox(it.first, it.second) },
    )

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name, false)

class TagFilter(tags: List<Pair<String, String>>) :
    Filter.Group<TagCheckBox>(
        "Tags",
        tags.map { TagCheckBox(it.first, it.second) },
    )

class TagCheckBox(name: String, val id: String) : Filter.CheckBox(name, false)
