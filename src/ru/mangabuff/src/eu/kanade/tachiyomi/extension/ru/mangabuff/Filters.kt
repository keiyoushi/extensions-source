package eu.kanade.tachiyomi.extension.ru.mangabuff

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
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

class SortFilter(defaultOrder: String? = null) : SelectFilter("Сортировать по", sort, defaultOrder) {
    companion object {
        private val sort = listOf(
            Pair("Популярные", "real_views"),
            Pair("Обновленные", "updated_at"),
            Pair("По рейтингу", "rating"),
            Pair("По новинкам", "created_at"),
        )
    }
}

class GenreFilter(genres: List<Pair<String, String>>) : TriStateGroup("Жанр", genres)

class TypeFilter : TriStateGroup("Тип", types) {
    companion object {
        private val types = listOf(
            Pair("Манга", "1"),
            Pair("OEL-манга", "2"),
            Pair("Манхва", "3"),
            Pair("Маньхуа", "4"),
            Pair("Сингл", "5"),
            Pair("Руманга", "6"),
            Pair("Комикс западный", "7"),
        )
    }
}

class TagFilter(tags: List<Pair<String, String>>) : TriStateGroup("Теги", tags)

class StatusFilter : CheckBoxGroup("Статус", statuses) {
    companion object {
        private val statuses = listOf(
            Pair("Завершен", "1"),
            Pair("Продолжается", "2"),
            Pair("Заморожен", "3"),
            Pair("Заброшен", "4"),
        )
    }
}

class AgeFilter : CheckBoxGroup("Возрастной рейтинг", ages) {
    companion object {
        private val ages = listOf(
            Pair("18+", "18+"),
            Pair("16+", "16+"),
        )
    }
}

class RatingFilter : CheckBoxGroup("Рейтинг", ratings) {
    companion object {
        private val ratings = listOf(
            Pair("Рейтинг 50%+", "5"),
            Pair("Рейтинг 60%+", "6"),
            Pair("Рейтинг 70%+", "7"),
            Pair("Рейтинг 80%+", "8"),
            Pair("Рейтинг 90%+", "9"),
        )
    }
}

class YearFilter : CheckBoxGroup("Год выпуска", years) {
    companion object {
        private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        private val years = (currentYear downTo 1986).map { year ->
            Pair(year.toString(), year.toString())
        }
    }
}

class ChapterCountFilter : CheckBoxGroup("Колличество глав", chapters) {
    companion object {
        private val chapters = listOf(
            Pair("<50", "0"),
            Pair("50-100", "50"),
            Pair("100-200", "100"),
            Pair(">200", "200"),
        )
    }
}
