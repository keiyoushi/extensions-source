package eu.kanade.tachiyomi.extension.all.peppercarrot

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(preferences: SharedPreferences): FilterList {
    val langData = preferences.langData
    val list: List<Filter<*>> = if (langData.isEmpty()) {
        listOf(Filter.Header("Tap 'Reset' to load languages"))
    } else {
        buildList(langData.size + 1) {
            add(Filter.Header("Languages"))
            val lang = preferences.lang.toHashSet()
            langData.mapTo(this) {
                LangFilter(it.key, "${it.name} (${it.progress})", it.key in lang)
            }
        }
    }
    return FilterList(list)
}

fun SharedPreferences.saveFrom(filters: FilterList) {
    val langFilters = filters.filterIsInstance<LangFilter>().ifEmpty { return }
    val selected = langFilters.filter { it.state }.mapTo(LinkedHashSet()) { it.key }
    val result = lang.filterTo(LinkedHashSet()) { it in selected }.apply { addAll(selected) }
    edit().setLang(result).apply()
}

class LangFilter(val key: String, name: String, state: Boolean) : Filter.CheckBox(name, state)
