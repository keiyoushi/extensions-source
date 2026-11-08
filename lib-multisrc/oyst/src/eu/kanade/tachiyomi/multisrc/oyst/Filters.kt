package eu.kanade.tachiyomi.multisrc.oyst

import eu.kanade.tachiyomi.source.model.Filter
import org.jsoup.nodes.Element
import java.text.Collator
import java.util.Locale

class SortFilter(state: Int = SORT_UPDATED) : Filter.Select<String>("Trier par", SORTS.map { it.first }.toTypedArray(), state) {
    val comparator get() = SORTS[state].second
}

class OriginFilter : Filter.Select<String>("Origine", ORIGINS.map { it.first }.toTypedArray()) {
    fun matches(card: Element) = ORIGINS[state].second.let { it == null || card.attr("data-origin") == it }
}

const val SORT_UPDATED = 0
const val SORT_POPULAR = 1

// The orders of the site's catalogue.
private val SORTS = listOf<Pair<String, Comparator<Element>>>(
    "Récemment mis à jour" to compareByDescending { it.number("updated") },
    "Les plus populaires" to compareByDescending<Element> { it.number("score") }.thenByDescending { it.number("rating") },
    "Nouveaux projets" to compareByDescending { it.number("created") },
    "A à Z" to compareBy(Collator.getInstance(Locale.FRENCH)) { it.attr("data-title") },
    "Plus de chapitres" to compareByDescending { it.number("chaps") },
)

private val ORIGINS = listOf(
    "Toutes" to null,
    "OYST" to "oyst",
    "Uploaders" to "upl",
)

private fun Element.number(name: String) = attr("data-$name").toLongOrNull() ?: 0
