package eu.kanade.tachiyomi.extension.ru.astramanga

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.second }.toTypedArray()) {
    val selected: String get() = options[state].first
}

class TypeFilter : SelectFilter("Тип", TYPES)
class StatusFilter : SelectFilter("Статус", STATUSES)

class SortFilter : Filter.Sort("Сортировка", SORT_LABELS, Filter.Sort.Selection(0, false)) {
    val selected: String get() = (if (state?.ascending == true) "" else "-") + SORT_FIELDS[state?.index ?: 0]
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter : Filter.Group<Genre>("Жанры", GENRES.map { Genre(it.first, it.second) })

private val TYPES = arrayOf(
    "" to "Все",
    "manga" to "Манга",
    "manhwa" to "Манхва",
    "manhua" to "Маньхуа",
)

private val STATUSES = arrayOf(
    "" to "Любой",
    "ongoing" to "Онгоинг",
    "completed" to "Завершён",
    "paused" to "Приостановлен",
)

private val SORT_LABELS = arrayOf(
    "По популярности",
    "По дате обновления",
    "По дате добавления",
    "По рейтингу",
    "По названию",
)
private val SORT_FIELDS = arrayOf(
    "popularity",
    "updated_at",
    "created_at",
    "rating",
    "name",
)

// name to genre id (confirmed via /genres endpoint)
private val GENRES = listOf(
    "Аниме" to "1",
    "Антиутопия" to "2",
    "Апокалиптический" to "3",
    "Арт" to "4",
    "Безумие" to "5",
    "Боевик" to "6",
    "Боевые искусства" to "7",
    "Вестерн" to "8",
    "Военное" to "9",
    "Выживание" to "10",
    "Гарем" to "11",
    "Героическое фэнтези" to "12",
    "Гуро" to "13",
    "Гэг-юмор" to "14",
    "Детектив" to "15",
    "Детское" to "16",
    "Дзёсей" to "17",
    "Драма" to "18",
    "Завоевание мира" to "19",
    "Исекай" to "20",
    "Искусство" to "21",
    "Исторический" to "22",
    "Киберпанк" to "23",
    "Кодомо" to "24",
    "Комедия" to "25",
    "Космос" to "26",
    "Криминал / Преступники" to "27",
    "Кулинария" to "28",
    "Культивация" to "29",
    "Литрес" to "30",
    "Махо-сёдзё" to "31",
    "Меха" to "32",
    "Мистика" to "33",
    "Мифология" to "34",
    "Мурим" to "35",
    "Научная фантастика" to "36",
    "Обратный Гарем" to "37",
    "Омегаверс" to "38",
    "Пародия" to "39",
    "Повседневность" to "40",
    "Постапокалипсис" to "41",
    "Приключения" to "42",
    "Психология" to "43",
    "Регрессия" to "44",
    "Рисование" to "45",
    "Романтика" to "46",
    "Самурайский боевик" to "47",
    "Сверхъестественное" to "48",
    "Сёдзе" to "49",
    "Сёнен" to "50",
    "Спорт" to "51",
    "Средневековье" to "52",
    "Стимпанк" to "53",
    "Сэйнэн" to "54",
    "Сянься" to "55",
    "Трагедия" to "56",
    "Триллер" to "57",
    "Ужасы" to "58",
    "Фантастика" to "59",
    "Философия" to "60",
    "Фэнтези" to "61",
    "Школьная жизнь" to "62",
    "Экшен" to "63",
    "Элементы юмора" to "64",
    "Юмор" to "65",
    "Образовательная литература" to "131",
)
