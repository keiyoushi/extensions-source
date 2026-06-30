package eu.kanade.tachiyomi.extension.ru.selfmanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class SelfManga : GroupLe() {

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        MoreList(getMoreList()),
        AdditionalFilterList(getAdditionalFilterList()),
    )

    private fun getAdditionalFilterList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Лицензия", "s_sale"),
        Genre("Белые жанры", "s_not_pessimized"),
        Genre("Онгоинг", "s_ongoing"),
    )

    private fun getMoreList() = listOf(
        Genre("В цвете", "el_6015"),
        Genre("Начинающий", "el_6013"),
        Genre("Нейросетевой арт", "el_6317"),
        Genre("Продвинутый", "el_6014"),
    )

    private fun getCategoryList() = listOf(
        Genre("Арт", "el_5894"),
        Genre("Вебтун", "el_2160"),
        Genre("Додзинси", "el_2141"),
        Genre("Журнал", "el_4983"),
        Genre("Комикс", "el_6011"),
        Genre("Манга", "el_6010"),
        Genre("Сборник", "el_2157"),
    )

    private fun getGenreList() = listOf(
        Genre("альтернативная история", "el_6026"),
        Genre("боевик", "el_2155"),
        Genre("боевые искусства", "el_2143"),
        Genre("вампиры", "el_2148"),
        Genre("гарем", "el_2142"),
        Genre("гендерная интрига", "el_2156"),
        Genre("героическое фэнтези", "el_2146"),
        Genre("детектив", "el_2152"),
        Genre("дзёсэй", "el_2158"),
        Genre("драма", "el_2118"),
        Genre("ёнкома", "el_2161"),
        Genre("история", "el_2119"),
        Genre("комедия", "el_2136"),
        Genre("махо-сёдзё", "el_2147"),
        Genre("мистика", "el_2132"),
        Genre("научная фантастика", "el_2133"),
        Genre("повседневность", "el_2135"),
        Genre("постапокалиптика", "el_2151"),
        Genre("приключения", "el_2130"),
        Genre("психология", "el_2144"),
        Genre("романтика", "el_2121"),
        Genre("сверхъестественное", "el_2159"),
        Genre("сёдзё", "el_2122"),
        Genre("сёнэн", "el_2134"),
        Genre("спорт", "el_2129"),
        Genre("сэйнэн", "el_5838"),
        Genre("трагедия", "el_2153"),
        Genre("триллер", "el_2150"),
        Genre("ужасы", "el_2125"),
        Genre("фантастика", "el_2140"),
        Genre("фэнтези", "el_2131"),
        Genre("школа", "el_2127"),
        Genre("эротика", "el_6012"),
        Genre("этти", "el_4982"),
    )
}
