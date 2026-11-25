package eu.kanade.tachiyomi.extension.es.mangavault

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreOption>?) = FilterList(
    TypeFilter(),
    DemographicFilter(),
    StatusFilter(),
    if (genres != null) GenreFilter(genres) else Filter.Header("Pulsa «Restablecer» para cargar los géneros"),
    NsfwFilter(),
)

class TypeFilter :
    SelectFilter(
        "Tipo",
        arrayOf("Todos" to null, "Manga" to "manga", "Manhwa" to "manhwa", "Manhua" to "manhua", "Comic" to "comic", "Novela" to "novel"),
    )

class DemographicFilter :
    SelectFilter(
        "Demografía",
        arrayOf("Todas" to null, "Shonen" to "shonen", "Seinen" to "seinen", "Shojo" to "shojo", "Josei" to "josei"),
    )

class StatusFilter :
    SelectFilter(
        "Estado",
        arrayOf("Todos" to null, "En emisión" to "Publishing", "Finalizado" to "Finished", "Pausado" to "On Hiatus"),
    )

class GenreFilter(genres: List<GenreOption>) : SelectFilter("Género", arrayOf<Pair<String, String?>>("Todos" to null) + genres.map { it.label to it.value })

class NsfwFilter : SelectFilter("Contenido +18", arrayOf("Incluir" to "include", "Excluir" to "exclude", "Solo +18" to "only"))

open class SelectFilter(name: String, private val options: Array<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value: String?
        get() = options[state].second
}
