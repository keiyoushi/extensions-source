package eu.kanade.tachiyomi.extension.uk.mangarama

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

// ============================== Order ===============================
abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeIf { it.isNotBlank() }
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

// ============================== Year ===============================
internal class MinFilter(data: Int) : Filter.Text("Від $data")
internal class MaxFilter(data: Int) : Filter.Text("До $data")

internal class YearRangeFilter :
    Filter.Group<Filter<String>>(
        name = "Рік випуску",
        state = run {
            val minYear = MIN_YEAR
            val maxYear = maxYear
            listOf(MinFilter(minYear), MaxFilter(maxYear))
        },
    ) {
    private companion object {
        const val MIN_YEAR = 2019
        val maxYear = Calendar.getInstance().get(Calendar.YEAR)
    }
    val minValue: String? get() = (state[0] as MinFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(MIN_YEAR, maxYear)?.toString()
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(MIN_YEAR, maxYear)?.toString()
}

// ============================== Filters ===============================
internal class GenreFilter(name: String, options: List<Pair<String, String>>) : TriStateGroup(name, options)
internal class TypeFilter(name: String, options: List<Pair<String, String>>) : MultiValueFilter(name, options)
internal class StatusFilter(name: String, options: List<Pair<String, String>>) : MultiValueFilter(name, options)
internal class TranslatorsFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, options)
internal class OrderBy(name: String, data: List<Pair<String, String>>) : SelectFilter(name, data, "popular")
internal class AgeLimit(name: String) : SelectFilter(name, data, "") {
    private companion object {
        private val data = listOf(
            "Усі" to "",
            "Без 18+" to "sfw",
            "18+" to "adult",
        )
    }
}
