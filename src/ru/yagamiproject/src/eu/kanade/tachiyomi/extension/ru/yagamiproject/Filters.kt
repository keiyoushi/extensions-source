package eu.kanade.tachiyomi.extension.ru.yagamiproject

import eu.kanade.tachiyomi.source.model.Filter

class FormatList(formas: Array<String>) : Filter.Select<String>("Тип", formas)

class FormUnit(val name: String, val query: String)

class CategoryList(categories: Array<String>) : Filter.Select<String>("Категории", categories)

class CatUnit(val name: String)

internal fun getFormatList() = listOf(
    FormUnit("Все", "not"),
    FormUnit("Манга", "manga"),
    FormUnit("Манхва", "manhva"),
    FormUnit("Веб Манхва", "webtoon"),
    FormUnit("Маньхуа", "manhua"),
    FormUnit("Артбуки", "artbooks"),
)

internal fun getCategoryList() = listOf(
    CatUnit("Без категории"),
    CatUnit("боевые искусства"),
    CatUnit("гарем"),
    CatUnit("гендерная интрига"),
    CatUnit("дзёсэй"),
    CatUnit("для взрослых"),
    CatUnit("драма"),
    CatUnit("зрелое"),
    CatUnit("исторический"),
    CatUnit("комедия"),
    CatUnit("меха"),
    CatUnit("мистика"),
    CatUnit("научная фантастика"),
    CatUnit("непристойности"),
    CatUnit("постапокалиптика"),
    CatUnit("повседневность"),
    CatUnit("приключения"),
    CatUnit("психология"),
    CatUnit("романтика"),
    CatUnit("сверхъестественное"),
    CatUnit("сёдзё"),
    CatUnit("сёнэн"),
    CatUnit("спорт"),
    CatUnit("сэйнэн"),
    CatUnit("трагедия"),
    CatUnit("ужасы"),
    CatUnit("фэнтези"),
    CatUnit("школьная жизнь"),
    CatUnit("экшн"),
    CatUnit("эротика"),
    CatUnit("этти"),
)
