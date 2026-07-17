package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class OrderByFilter :
    Filter.Sort(
        "Сортировать по",
        SORT_ENTRIES.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    fun toKey(): String = SORT_ENTRIES[state?.index ?: 0].second
    fun toDescending(): Boolean = state?.ascending != true
}

class Check(name: String, val id: String) : Filter.CheckBox(name)

class TypeGroup(types: List<Check>) : Filter.Group<Check>("Тип тайтла", types)
class StatusGroup(statuses: List<Check>) : Filter.Group<Check>("Статус тайтла", statuses)
class TranslationStatusGroup(statuses: List<Check>) : Filter.Group<Check>("Статус перевода", statuses)
class PegiGroup(ratings: List<Check>) : Filter.Group<Check>("Возрастное ограничение", ratings)

abstract class YearFilter(name: String) : Filter.Text(name, "") {
    fun toQueryValue(): String? {
        val trimmed = state.trim()
        if (trimmed.length != 4) return null
        val year = trimmed.toIntOrNull() ?: return null
        return year.coerceIn(YEAR_MIN, YEAR_MAX).toString()
    }
}

class YearFromFilter : YearFilter("От (Минимум: $YEAR_MIN)")
class YearToFilter : YearFilter("До (Максимум: $YEAR_MAX)")

class YearGroup(filters: List<Filter<*>>) : Filter.Group<Filter<*>>("Год", filters)

// ── data ─────────────────────────────────────────────────────────────────────

private val SORT_ENTRIES = arrayOf(
    "Обновлениям" to "updatedAt",
    "Лайкам" to "likes",
    "Новизне" to "createdAt",
    "Просмотрам" to "views",
    "Алфавиту" to "alphabetical",
)

private const val YEAR_MIN = 1990
private const val YEAR_MAX = 2100

val typeList = listOf(
    Check("Манга", "manga"),
    Check("Манхва", "manhwa"),
    Check("Манхуа", "manhua"),
    Check("Комикс", "comic"),
    Check("Рукопись", "manuscript"),
)

val statusList = listOf(
    Check("Онгоинг", "ongoing"),
    Check("Завершено", "completed"),
    Check("Перерыв", "hiatus"),
    Check("Отменено", "cancelled"),
    Check("Неизвестно", "unknown"),
    Check("Заброшено", "abandoned"),
    Check("Анонсировано", "announced"),
)

val translationStatusList = listOf(
    Check("Онгоинг", "ongoing"),
    Check("Завершено", "completed"),
    Check("Перерыв", "hiatus"),
    Check("Отменено", "cancelled"),
    Check("Неизвестно", "unknown"),
    Check("Заброшено", "abandoned"),
    Check("Анонсировано", "announced"),
)

val pegiList = listOf(
    Check("3+", "3+"),
    Check("6+", "6+"),
    Check("12+", "12+"),
    Check("16+", "16+"),
    Check("18+", "18+"),
)

fun defaultFilters() = FilterList(
    OrderByFilter(),
    TypeGroup(typeList),
    StatusGroup(statusList),
    TranslationStatusGroup(translationStatusList),
    PegiGroup(pegiList),
    YearGroup(listOf(YearFromFilter(), YearToFilter())),
)
