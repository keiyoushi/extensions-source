package eu.kanade.tachiyomi.multisrc.inkstory

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
    val included: List<String>?
        get() = state.filter { it.isIncluded() }.map { it.id }.takeIf { it.isNotEmpty() }
    val excluded: List<String>?
        get() = state.filter { it.isExcluded() }.map { it.id }.takeIf { it.isNotEmpty() }
}

// ============================== Select ===============================
internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    values: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = values.map { MultiValueOption(it.first, it.second) },
) {
    val selected: List<String>?
        get() = state.filter { it.state }.map { it.value }.takeIf { it.isNotEmpty() }
}

// ============================== Range filters ===============================
internal class MinFilter : Filter.Text("От")
internal class MaxFilter : Filter.Text("До")

abstract class RangeFilter(name: String) :
    Filter.Group<Filter<String>>(
        name = name,
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeIf { it.isNotBlank() }
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeIf { it.isNotBlank() }
}

// ============================== Filter data ===============================
internal class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)

internal class OrderBy :
    OrderByFilter(
        "Сортировать по",
        listOf(
            "Просмотрам" to "viewsCount",
            "Лайкам" to "likesCount",
            "Главам" to "chaptersCount",
            "Закладкам" to "bookmarksCount",
            "Рейтингу" to "averageRating",
            "Дате добавления" to "createdAt",
        ),
        Selection(0, false),
    )

internal class StatusFilter :
    MultiValueFilter(
        name = "Статусы",
        values = listOf(
            "Онгоинг" to "ONGOING",
            "Завершен" to "DONE",
            "Заморожен" to "FROZEN",
            "Анонс" to "ANNOUNCE",
        ),
    )

internal class CountryFilter :
    MultiValueFilter(
        name = "Страны",
        values = listOf(
            "Россия" to "RUSSIA",
            "Япония" to "JAPAN",
            "Корея" to "KOREA",
            "Китай" to "CHINA",
            "Другое" to "OTHER",
        ),
    )

internal class ContentStatusFilter :
    MultiValueFilter(
        name = "Контент-статусы",
        values = listOf(
            "Безопасный" to "SAFE",
            "Небезопасный" to "UNSAFE",
            "Эротический" to "EROTIC",
        ),
    )

internal class FormatFilter :
    MultiValueFilter(
        name = "Форматы",
        values = listOf(
            "Енкома" to "FOURTH_KOMA",
            "Сборник" to "COMPILATION",
            "Додзинси" to "DOUJINSHI",
            "Вебтун" to "WEBTOON",
            "Цветной" to "COLORED",
            "Артбук" to "ARTBOOK",
            "Сингл" to "SINGLE",
            "Лайт" to "LIGHT",
            "Веб" to "WEB",
        ),
    )

internal class StrictLabelEqualFilter :
    Filter.CheckBox(
        "Строгое совпадение жанров",
    )

internal class ChaptersRangeFilter : RangeFilter("Количество глав")
internal class RatingRangeFilter : RangeFilter("Рейтинг")
internal class YearRangeFilter : RangeFilter("Год выпуска")
