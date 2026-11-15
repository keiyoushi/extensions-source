package eu.kanade.tachiyomi.extension.pt.roxinha

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    val selected get() = vals[state].second
}

class SortFilter(
    state: Selection = Selection(2, false),
    private val options: List<String> = listOf(
        "title",
        "updated",
        "views",
        "rating",
    ),
) : Filter.Sort(
    "Ordenar por",
    arrayOf("Título", "Atualização", "Visualizações", "Avaliação"),
    state,
) {
    val selected get() = options[state?.index ?: 0]
    val order get() = if (state?.ascending == true) "ASC" else "DESC"

    companion object {
        val LATEST get() = FilterList(SortFilter(Selection(1, false)))
        val POPULAR get() = FilterList(SortFilter(Selection(2, false)))
    }
}

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Em andamento" to "ongoing",
            "Concluído" to "completed",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        arrayOf(
            "Todos" to "",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Webtoon" to "webtoon",
        ),
    )
