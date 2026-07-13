package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class OrderByFilter :
    Filter.Sort(
        "Сортировать по",
        arrayOf("Лайкам", "Обновлениям", "Новизне", "Просмотрам", "Алфавиту"),
        Selection(0, false),
    ) {
    fun toKey(): String = SORT_FIELDS[state?.index ?: 0]
    fun toDescending(): Boolean = state?.ascending != true
}

class Check(name: String, val id: String) : Filter.CheckBox(name)

class TypeGroup(types: List<Check>) : Filter.Group<Check>("Тип тайтла", types)
class StatusGroup(statuses: List<Check>) : Filter.Group<Check>("Статус тайтла", statuses)
class TranslationStatusGroup(statuses: List<Check>) : Filter.Group<Check>("Статус перевода", statuses)
class PegiGroup(ratings: List<Check>) : Filter.Group<Check>("Возрастное ограничение", ratings)

class YearFromFilter : Filter.Text("От (Минимум: 1990)", "")
class YearToFilter : Filter.Text("До (Максимум: 2100)", "")
class YearGroup(filters: List<Filter<*>>) : Filter.Group<Filter<*>>("Год", filters)

// ── data ─────────────────────────────────────────────────────────────────────

private val SORT_FIELDS = arrayOf("likes", "updatedAt", "createdAt", "views", "alphabetical")

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

val ALL_TYPES = typeList.map { it.id }
val ALL_STATUSES = statusList.map { it.id }
val ALL_TRANSLATION_STATUSES = translationStatusList.map { it.id }
val ALL_PEGI = pegiList.map { it.id }

fun defaultFilters() = FilterList(
    OrderByFilter(),
    TypeGroup(typeList),
    StatusGroup(statusList),
    TranslationStatusGroup(translationStatusList),
    PegiGroup(pegiList),
    YearGroup(listOf(YearFromFilter(), YearToFilter())),
)
