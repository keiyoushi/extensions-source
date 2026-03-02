package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

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
        listOf(
            TagCheckBox("Ação", "action"),
            TagCheckBox("Aventura", "adventure"),
            TagCheckBox("Comédia", "comedy"),
            TagCheckBox("Drama", "drama"),
            TagCheckBox("Fantasia", "fantasy"),
            TagCheckBox("Terror", "horror"),
            TagCheckBox("Mistério", "mystery"),
            TagCheckBox("Romance", "romance"),
            TagCheckBox("Vida escolar", "school_life"),
            TagCheckBox("Sci-fi", "sci_fi"),
            TagCheckBox("Slice of life", "slice_of_life"),
            TagCheckBox("Esportes", "sports"),
            TagCheckBox("Sobrenatural", "supernatural"),
            TagCheckBox("Thriller", "thriller"),
            TagCheckBox("Tragédia", "tragedy"),
        ),
    )

class TagCheckBox(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

inline fun <reified T : Filter<*>> FilterList.find(): T? = this.filterIsInstance<T>().firstOrNull()

inline fun <reified T : ChoiceFilter> FilterList.valueOrEmpty(): String = find<T>()?.getValue().orEmpty()

fun FilterList.selectedTags(): List<String> = find<TagsFilter>()?.state
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
