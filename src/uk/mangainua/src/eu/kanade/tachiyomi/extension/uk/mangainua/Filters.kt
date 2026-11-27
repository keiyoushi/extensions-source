package eu.kanade.tachiyomi.extension.uk.mangainua

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.map { it.second }.indexOf(defaultValue).coerceAtLeast(0),
) {
    val selected get() = options[state].second.takeUnless { it.isBlank() }
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }.takeUnless { it.isEmpty() }
}

class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

abstract class MultiValueFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = options.map { MultiValueOption(it.first, it.second) },
) {
    val selected get() = state.filter { it.state }.map { it.value }.takeIf { it.isNotEmpty() }
}

internal class MinFilter : Filter.Text("Від")
internal class MaxFilter : Filter.Text("До")

abstract class RangeFilter(name: String) :
    Filter.Group<Filter<String>>(
        name = name,
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val min: Int?
        get() = (state[0] as MinFilter).state.toIntOrNull()?.takeIf { it > 0 }
    val max: Int?
        get() = (state[1] as MaxFilter).state.toIntOrNull()?.takeIf { it > 0 }
}

class SortFilter(defaultOrder: String? = null) : SelectFilter("Сортувати", sort, defaultOrder) {
    companion object {
        private val sort = listOf(
            "Популярні за місяць" to "pop",
            "Популярні за тиждень" to "week",
            "Нові на сайті" to "new",
            "За оцінкою" to "rate",
            "Найбільше коментарів" to "comm",
            "Найбільше переглядів" to "views",
            "Найбільше розділів" to "ch",
            "За назвою (А–Я)" to "name",
            "Рік: новіші" to "year",
            "Рік: старіші" to "old",
        )
    }
}

class CategoriesFilter :
    MultiValueFilter(
        name = "Тип",
        options = listOf(
            "Манґа" to "manga",
            "Манхва" to "manhwa",
            "Маньхва" to "manhua",
            "Вебманхва" to "web-manhwa",
            "Вебманьхва" to "web-manhua",
            "Ваншот" to "one-shot",
            "Доджінші" to "dojinshi",
            "Мальопис" to "malopys",
        ),
    )

class YearsFilter : RangeFilter(name = "Рік")

class SizeFilter : RangeFilter(name = "Розділів")

class TagFilter(blockedTags: Set<String>) : TriStateGroup("Жанри", options) {
    init {
        if (blockedTags.isNotEmpty()) {
            state.forEach { filter ->
                if (blockedTags.contains(filter.value)) {
                    filter.state = TriState.STATE_EXCLUDE
                }
            }
        }
    }
    companion object {
        var options = emptyList<Pair<String, String>>()
    }
}

class AgeFilter :
    MultiValueFilter(
        name = "Вік", // b.vik
        options = listOf(
            "Будь-який вік" to "0",
            "Від 12 років" to "12",
            "Від 16 років" to "16",
            "Від 18 років" to "18",
        ),
    )

class StatusFilter :
    MultiValueFilter(
        name = "Статус перекладу",
        options = listOf(
            "Триває" to "Триває",
            "Закінчено" to "Закінчений",
            "Невідомо" to "Невідомо",
            "Покинуто" to "Покинуто",
            "Заморожено" to "Заморожено",
        ),
    )
