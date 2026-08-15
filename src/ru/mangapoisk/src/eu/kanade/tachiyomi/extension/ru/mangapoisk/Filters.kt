package eu.kanade.tachiyomi.extension.ru.mangapoisk

import eu.kanade.tachiyomi.source.model.Filter

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
    val order get() = when (selected) {
        "name", "popular" -> if (state!!.ascending) "-" else ""
        else -> if (state!!.ascending) "" else "-"
    }
}

internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    values: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = values.map { MultiValueOption(it.first, it.second) },
) {
    val checked: List<String>? get() = state.filter { it.state && it.value.isNotEmpty() }.map { it.value }.takeIf { it.isNotEmpty() }
}

class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }.takeUnless { it.isEmpty() }
}

internal class OrderBy :
    OrderByFilter(
        "Сортировка",
        listOf(
            "Год" to "year",
            "Популярность" to "popular",
            "Алфавит" to "name",
            "Дата добавления" to "published_at",
            "Дата обновления" to "last_chapter_at",
            "Количество глав" to "chapters_count",
        ),
        Selection(1, false),
    )

internal class StatusList : MultiValueFilter("Статус", statuses) {
    companion object {
        val statuses = listOf(
            "Выпускается" to "0",
            "Завершена" to "1",
        )
    }
}

internal class GenresFilter : TriStateGroup("Жанры", genres) {
    companion object {
        val genres = listOf(
            "Арт" to "7332",
            "Боевик" to "3",
            "Боевые искусства" to "31",
            "Вампиры" to "10",
            "Гарем" to "29",
            "Гендерная интрига" to "172",
            "Героическое фэнтези" to "30",
            "Детектив" to "121",
            "Дзёсэй" to "230",
            "Додзинси" to "1785",
            "Драма" to "6",
            "Игра" to "105",
            "Исэкай" to "8120",
            "Исторя" to "123",
            "Киберпанк" to "355",
            "Комедия" to "4",
            "Кодомо" to "1789",
            "Махо-сёдзё" to "1472",
            "Меха" to "356",
            "Музыка" to "34948",
            "Научная фантастика" to "171",
            "Образование" to "987",
            "Омегаверс" to "7514",
            "Пародия" to "34402",
            "Повседневность" to "18",
            "Повседневность" to "10163",
            "Постапокалиптика" to "310",
            "Постапокалиптика" to "44805",
            "Приключения" to "1",
            "Психология" to "38",
            "Романтика" to "2",
            "Самурайский боевик" to "916",
            "Сверхъестественное" to "5",
            "Сёдзё" to "57",
            "Сёдзё-ай" to "147",
            "Сёнэн" to "8",
            "Сэйнэн" to "12",
            "Спорт" to "160",
            "Триллер" to "120",
            "Трагедия" to "122",
            "Уся" to "10128",
            "Ужасы" to "260",
            "Фэнтези" to "7",
            "Школа" to "11",
            "Щанься" to "9321",
        )
    }
}
