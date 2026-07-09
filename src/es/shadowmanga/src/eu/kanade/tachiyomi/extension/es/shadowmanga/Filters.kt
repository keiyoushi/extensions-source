package eu.kanade.tachiyomi.extension.es.shadowmanga

import eu.kanade.tachiyomi.source.model.Filter

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
