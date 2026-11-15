package eu.kanade.tachiyomi.extension.fr.bdplus

import eu.kanade.tachiyomi.source.model.Filter
import java.text.Collator
import java.util.Locale

private val titleCollator = Collator.getInstance(Locale.FRENCH)

class SortFilter :
    Filter.Sort(
        "Trier par",
        SORTS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    fun comparator(): Comparator<CorpusEntry> {
        val selection = state ?: Selection(0, false)
        val comparator = SORTS[selection.index].second
        return if (selection.ascending) comparator else comparator.reversed()
    }
}

class GenreFilter : Filter.Select<String>("Genre", GENRES.map { it.first }.toTypedArray()) {
    val selected get() = GENRES[state].second
}

private val SORTS = listOf<Pair<String, Comparator<CorpusEntry>>>(
    "Popularité" to compareBy { it.popularity ?: 0f },
    "Note" to compareBy { it.rating ?: 0f },
    "Titre" to compareBy(titleCollator) { it.title },
    "Année" to compareBy { it.year ?: 0 },
    "Nombre de tomes" to compareBy { it.nbTomes ?: 0 },
)

private val GENRES = listOf(
    "Tous" to null,
    "Science-Fiction" to "Science-Fiction",
    "Fantasy" to "Fantasy",
    "Aventure" to "Aventure",
    "Humour" to "Humour",
    "Historique" to "Historique",
    "Thriller / Policier" to "Thriller",
    "Horreur / Mystique" to "Horreur",
    "Western" to "Western",
    "Sport" to "Sport",
    "Tranche de vie" to "Tranche de vie",
    "Autre" to "Autre",
)
