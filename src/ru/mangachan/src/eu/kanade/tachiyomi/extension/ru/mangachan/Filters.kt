package eu.kanade.tachiyomi.extension.ru.mangachan

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
    Genre("18_плюс"),
    Genre("bdsm"),
    Genre("арт"),
    Genre("боевик"),
    Genre("боевые_искусства"),
    Genre("вампиры"),
    Genre("веб"),
    Genre("гарем"),
    Genre("гендерная_интрига"),
    Genre("героическое_фэнтези"),
    Genre("детектив"),
    Genre("дзёсэй"),
    Genre("додзинси"),
    Genre("драма"),
    Genre("игра"),
    Genre("инцест"),
    Genre("искусство"),
    Genre("история"),
    Genre("киберпанк"),
    Genre("кодомо"),
    Genre("комедия"),
    Genre("литРПГ"),
    Genre("махо-сёдзё"),
    Genre("меха"),
    Genre("мистика"),
    Genre("музыка"),
    Genre("научная_фантастика"),
    Genre("повседневность"),
    Genre("постапокалиптика"),
    Genre("приключения"),
    Genre("психология"),
    Genre("романтика"),
    Genre("самурайский_боевик"),
    Genre("сборник"),
    Genre("сверхъестественное"),
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
    Genre("юри"),
    Genre("яой"),
    Genre("ёнкома"),
)
