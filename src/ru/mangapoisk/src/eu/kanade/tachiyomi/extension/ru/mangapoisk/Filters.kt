package eu.kanade.tachiyomi.extension.ru.mangapoisk

import eu.kanade.tachiyomi.source.model.Filter

class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
class GenreList(genres: List<CheckFilter>) : Filter.Group<CheckFilter>("Жанры", genres)

class OrderBy :
    Filter.Sort(
        "Сортировка",
        arrayOf("Год", "Популярности", "Алфавиту", "Дате добавления", "Дате обновления"),
        Selection(1, false),
    )

fun getStatusList() = listOf(
    CheckFilter("Выпускается", "0"),
    CheckFilter("Завершена", "1"),
)

fun getGenreList() = listOf(
    CheckFilter("приключения", "1"),
    CheckFilter("романтика", "2"),
    CheckFilter("боевик", "3"),
    CheckFilter("комедия", "4"),
    CheckFilter("сверхъестественное", "5"),
    CheckFilter("драма", "6"),
    CheckFilter("фэнтези", "7"),
    CheckFilter("сёнэн", "8"),
    CheckFilter("этти", "7"),
    CheckFilter("вампиры", "10"),
    CheckFilter("школа", "11"),
    CheckFilter("сэйнэн", "12"),
    CheckFilter("повседневность", "18"),
    CheckFilter("сёнэн-ай", "19"),
    CheckFilter("гарем", "29"),
    CheckFilter("героическое фэнтези", "30"),
    CheckFilter("боевые искусства", "31"),
    CheckFilter("психология", "38"),
    CheckFilter("сёдзё", "57"),
    CheckFilter("игра", "105"),
    CheckFilter("триллер", "120"),
    CheckFilter("детектив", "121"),
    CheckFilter("трагедия", "122"),
    CheckFilter("история", "123"),
    CheckFilter("сёдзё-ай", "147"),
    CheckFilter("спорт", "160"),
    CheckFilter("научная фантастика", "171"),
    CheckFilter("гендерная интрига", "172"),
    CheckFilter("дзёсэй", "230"),
    CheckFilter("ужасы", "260"),
    CheckFilter("постапокалиптика", "310"),
    CheckFilter("киберпанк", "355"),
    CheckFilter("меха", "356"),
    CheckFilter("эротика", "380"),
    CheckFilter("яой", "612"),
    CheckFilter("самурайский боевик", "916"),
    CheckFilter("махо-сёдзё", "1472"),
    CheckFilter("додзинси", "1785"),
    CheckFilter("кодомо", "1789"),
    CheckFilter("юри", "3197"),
    CheckFilter("арт", "7332"),
    CheckFilter("омегаверс", "7514"),
    CheckFilter("бара", "8119"),
)
