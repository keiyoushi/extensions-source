package eu.kanade.tachiyomi.extension.uk.faust

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name = name,
    values = options.map { it.first }.toTypedArray(),
    state = options.indexOfFirst { it.second == defaultValue }.takeIf { it >= 0 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isBlank() }
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    val options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }.takeUnless { it.isEmpty() }
}

class GenresFilter : TriStateGroup("Жанри", options) {
    companion object {
        var options = emptyList<Pair<String, String>>()
    }
}

class TagsFilter : TriStateGroup("Теги", options) {
    companion object {
        var options = emptyList<Pair<String, String>>()
    }
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
}

internal class OrderBy :
    OrderByFilter(
        "Сортувати за",
        listOf(
            "Оцінками" to "rating",
            "Популярністю" to "popularity",
            "Алфавітом" to "alphabet",
            "Останні оновлення" to "updated",
            "Нові тайтли" to "newest",
        ),
        Selection(0, true),
    )
internal class CategoriesFilter :
    SelectFilter(
        name = "Тип",
        options = listOf(
            "Всі категорї" to "",
            "Манґа" to "Manga",
            "Манхва" to "Manhwa",
            "Маньхва" to "Manhua",
            "Ваншот" to "Oneshot",
            "Вебкомікс" to "Webcomic",
            "Доджінші" to "Doujinshi",
            "Екстра" to "Extra",
            "Комікс" to "Comics",
            "Мальопис" to "Malyopys",
        ),
    )
internal class TranslationStatusFilter :
    SelectFilter(
        name = "Статус перекладу",
        options = listOf(
            "Будь-який статус" to "",
            "Покинуто" to "Inactive",
            "Перекладено" to "Translated",
            "Перекладається" to "Active",
        ),
    )
internal class PublicationStatusFilter :
    SelectFilter(
        name = "Статус виходу",
        options = listOf(
            "Будь-який статус" to "",
            "Триває" to "Ongoing",
            "Призупинено" to "Paused",
            "Закінчено" to "Completed",
            "Гіатус" to "Hiatus",
        ),
    )
internal class AgeStatusFilter :
    SelectFilter(
        name = "Вікова категорія",
        options = listOf(
            "Будь-якa категорія" to "",
            "Для всіх" to "FitForAll",
            "13+" to "ThirteenPlus",
            "16+" to "SixteenPlus",
            "18+" to "AdultsOnly",
        ),
    )
internal class MinFilter : Filter.Text("Від")
internal class MaxFilter : Filter.Text("До")

internal class ChaptersRangeFilter :
    Filter.Group<Filter<String>>(
        name = "Кількість розділів",
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeUnless { it.isBlank() }
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeUnless { it.isBlank() }
}

internal class YearRangeFilter :
    Filter.Group<Filter<String>>(
        name = "Рік виходу",
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeUnless { it.isBlank() }
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeUnless { it.isBlank() }
}
