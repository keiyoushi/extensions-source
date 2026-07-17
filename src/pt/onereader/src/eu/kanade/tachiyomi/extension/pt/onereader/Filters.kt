package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
internal class FilterData(
    val tags: List<String>,
    val types: List<String>,
)

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selected(): String = options[state].second
}

internal class OrderFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "A-Z" to "az",
            "Atualizados recentemente" to "recent",
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to "",
            "Em lançamento" to "Em Lançamento",
            "Completo" to "Completo",
            "Hiato" to "Hiatus",
            "Cancelado" to "Cancelado",
        ),
    )

internal class TypeFilter(types: List<String>) :
    SelectFilter(
        "Tipo",
        listOf("Todos" to "") + types.map { it to it },
    )

internal class TagFilter(tags: List<String>) :
    SelectFilter(
        "Tag",
        listOf("Todas" to "") + tags.map { it to it },
    )
