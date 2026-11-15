package eu.kanade.tachiyomi.extension.all.yaoimangaonline

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterData(val categories: List<Option>, val tags: List<Option>)

@Serializable
class Option(val name: String, val value: String)

class CategoryFilter(private val options: List<Option>) : Filter.Select<String>("Category", (listOf("All") + options.map { it.name }).toTypedArray()) {
    val selected get() = options.getOrNull(state - 1)?.value
}

class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TagFilter(tags: List<Option>) : Filter.Group<TagCheckBox>("Tags", tags.map { TagCheckBox(it.name, it.value) })
