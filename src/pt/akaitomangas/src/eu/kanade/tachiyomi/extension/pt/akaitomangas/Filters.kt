package eu.kanade.tachiyomi.extension.pt.akaitomangas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<String>?) = FilterList(
    listOfNotNull(
        Filter.Header("Filtros são ignorados na busca por texto"),
        SectionFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter),
        AuthorFilter(),
        ArtistFilter(),
    ),
)

class SectionFilter : Filter.Select<String>("Seção", sections.map { it.first }.toTypedArray()) {
    val path get() = sections[state].second
}

private val sections = listOf(
    "Projetos" to "projetos",
    "Hentai" to "hentai",
)

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.joinToString(",") { it.value }
}

class StatusFilter :
    CheckBoxGroup(
        "Status",
        listOf(
            "Em Andamento" to "ACTIVE",
            "Em Dia" to "UP_TO_DATE",
            "Completo" to "FINISHED",
            "Hiato" to "HIATUS",
            "Dropado" to "DROPPED",
            "Em Breve" to "COMING_SOON",
        ),
    )

class GenreFilter(genres: List<String>) : CheckBoxGroup("Gêneros", genres.map { it to it })

class AuthorFilter : Filter.Text("Autor")

class ArtistFilter : Filter.Text("Artista")
