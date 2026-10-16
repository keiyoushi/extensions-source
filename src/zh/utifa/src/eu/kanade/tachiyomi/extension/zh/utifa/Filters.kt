package eu.kanade.tachiyomi.extension.zh.utifa

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(
    val key: String,
    name: String,
    private val options: List<CategoryOptionDto>,
) : Filter.Select<String>(name, options.map { it.label }.toTypedArray()) {
    val value get() = options[state].value.takeIf { it != "all" }
}

class SortFilter(
    name: String,
    private val options: List<CategoryOptionDto>,
) : Filter.Sort(
    name,
    options.map { it.label }.toTypedArray(),
    Selection(options.indexOfFirst { it.value == "views" }.coerceAtLeast(0), false),
) {
    val value get() = options[state?.index ?: 0].value
    val order get() = if (state?.ascending == true) "asc" else "desc"
}
