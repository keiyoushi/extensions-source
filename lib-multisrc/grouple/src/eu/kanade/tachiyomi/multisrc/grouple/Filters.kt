package eu.kanade.tachiyomi.multisrc.grouple

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

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

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class OrderBy(data: List<Pair<String, String>>, defaultOrder: String? = null) : SelectFilter("Сортировать по", data, defaultOrder)
class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)
class TagsFilter(data: List<Pair<String, String>>) : TriStateGroup("Теги", data)
class CategoryFilter(data: List<Pair<String, String>>) : TriStateGroup("Категории", data)
class AnotherFilter(data: List<Pair<String, String>>) : TriStateGroup("Прочее", data)
class LimitationFilter(data: List<Pair<String, String>>) : TriStateGroup("Возрастная рекомендация", data)
class StatusFilter(data: List<Pair<String, String>>) : TriStateGroup("Статус выхода", data)
class TranslationStatusFilter(data: List<Pair<String, String>>) : TriStateGroup("Статус перевода", data)
class AdditionalFilters(data: List<Pair<String, String>>) : TriStateGroup("Фильтры", data)

class YearRangeFilter(data: YearsData?) :
    Filter.Group<Filter<String>>(
        name = "Год выпуска",
        state = run {
            val minYear = data?.min ?: 1950
            val maxYear = data?.max ?: Calendar.getInstance().get(Calendar.YEAR)
            listOf(MinFilter(minYear), MaxFilter(maxYear))
        },
    ) {
    val minYear = data?.min ?: 1950
    val maxYear = data?.max ?: Calendar.getInstance().get(Calendar.YEAR)
    val minValue: String get() = (state[0] as MinFilter).state.takeUnless { it.isBlank() } ?: minYear.toString()
    val maxValue: String get() = (state[1] as MaxFilter).state.takeUnless { it.isBlank() } ?: maxYear.toString()
}

internal class MinFilter(data: Int) : Filter.Text("От $data")
internal class MaxFilter(data: Int) : Filter.Text("До $data")
