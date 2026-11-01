package eu.kanade.tachiyomi.extension.es.lectortmounoriginal

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterOption(val name: String, val value: String)

open class SelectFilter(
    name: String,
    private val options: List<FilterOption>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), state) {
    val selected get() = options[state].value
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            FilterOption("Más Recientes", "date"),
            FilterOption("Más Vistos", "views"),
            FilterOption("A – Z", "title"),
            FilterOption("Z – A", "title-desc"),
        ),
        state = 1,
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            FilterOption("Todos", ""),
            FilterOption("Manga", "manga"),
            FilterOption("Manhua", "manhua"),
            FilterOption("Manhwa", "manhwa"),
            FilterOption("Novel", "novel"),
            FilterOption("Oel", "oel"),
            FilterOption("One Shot", "one-shot"),
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demografía",
        listOf(
            FilterOption("Todas", ""),
            FilterOption("Shounen", "Shounen"),
            FilterOption("Shoujo", "Shoujo"),
            FilterOption("Seinen", "Seinen"),
            FilterOption("Josei", "Josei"),
            FilterOption("Kids", "Kids"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Estado",
        listOf(
            FilterOption("Todos", ""),
            FilterOption("En Emisión", "currently publishing"),
            FilterOption("Finalizado", "finished"),
            FilterOption("En pausa", "on hiatus"),
            FilterOption("No publicado", "not yet published"),
            FilterOption("Descontinuado", "discontinued"),
        ),
    )

class GenreFilter(genres: List<FilterOption>) : SelectFilter("Género", listOf(FilterOption("Todos", "")) + genres)
