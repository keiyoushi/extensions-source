package eu.kanade.tachiyomi.extension.ru.seimanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class SeiManga : GroupLe() {

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        AgeList(getAgeList()),
        MoreList(getMoreList()),
        AdditionalFilterList(getAdditionalFilterList()),
    )
    private fun getAdditionalFilterList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Заброшен перевод", "s_abandoned_popular"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Онгоинг", "s_ongoing"),
    )
    private fun getMoreList() = listOf(
        Genre("В цвете", "el_4614"),
        Genre("Веб", "el_1355"),
        Genre("На экранах", "el_6827"),
        Genre("Сборник", "el_1348"),
    )

    private fun getAgeList() = listOf(
        Genre("NC-17", "el_3969"),
        Genre("R", "el_3968"),
    )

    private fun getCategoryList() = listOf(
        Genre("OEL-манга", "el_6637"),
        Genre("Арт", "el_2220"),
        Genre("Додзинси", "el_1332"),
        Genre("Ёнкома", "el_2741"),
        Genre("Комикс", "el_1903"),
        Genre("Манга", "el_6421"),
        Genre("Манхва", "el_1873"),
        Genre("Маньхуа", "el_1875"),
    )

    private fun getGenreList() = listOf(
        Genre("боевик", "el_1346"),
        Genre("боевые искусства", "el_1334"),
        Genre("гарем", "el_1333"),
        Genre("гендерная интрига", "el_1347"),
        Genre("детектив", "el_1343"),
        Genre("дзёсэй", "el_1349"),
        Genre("драма", "el_1310"),
        Genre("история", "el_1311"),
        Genre("исэкай", "el_6420"),
        Genre("киберпанк", "el_1351"),
        Genre("комедия", "el_1328"),
        Genre("музыка", "el_6585"),
        Genre("научная фантастика", "el_1325"),
        Genre("пародия", "el_6595"),
        Genre("повседневность", "el_1327"),
        Genre("постапокалиптика", "el_1342"),
        Genre("приключения", "el_1322"),
        Genre("психология", "el_1335"),
        Genre("романтика", "el_1313"),
        Genre("сверхъестественное", "el_1350"),
        Genre("сёдзё", "el_1314"),
        Genre("сёнэн", "el_1326"),
        Genre("спорт", "el_1321"),
        Genre("сэйнэн", "el_1329"),
        Genre("трагедия", "el_1344"),
        Genre("триллер", "el_1341"),
        Genre("ужасы", "el_1317"),
        Genre("фэнтези", "el_1323"),
        Genre("школа", "el_1319"),
        Genre("эротика", "el_1340"),
        Genre("этти", "el_1354"),
    )
}
