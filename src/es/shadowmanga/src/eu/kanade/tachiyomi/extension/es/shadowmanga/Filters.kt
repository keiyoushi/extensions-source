package eu.kanade.tachiyomi.extension.es.shadowmanga

import eu.kanade.tachiyomi.source.model.Filter

class SectionFilter :
    Filter.Select<String>(
        "Sección",
        arrayOf("Predeterminado", "Todo público (SFW)", "Adultos (+18)"),
    )

class OrderByFilter :
    Filter.Select<String>(
        "Orden (solo sección Adultos)",
        arrayOf("Recientes", "Más vistos", "A-Z"),
    )

open class UriMultiTriStateOption(name: String, val value: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) :
    UriMultiTriStateFilter(
        name = "Géneros",
        vals = genres,
    )

open class UriMultiTriStateFilter(
    name: String,
    vals: List<String>,
) : Filter.Group<UriMultiTriStateOption>(
    name,
    vals.map { UriMultiTriStateOption(it, it) },
) {
    fun getIncluded(): List<String> = state.filter { it.isIncluded() }.map { it.value }

    fun getExcluded(): List<String> = state.filter { it.isExcluded() }.map { it.value }
}
