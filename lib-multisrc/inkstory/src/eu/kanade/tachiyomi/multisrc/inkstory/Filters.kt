package eu.kanade.tachiyomi.multisrc.inkstory

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter :
    Filter.Sort(
        "Сортировать по",
        SORTS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    val selected get() = SORTS[state!!.index].second
    val order get() = if (state!!.ascending) "asc" else "desc"
}

internal class LabelOption(name: String, val id: String) : Filter.TriState(name)

internal open class LabelFilter(name: String, labels: List<Pair<String, String>>) : Filter.Group<LabelOption>(name, labels.map { LabelOption(it.first, it.second) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}

internal class GenreFilter(labels: List<Pair<String, String>>) : LabelFilter("Жанры", labels)

internal class TagFilter(labels: List<Pair<String, String>>) : LabelFilter("Теги", labels)

internal class StrictLabelEqualFilter : Filter.CheckBox("Строгое совпадение жанров и тегов")

internal class ValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal open class ValueFilter(name: String, values: List<Pair<String, String>>) : Filter.Group<ValueOption>(name, values.map { ValueOption(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.value }.ifEmpty { null }
}

internal class StatusFilter : ValueFilter("Статус", STATUSES)

internal class FormatFilter : ValueFilter("Формат", FORMAT_OPTIONS)

internal class CountryFilter : ValueFilter("Страна", COUNTRY_OPTIONS)

internal class ContentStatusFilter : ValueFilter("Возрастной рейтинг", CONTENT_STATUSES)

internal class RangeText(name: String) : Filter.Text(name)

internal open class RangeFilter(name: String) : Filter.Group<RangeText>(name, listOf(RangeText("От"), RangeText("До"))) {
    val min get() = state[0].state.trim().toIntOrNull()
    val max get() = state[1].state.trim().toIntOrNull()
}

internal class RatingFilter : RangeFilter("Рейтинг")

internal class YearFilter : RangeFilter("Год выпуска")

internal class ChaptersFilter : RangeFilter("Количество глав")

private val SORTS = listOf(
    "Просмотрам" to "viewsCount",
    "Лайкам" to "likesCount",
    "Главам" to "chaptersCount",
    "Закладкам" to "bookmarksCount",
    "Рейтингу" to "averageRating",
    "Дате создания" to "createdAt",
    "Дате последней главы" to "latestChapterAt",
)

private val STATUSES = listOf(
    "Анонс" to "ANNOUNCE",
    "Онгоинг" to "ONGOING",
    "Завершен" to "DONE",
    "Заморожен" to "FROZEN",
)

private val FORMAT_OPTIONS = FORMATS.map { (value, name) -> name to value }

private val COUNTRY_OPTIONS = COUNTRIES.map { (value, name) -> name to value }

private val CONTENT_STATUSES = listOf(
    "Безопасный" to "SAFE",
    "Неприемлемый" to "UNSAFE",
    "Эротика" to "EROTIC",
    "Порнография" to "PORNOGRAPHIC",
)
