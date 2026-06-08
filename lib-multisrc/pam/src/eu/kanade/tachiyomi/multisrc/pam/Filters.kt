package eu.kanade.tachiyomi.multisrc.pam

import eu.kanade.tachiyomi.source.model.Filter

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SortFilter(
    name: String,
    private val sortValues: List<Pair<String, String>>,
    selection: Selection = Selection(0, false),
) : Filter.Sort(
    name = name,
    values = sortValues.map { it.first }.toTypedArray(),
    state = selection,
) {
    val sort get() = sortValues[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false
}
