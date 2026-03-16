package eu.kanade.tachiyomi.extension.pt.pizzariascan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

fun getFilters() = FilterList(
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    YearFilter(),
    GenreFilter(),
)

abstract class SelectFilter<T>(
    name: String,
    val query: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class CheckBoxFilter<T>(name: String, val value: T) : Filter.CheckBox(name)

abstract class CheckBoxGroup<T>(
    name: String,
    val query: String,
    options: List<Pair<String, T>>,
) : Filter.Group<CheckBoxFilter<T>>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SortFilter :
    SelectFilter<String?>(
        name = "Ordenar por",
        query = "order",
        options = listOf(
            "A-Z" to "title",
            "Z-A" to "titlereverse",
            "Última atualização" to "update",
            "Últimos adicionados" to "latest",
            "Popularidade" to "popular",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        name = "Status",
        query = "status",
        options = listOf(
            "Todos" to null,
            "Publicando" to "Publishing",
            "Finalizado" to "Finished",
        ),
    )

class TypeFilter :
    SelectFilter<String?>(
        name = "Tipo",
        query = "type",
        options = listOf(
            "Todos" to null,
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

class YearFilter :
    CheckBoxGroup<String>(
        name = "Anos",
        query = "years[]",
        options = buildList {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            for (year in currentYear downTo 2021) {
                val y = year.toString()
                add(y to y)
            }
        },
    )

class GenreFilter :
    CheckBoxGroup<String>(
        name = "Gêneros",
        query = "genre[]",
        options = listOf(
            "Ação" to "acao",
            "Comédia" to "comedia",
            "Drama" to "drama",
            "Erotismo" to "erotismo",
            "Girls loves" to "girls-loves",
            "Harém" to "harem",
            "Psicológico" to "psicologico",
            "Romance" to "romance",
            "School life" to "school-life",
            "Vida escolar" to "vida-escolar",
        ),
    )
