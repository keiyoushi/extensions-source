package eu.kanade.tachiyomi.extension.ru.mangamen

import eu.kanade.tachiyomi.source.model.Filter

// ============================== Order ===============================
abstract class OrderByFilter(
    displayName: String,
    val options: List<Pair<String, String>>,
    state: Selection,
) : Filter.Sort(
    displayName,
    options.map { it.first }.toTypedArray(),
    state,
) {
    val selected get() = options[state!!.index].second
    val order get() = if (state!!.ascending) "asc" else "desc"
}

// ============================== TriState ===============================
internal class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

internal abstract class TriStateGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included: List<String>? get() = state.filter { it.isIncluded() }.map { it.id }.takeIf { it.isNotEmpty() }
    val excluded: List<String>? get() = state.filter { it.isExcluded() }.map { it.id }.takeIf { it.isNotEmpty() }
}

// ============================== Multi Value ===============================
internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = options.map { MultiValueOption(it.first, it.second) },
) {
    val selected get() = state.filter { it.state }.map { it.value }.takeIf { it.isNotEmpty() }
}

// ============================== Range filters ===============================
internal class MinFilter : Filter.Text("От")
internal class MaxFilter : Filter.Text("До")

abstract class RangeFilter(name: String) :
    Filter.Group<Filter<String>>(
        name = name,
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it > 0 }?.toString()
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it > 0 }?.toString()
}

// ============================== Filter data ===============================
internal class OrderBy :
    OrderByFilter(
        "Сортировать по",
        listOf(
            "Названию (A-Z)" to "name",
            "Просмотрам" to "views",
            "Дате добавления" to "created_at",
            "Дате обновления" to "last_chapter_at",
        ),
        Selection(1, false),
    )

internal class YearRangeFilter : RangeFilter("Год выпуска")
internal class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)
internal class TagsFilter(data: List<Pair<String, String>>) : TriStateGroup("Теги", data)
internal class StatusFilter(data: List<Pair<String, String>>) : MultiValueFilter("Статус тайтла", data)
internal class TypeFilter(data: List<Pair<String, String>>) : MultiValueFilter("Тип", data)
internal class TranslationStatusFilter(data: List<Pair<String, String>>) : MultiValueFilter("Статус перевода", data)
