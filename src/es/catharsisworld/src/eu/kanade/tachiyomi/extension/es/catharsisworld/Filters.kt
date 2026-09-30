package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(state: Selection = Selection(0, false)) : Filter.Sort("Ordenar por", SORTS.map { it.first }.toTypedArray(), state) {
    val value: String
        get() {
            val selection = state ?: Selection(0, false)
            val prefix = if (selection.ascending) "" else "-"
            return prefix + SORTS[selection.index].second
        }

    companion object {
        private val SORTS = listOf(
            "Última actualización" to "fecha_ultimo_capitulo",
            "Visitas" to "n_visitas",
            "Fecha de creación" to "date_created",
            "Nombre" to "nombre",
        )
    }
}

class StatusFilter : Filter.Select<String>("Estado", STATUSES.map { it.first }.toTypedArray()) {
    val value get() = STATUSES[state].second

    companion object {
        private val STATUSES = listOf(
            "Todos" to null,
            "En emisión" to "curso",
            "Hiatus" to "pausado",
            "Terminado" to "terminado",
        )
    }
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>("Géneros", genres)
