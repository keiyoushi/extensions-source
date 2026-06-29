package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal val tagMapping = mapOf(
    "action" to "Ação",
    "adventure" to "Aventura",
    "comedy" to "Comédia",
    "drama" to "Drama",
    "fantasy" to "Fantasia",
    "horror" to "Terror",
    "mystery" to "Mistério",
    "romance" to "Romance",
    "school_life" to "Vida escolar",
    "sci_fi" to "Sci-fi",
    "slice_of_life" to "Slice of life",
    "sports" to "Esportes",
    "supernatural" to "Sobrenatural",
    "thriller" to "Thriller",
    "tragedy" to "Tragédia",
)

class SeriesTypeFilter :
    ChoiceFilter(
        "Tipo",
        arrayOf(
            "" to "Todos",
            "MANGA" to "Mangá",
            "MANHWA" to "Manhwa",
            "MANHUA" to "Manhua",
            "COMIC" to "Comic",
            "WEBTOON" to "Webtoon",
        ),
    )

class StatusFilter :
    ChoiceFilter(
        "Status",
        arrayOf(
            "" to "Todos",
            "ONGOING" to "Em andamento",
            "COMPLETED" to "Completo",
            "HIATUS" to "Hiato",
            "CANCELLED" to "Cancelado",
        ),
    )

open class ChoiceFilter(
    name: String,
    private val entries: Array<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    entries.map { it.second }.toTypedArray(),
) {
    fun getValue(): String = entries[state].first
}

class TagsFilter :
    Filter.Group<TagCheckBox>(
        "Tags",
        tagMapping.map { TagCheckBox(it.value, it.key) },
    )

class TagCheckBox(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

inline fun <reified T : ChoiceFilter> FilterList.valueOrEmpty(): String = firstInstanceOrNull<T>()?.getValue().orEmpty()

fun FilterList.selectedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state }
    ?.map { it.value }
    .orEmpty()

object LycanToonsFilters {
    fun get(): FilterList = FilterList(
        SeriesTypeFilter(),
        StatusFilter(),
        TagsFilter(),
    )
}
