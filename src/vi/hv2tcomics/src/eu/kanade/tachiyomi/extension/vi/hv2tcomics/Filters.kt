package eu.kanade.tachiyomi.extension.vi.hv2tcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
class TagOption(val id: Int, val name: String, val slug: String)

@Serializable
class TranslatorOption(val name: String)

class GenreFilter(tags: List<TagOption>) :
    Filter.Group<TagTriState>(
        "Thể loại",
        tags.map { TagTriState(it.name, it.slug) },
    ) {
    fun selectedSlugs(): FilterState {
        val include = state.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.slug }
        val exclude = state.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.slug }
        return FilterState(include, exclude)
    }
}

class TranslatorFilter(translators: List<TranslatorOption>) :
    Filter.Group<TranslatorTriState>(
        "Nhóm dịch / Dịch giả",
        translators.map { TranslatorTriState(it.name) },
    ) {
    fun selectedNames(): FilterState {
        val include = state.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.name }
        val exclude = state.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.name }
        return FilterState(include, exclude)
    }
}

class TagTriState(name: String, val slug: String) : Filter.TriState(name)

class TranslatorTriState(name: String) : Filter.TriState(name)

data class FilterState(
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
)

fun getFilters(tags: List<TagOption>?, translators: List<TranslatorOption>?): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (!tags.isNullOrEmpty()) filters += GenreFilter(tags)
    if (!translators.isNullOrEmpty()) filters += TranslatorFilter(translators)
    return FilterList(filters)
}
