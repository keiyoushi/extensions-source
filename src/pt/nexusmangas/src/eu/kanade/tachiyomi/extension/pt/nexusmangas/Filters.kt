package eu.kanade.tachiyomi.extension.pt.nexusmangas

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterData(
    val genres: List<String> = emptyList(),
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Melhor avaliados" to "avg_rating.desc.nullslast",
            "Atualizados" to "updated_at.desc",
            "Adicionados" to "created_at.desc",
            "A-Z" to "title.asc",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to "",
            "Manhwa" to "MANHWA",
            "Mangá" to "MANGA",
            "Manhua" to "MANHUA",
            "Webtoon" to "WEBTOON",
            "Novel" to "NOVEL",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to "",
            "Em lançamento" to "RELEASING",
            "Completo" to "COMPLETED",
            "Hiato" to "HIATUS",
            "Cancelado" to "CANCELLED",
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demografia",
        listOf(
            "Todas" to "",
            "Shounen" to "SHOUNEN",
            "Seinen" to "SEINEN",
            "Shoujo" to "SHOUJO",
            "Josei" to "JOSEI",
        ),
    )

class GenreFilter(genres: List<String>) :
    SelectFilter(
        "Gênero",
        listOf("Todos" to "") + genres.map { it to it },
    )
