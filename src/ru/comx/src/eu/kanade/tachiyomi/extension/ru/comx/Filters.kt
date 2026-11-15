package eu.kanade.tachiyomi.extension.ru.comx

import eu.kanade.tachiyomi.source.model.Filter

class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }.takeIf { it.isNotEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }.takeIf { it.isNotEmpty() }
}

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

internal class OrderBy :
    OrderByFilter(
        "Сортировать по",
        listOf(
            "Дате" to "date",
            "Дате изменения" to "editdate",
            "Популярности" to "rating",
            "Посещаемости" to "news_read",
            "Комментариям" to "comm_num",
            "Алфавиту" to "ltitle",
        ),
        Selection(2, false),
    )

internal class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)
internal class GroupFilter(data: List<Pair<String, String>>) : TriStateGroup("Разделы", data)
internal class TypeFilter(data: List<Pair<String, String>>) : TriStateGroup("Тип выпуска", data)
internal class StatusFilter(data: List<Pair<String, String>>) : TriStateGroup("Статус", data)

internal class MinFilter : Filter.Text("От")
internal class MaxFilter : Filter.Text("До")

internal class YearRangeFilter :
    Filter.Group<Filter<String>>(
        name = "Год выпуска",
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeIf { it.isNotBlank() }
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeIf { it.isNotBlank() }
}
