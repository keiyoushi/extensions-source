package eu.kanade.tachiyomi.extension.ru.yaoichan

import eu.kanade.tachiyomi.source.model.Filter

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)

class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.TriState(name)

class Status : Filter.Select<String>("Статус", arrayOf("Все", "Перевод завершен", "Выпуск завершен", "Онгоинг", "Новые главы"))

class OrderBy :
    Filter.Sort(
        "Сортировка",
        arrayOf("Дата", "Популярность", "Имя", "Главы"),
        Selection(1, false),
    )

internal fun getGenreList() = listOf(
    Genre("18 плюс"),
    Genre("bdsm"),
    Genre("арт"),
    Genre("бара"),
    Genre("боевик"),
    Genre("боевые искусства"),
    Genre("вампиры"),
    Genre("веб"),
    Genre("гарем"),
    Genre("гендерная интрига"),
    Genre("героическое фэнтези"),
    Genre("групповой секс"),
    Genre("детектив"),
    Genre("дзёсэй"),
    Genre("додзинси"),
    Genre("драма"),
    Genre("игра"),
    Genre("инцест"),
    Genre("искусство"),
    Genre("история"),
    Genre("киберпанк"),
    Genre("комедия"),
    Genre("литРПГ"),
    Genre("махо-сёдзё"),
    Genre("меха"),
    Genre("мистика"),
    Genre("мужская беременность"),
    Genre("музыка"),
    Genre("научная фантастика"),
    Genre("омегаверс"),
    Genre("переодевание"),
    Genre("повседневность"),
    Genre("постапокалиптика"),
    Genre("приключения"),
    Genre("психология"),
    Genre("романтика"),
    Genre("самурайский боевик"),
    Genre("сборник"),
    Genre("сверхъестественное"),
    Genre("сетакон"),
    Genre("сказка"),
    Genre("спорт"),
    Genre("супергерои"),
    Genre("сэйнэн"),
    Genre("сёдзё"),
    Genre("сёдзё-ай"),
    Genre("сёнэн"),
    Genre("сёнэн-ай"),
    Genre("тентакли"),
    Genre("трагедия"),
    Genre("триллер"),
    Genre("ужасы"),
    Genre("фантастика"),
    Genre("фурри"),
    Genre("фэнтези"),
    Genre("школа"),
    Genre("эротика"),
    Genre("юмор"),
    Genre("юри"),
    Genre("яой"),
    Genre("ёнкома"),
)
