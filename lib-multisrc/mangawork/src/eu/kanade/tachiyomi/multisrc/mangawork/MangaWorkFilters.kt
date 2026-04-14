package eu.kanade.tachiyomi.multisrc.mangawork

import eu.kanade.tachiyomi.source.model.Filter

// ==========Filters==========

internal interface MangaWorkQueryFilter {
    fun appendQueryParameters(parameters: MutableList<Pair<String, String>>)
}

internal open class MangaWorkSingleSelectFilter(
    title: String,
    val queryParam: String,
    private val options: Array<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    title,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it >= 0 } ?: 0,
),
    MangaWorkQueryFilter {

    val selectedValue: String
        get() = options[state].second

    override fun appendQueryParameters(parameters: MutableList<Pair<String, String>>) {
        selectedValue
            .takeIf { it.isNotEmpty() }
            ?.let { parameters += queryParam to it }
    }
}

internal class MangaWorkOrderFilter(
    title: String,
    queryParam: String,
    options: Array<Pair<String, String>>,
    defaultValue: String,
) : MangaWorkSingleSelectFilter(title, queryParam, options, defaultValue)

internal class MangaWorkStatusFilter(
    title: String,
    queryParam: String,
    options: Array<Pair<String, String>>,
) : MangaWorkSingleSelectFilter(title, queryParam, options)

internal class MangaWorkTypeFilter(
    title: String,
    queryParam: String,
    options: Array<Pair<String, String>>,
) : MangaWorkSingleSelectFilter(title, queryParam, options)

internal class MangaWorkCheckboxOption(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

internal open class MangaWorkMultiSelectFilter(
    title: String,
    private val queryParam: String,
    options: Array<Pair<String, String>>,
) : Filter.Group<MangaWorkCheckboxOption>(
    title,
    options.map { MangaWorkCheckboxOption(it.first, it.second) },
),
    MangaWorkQueryFilter {

    override fun appendQueryParameters(parameters: MutableList<Pair<String, String>>) {
        state.filter { it.state }
            .forEach { option ->
                parameters += queryParam to option.value
            }
    }
}

internal class MangaWorkGenreFilter(
    title: String,
    queryParam: String,
    options: Array<Pair<String, String>>,
) : MangaWorkMultiSelectFilter(title, queryParam, options)

internal class MangaWorkYearFilter(
    title: String,
    queryParam: String,
    options: Array<Pair<String, String>>,
) : MangaWorkMultiSelectFilter(title, queryParam, options)
