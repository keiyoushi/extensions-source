package eu.kanade.tachiyomi.extension.all.kyokotsu

import eu.kanade.tachiyomi.source.model.Filter

// ============================== Select ===============================
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

// ============================== Range ===============================
internal class MinFilter(name: String) : Filter.Text(name)
internal class MaxFilter(name: String) : Filter.Text(name)

abstract class RangeFilter(name: String, from: String, to: String, private val min: Int, private val max: Int) :
    Filter.Group<Filter<String>>(
        name = name,
        state = run {
            val minVal = "$from $min"
            val maxVal = "$to $max"
            listOf(MinFilter(minVal), MaxFilter(maxVal))
        },
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(min, max)?.toString()
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(min, max)?.toString()
}

// ============================== Filters ===============================
internal class OrderBy(name: String, data: List<Pair<String, String>>) : SelectFilter(name, data, "popular")
internal class TypeFilter(name: String, data: List<Pair<String, String>>) : SelectFilter(name, data, "")
internal class GenreFilter(name: String, options: List<Pair<String, String>>) : MultiValueFilter(name, options)
internal class StatusFilter(name: String, options: List<Pair<String, String>>) : MultiValueFilter(name, options)

// internal class ChaptersFilter(name: String, from: String, to: String, min: Int, max: Int) : RangeFilter(name, from, to, min, max)
// internal class RatingFilter(name: String, from: String, to: String, min: Int, max: Int) : RangeFilter(name, from, to, min, max)
internal class YearReleaseFilter(name: String, from: String, to: String, min: Int, max: Int) : RangeFilter(name, from, to, min, max)
internal class AgeLimit(name: String) : MultiValueFilter(name, data) {
    private companion object {
        private val data = listOf(
            "0+" to "0+",
            "6+" to "6+",
            "12+" to "12+",
            "16+" to "16+",
            "18+" to "18+",
        )
    }
}
