package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.Filter

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selected(): String = options[state].second
}

internal class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Mais vistos" to "VIEWS",
            "A-Z" to "AZ",
            "Recentes" to "DATE",
            "Melhor avaliados" to "RATING",
        ),
    )

internal class FormatFilter :
    SelectFilter(
        "Formato",
        listOf(
            "Todos" to "ALL",
            "Webtoon" to "WEBTOON",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
            "Mangá" to "MANGÁ",
            "Shoujo" to "SHOUJO",
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to "ALL",
            "Em lançamento" to "LANÇANDO",
            "Completo" to "COMPLETO",
            "Hiato" to "HIATO",
            "Cancelado" to "CANCELADO",
        ),
    )

internal class AllGenresFilter : Filter.CheckBox("Exigir todos os gêneros")

internal class GenreOption(name: String) : Filter.TriState(name)

internal class GenreFilter(genres: List<String>) : Filter.Group<GenreOption>("Gêneros", genres.map(::GenreOption))
