package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Sort("Ordenar por", SORTS.map { it.first }.toTypedArray(), DEFAULT) {
    val value: String
        get() {
            val selection = state ?: DEFAULT
            val prefix = if (selection.ascending) "" else "-"
            return prefix + SORTS[selection.index].second
        }

    companion object {
        private val DEFAULT = Selection(1, false)
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
            "Abandonado" to "abandonado",
        )
    }
}

class LetterFilter : Filter.Select<String>("Letra", LETTERS.toTypedArray()) {
    val value get() = LETTERS[state].takeIf { state > 0 }

    companion object {
        private val LETTERS = listOf("Todas", "#") + ('A'..'Z').map(Char::toString)
    }
}

class ScanFilter(scans: List<Pair<String, String>>) : Filter.Select<String>("Grupo", (listOf("Todos" to null) + scans).map { it.first }.toTypedArray()) {
    private val ids = listOf(null) + scans.map { it.second }
    val value get() = ids[state]
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>("Géneros", genres)
