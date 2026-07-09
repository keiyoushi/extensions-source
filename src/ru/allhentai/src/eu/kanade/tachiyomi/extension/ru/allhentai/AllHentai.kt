package eu.kanade.tachiyomi.extension.ru.allhentai

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class AllHentai : GroupLe() {

    override val isNeedAuth = true

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        AdditionalFilterList(getAdditionalFilterList()),
    )

    private fun getGenreList() = listOf(
        Genre("ahegao", "el_855"),
        Genre("анал", "el_828"),
        Genre("бдсм", "el_78"),
        Genre("без цензуры", "el_888"),
        Genre("большая грудь", "el_837"),
        Genre("большая попка", "el_3156"),
        Genre("большой член", "el_884"),
        Genre("бондаж", "el_5754"),
        Genre("в первый раз", "el_811"),
        Genre("в цвете", "el_290"),
        Genre("гарем", "el_87"),
        Genre("гендарная интрига", "el_89"),
        Genre("групповой секс", "el_88"),
        Genre("драма", "el_95"),
        Genre("зрелые женщины", "el_5679"),
        Genre("измена", "el_291"),
        Genre("изнасилование", "el_124"),
        Genre("инцест", "el_85"),
        Genre("исторический", "el_93"),
        Genre("комедия", "el_73"),
        Genre("маленькая грудь", "el_870"),
        Genre("научная фантастика", "el_76"),
        Genre("нетораре", "el_303"),
        Genre("оральный секс", "el_853"),
        Genre("романтика", "el_74"),
        Genre("тентакли", "el_69"),
        Genre("трагедия", "el_1321"),
        Genre("ужасы", "el_75"),
        Genre("футанари", "el_77"),
        Genre("фэнтези", "el_70"),
        Genre("чикан", "el_1059"),
        Genre("этти", "el_798"),
    )

    private fun getCategoryList() = listOf(
        Genre("3D", "el_626"),
        Genre("Анимация", "el_5777"),
        Genre("Без текста", "el_3157"),
        Genre("Манхва", "el_1104"),
        Genre("Маньхуа", "el_5902"),
        Genre("Порно комикс", "el_1003"),
        Genre("Руманга", "el_5896"),
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
        Genre("Лицензия", "s_sale"),
        Genre("Белые жанры", "s_not_pessimized"),
        Genre("Онгоинг", "s_ongoing"),
    )
}
