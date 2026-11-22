package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(state: Selection = Selection(0, false)) :
    Filter.Sort(
        "Ordenar por",
        OPTIONS.map { it.first }.toTypedArray(),
        state,
    ) {
    val sortBy get() = OPTIONS[state!!.index].second
    val ascending get() = state!!.ascending

    companion object {
        private val OPTIONS = listOf(
            "Mais Visualizadas" to "popular",
            "Lançamentos" to "release",
            "Última Atualização" to "updated",
            "Melhor Avaliação" to "rating",
            "A-Z" to "title",
        )
    }
}
