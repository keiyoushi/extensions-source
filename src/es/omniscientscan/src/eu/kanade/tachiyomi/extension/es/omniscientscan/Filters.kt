package eu.kanade.tachiyomi.extension.es.omniscientscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.jsoup.nodes.Element
import java.text.Collator
import java.util.Locale

const val SORT_RECENT = 0
const val SORT_VISITS = 1

private val TITLE_COLLATOR: Collator = Collator.getInstance(Locale("es"))

fun getFilters(data: Map<String, List<Pair<String, String>>>?): FilterList = FilterList(
    listOf(SortFilter()) + AttributeFilter.entries.mapNotNull { attribute ->
        data?.get(attribute.name)?.takeIf { it.isNotEmpty() }?.let { CardFilter(attribute, it) }
    },
)

enum class AttributeFilter(val param: String, val title: String) {
    TYPE("tipo", "Tipo"),
    STATUS("estado", "Estado"),
    GENRE("genero", "Género"),
    ;

    fun matches(card: Element, value: String) = when (this) {
        GENRE -> "|$value|" in card.attr("data-genres")
        else -> card.attr("data-$param") == value
    }
}

class CardFilter(private val attribute: AttributeFilter, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(attribute.title, (listOf("Todos") + options.map { it.first }).toTypedArray(), state) {
    fun matches(card: Element) = options.getOrNull(state - 1)?.let { attribute.matches(card, it.second) } ?: true
}

class SortFilter(state: Int = SORT_RECENT) : Filter.Select<String>("Ordenar", arrayOf("Recientes", "Más visitadas", "Más capítulos", "A-Z", "Z-A"), state) {
    val comparator: Comparator<Element>
        get() = when (state) {
            SORT_VISITS -> compareByDescending<Element> { it.number("data-visits") }.thenByDescending { it.number("data-ts") }
            2 -> compareByDescending<Element> { it.number("data-caps") }.thenByDescending { it.number("data-ts") }
            3 -> compareBy(TITLE_COLLATOR) { it.attr("data-title") }
            4 -> compareByDescending(TITLE_COLLATOR) { it.attr("data-title") }
            else -> compareByDescending { it.number("data-ts") }
        }
}

private fun Element.number(attribute: String) = attr(attribute).toLongOrNull() ?: 0L
