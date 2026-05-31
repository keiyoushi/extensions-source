package eu.kanade.tachiyomi.extension.all.junmeitu

import eu.kanade.tachiyomi.source.model.Filter

class SelectFilterOption(val name: String, val value: String = name)

abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
    val selected: String
        get() = options[state].value
    val slug: String
        get() = options[state].name
}

class TagFilter : Filter.Text("Tag")
class ModelFilter : Filter.Text("Model")
class GroupFilter : Filter.Text("Group")
class CategoryFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Category", options, default)
class SortFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort", options, default)

internal fun getCategoryFilter() = listOf(
    SelectFilterOption("beauty", "6"),
    SelectFilterOption("handsome", "5"),
    SelectFilterOption("news", "30"),
    SelectFilterOption("street", "32"),
)

internal fun getSortFilter() = listOf(
    SelectFilterOption("default", "index"),
    SelectFilterOption("hot"),
)
