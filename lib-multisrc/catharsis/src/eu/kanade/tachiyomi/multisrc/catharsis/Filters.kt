package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(labels: List<String>, default: Int) : Filter.Sort("Ordenar por", labels.toTypedArray(), Selection(default, false))

class StatusFilter(private val statuses: List<String>) : Filter.Select<String>("Estado", (listOf("Todos") + statuses).toTypedArray()) {
    val value get() = statuses.getOrNull(state - 1)
}

class LetterFilter : Filter.Select<String>("Letra", LETTERS.toTypedArray()) {
    val value get() = LETTERS[state].takeIf { state > 0 }

    companion object {
        private val LETTERS = listOf("Todas", "#") + ('A'..'Z').map(Char::toString)
    }
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>("Géneros", genres)
