package eu.kanade.tachiyomi.extension.pt.pandasuki

import eu.kanade.tachiyomi.source.model.Filter
import org.jsoup.parser.Parser

class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val orderBy get() = OPTIONS[state].second
    val order get() = if (orderBy == "title") "asc" else "desc"

    private companion object {
        val OPTIONS = listOf(
            "Mais vistos" to "post_views",
            "Mais recentes" to "date",
            "Título" to "title",
        )
    }
}

class GenreOption(name: String, val id: Int) : Filter.TriState(name)

class GenreFilter(genres: List<GenreDto>) :
    Filter.Group<GenreOption>(
        "Gêneros",
        genres.map { GenreOption(Parser.unescapeEntities(it.name, false), it.id) },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}
