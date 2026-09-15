package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Locale

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
    // without `sortedBy{ it }` search page returns 404 error
    val included: List<String>?
        get() = state.filter { it.isIncluded() }.map { it.id }.sortedBy { it }.takeIf { it.isNotEmpty() }
    val excluded: List<String>?
        get() = state.filter { it.isExcluded() }.map { it.id }.sortedBy { it }.takeIf { it.isNotEmpty() }
    internal fun toPathSegment(prefix: String): String? {
        val path = listOfNotNull(
            included?.joinToString("-or-"),
            excluded?.joinToString("-nor-")?.let { "nor-$it" },
        ).joinToString("-").takeIf(String::isNotBlank)

        return path?.let { "$prefix-is-$it" }
    }
}

// ============================== Range filters ===============================
internal class MinFilter : Filter.Text("От")
internal class MaxFilter : Filter.Text("До")

abstract class RangeFilter(name: String) :
    Filter.Group<Filter<String>>(
        name = name,
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val ratingValue: String?
        get() = (state[0] as MinFilter).state.toFloatOrNull()?.let { v ->
            v.coerceIn(0f, 10f).let { "%.1f".format(Locale.ROOT, it) }
        }
    val ratingMaxValue: String?
        get() = (state[1] as MaxFilter).state.toFloatOrNull()?.let { v ->
            v.coerceIn(0f, 10f).let { "%.1f".format(Locale.ROOT, it) }
        }
    val intMinValue: String?
        get() = (state[0] as MinFilter).state.toIntOrNull()?.takeIf { it > 0 }?.toString()
    val intMaxValue: String?
        get() = (state[1] as MaxFilter).state.toIntOrNull()?.takeIf { it > 0 }?.toString()
    internal fun toPathSegment(prefix: String, isRating: Boolean = false): String? {
        val min = if (isRating) ratingValue else intMinValue
        val max = if (isRating) ratingMaxValue else intMaxValue
        return when {
            min != null && max != null -> "$prefix-from-$min-to-$max"
            min != null -> "$prefix-from-$min"
            max != null -> "$prefix-to-$max"
            else -> null
        }
    }
}

// ============================== Filter data ===============================
internal class OrderBy(data: List<Pair<String, String>>) : SelectFilter("Сортировать по", data, "rating")
internal class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)
internal class TagsFilter(data: List<Pair<String, String>>) : TriStateGroup("Теги", data)
internal class TypeFilter(data: List<Pair<String, String>>) : TriStateGroup("Тип", data)
internal class FormatFilter(data: List<Pair<String, String>>) : TriStateGroup("Формат выпуска", data)
internal class AgeFilter(data: List<Pair<String, String>>) : TriStateGroup("Возрастное ограничение", data)
internal class StatusFilter(data: List<Pair<String, String>>) : TriStateGroup("Статус", data)
internal class TranslationStatusFilter(data: List<Pair<String, String>>) : TriStateGroup("Статус перевода", data)
internal class CountryFilters(data: List<Pair<String, String>>) : TriStateGroup("Страна", data)
internal class ChaptersRangeFilter : RangeFilter("Количество глав")
internal class RatingRangeFilter : RangeFilter("Рейтинг")
internal class YearRangeFilter : RangeFilter("Год выпуска")
