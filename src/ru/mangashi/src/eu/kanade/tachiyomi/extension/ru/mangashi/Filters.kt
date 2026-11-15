package eu.kanade.tachiyomi.extension.ru.mangashi

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second
}

class SortFilter(defaultOrder: String = "popular") : SelectFilter("Сортировка", data, defaultOrder) {
    companion object {
        private val data = listOf(
            Pair("По дате добавления", "added"),
            Pair("По обновлению глав", "updated"),
            Pair("По рейтингу", "rating"),
            Pair("По популярности", "popular"),
            Pair("По количеству глав", "chapters"),
            Pair("По году выпуска", "year"),
        )
    }
}

class StatusFilter(defaultValue: String = "") : SelectFilter("Статус", data, defaultValue) {
    companion object {
        private val data = listOf(
            Pair("Все", ""),
            Pair("Онгоинг", "ONGOING"),
            Pair("Завершён", "COMPLETED"),
            Pair("Хиатус", "HIATUS"),
        )
    }
}

class TypeFilter(defaultValue: String = "") : SelectFilter("Тип", data, defaultValue) {
    companion object {
        private val data = listOf(
            Pair("Все", ""),
            Pair("Манга", "MANGA"),
            Pair("Манхва", "MANHWA"),
            Pair("Маньхуа", "MANHUA"),
            Pair("Западный комикс", "WESTERN"),
        )
    }
}

class YearFilter(defaultValue: String = "") : SelectFilter("Год выпуска", data, defaultValue) {
    companion object {
        private val data = listOf(
            Pair("Все годы", ""),
            Pair("2026", "2026"),
            Pair("2025", "2025"),
            Pair("2024", "2024"),
            Pair("2023", "2023"),
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("2017", "2017"),
            Pair("2016", "2016"),
            Pair("2015", "2015"),
            Pair("2014", "2014"),
            Pair("2013", "2013"),
            Pair("2012", "2012"),
            Pair("2011", "2011"),
            Pair("2010", "2010"),
            Pair("2005", "2005"),
            Pair("2002", "2002"),
            Pair("1998", "1998"),
            Pair("1997", "1997"),
            Pair("1989", "1989"),
            Pair("1977", "1977"),
        )
    }
}

class AgeRatingFilter(defaultValue: String = "") : SelectFilter("Возрастной рейтинг", data, defaultValue) {
    companion object {
        private val data = listOf(
            Pair("Все", ""),
            Pair("Без 18+", "sfw"),
            Pair("Только 18+", "adult"),
        )
    }
}

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

class GenreFilter(data: List<Pair<String, String>>) : TriStateGroup("Жанры", data)
