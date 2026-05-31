package eu.kanade.tachiyomi.extension.uk.mangainua

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.map { it.second }.indexOf(defaultValue).coerceAtLeast(0),
) {
    val selected get() = options[state].second.takeUnless { it.isBlank() }
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    val options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }.takeUnless { it.isEmpty() }
}

class SortFilter(defaultOrder: String? = null) : SelectFilter("Сортувати", sort, defaultOrder) {
    companion object {
        private val sort = listOf(
            "За переглядами" to "news_read;desc",
            "За рейтингом" to "d.mrating;desc",
            "Спочатку нові" to "date;desc",
            "За коментарями" to "comm_num;desc",
            "За назвою" to "nameukr;asc",
            "Від новинок до старих" to "d.yer;desc",
            "Від старих до новинок" to "d.yer;asc",
        )
    }
}

class CategoriesFilter :
    SelectFilter(
        name = "Категорії", // b.type
        options = listOf(
            "Всі категорї" to "",
            "МАНҐА" to "manga",
            "МАНХВА" to "manhwa",
            "МАНЬХВА" to "manhua",
            "ВАНШОТ" to "one-shot",
            "ДОДЖІНШІ" to "dojinshi",
            "МАЛЬОПИС" to "malopys",
        ),
    )

class YearsFilter :
    SelectFilter(
        name = "Роки", // c.yer
        options = listOf(
            "Всі роки" to "",
            "2026" to "2026,2026",
            "2025" to "2025,2025",
            "2024" to "2024,2024",
            "2023" to "2023,2023",
            "2022" to "2022,2022",
            "2021" to "2021,2021",
            "2020" to "2020,2020",
            "2019" to "2019,2019",
            "2016 - 2026" to "2016,2026",
            "2010 - 2016" to "2010,2016",
            "2000 - 2010" to "2000,2010",
            "1990 - 2000" to "1990,2000",
            "1980 - 1990" to "1980,1990",
            "До 80-х" to "1921,1980",
        ),
    )

class SizeFilter :
    SelectFilter(
        name = "Кількість розділів", // c.lastchappr
        options = listOf(
            "Будь-яка кількість" to "",
            "Від 1 до 20 розділів" to "1,20",
            "Від 20 до 50 розділів" to "20,50",
            "Від 50 до 100 розділів" to "50,100",
            "Більше 100 розділів" to "100,1000",
        ),
    )

class TagFilter : TriStateGroup("Жанри", options) {
    companion object {
        val options = listOf(
            "Божевілля" to "53",
            "Бойовик" to "12",
            "Бойові мистецтва" to "13",
            "Буденність" to "26",
            "Вампіри" to "14",
            "Гарем" to "15",
            "Для дітей" to "3",
            "Детектив" to "16",
            "Демони" to "52",
            "Джьосей" to "10",
            "Доджінші" to "18",
            "Драма" to "19",
            "Еччі" to "39",
            "Жахи" to "35",
            "Зміна статі" to "44",
            "Ігри" to "51",
            "Ісекай" to "63",
            "Історія" to "20",
            "Іяшікей" to "62",
            "Йонкома" to "42",
            "Космос" to "50",
            "Комедія" to "22",
            "Махо-шьоджьо" to "11",
            "Машини" to "49",
            "Меха" to "23",
            "Містика" to "24",
            "Музика" to "48",
            "Надприродне" to "31",
            "Наукова фантастика" to "25",
            "Пародія" to "47",
            "Пригоди" to "28",
            "Психологія" to "29",
            "Поліція" to "46",
            "Постапокаліптика" to "27",
            "Романтика" to "30",
            "Самураї" to "45",
            "Сентай" to "6",
            "Сейнен" to "9",
            "Спорт" to "32",
            "Суперсила" to "43",
            "Трагедія" to "33",
            "Трилер" to "34",
            "Фантастика" to "36",
            "Фентезі" to "37",
            "Шьоджьо" to "4",
            "Шьоджьо-ай" to "60",
            "Шьонен" to "5",
            "Шьонен-ай" to "61",
            "Школа" to "38",
            "Юрі" to "40",
            "Яой" to "41",
        )
    }
}

class AgeFilter :
    SelectFilter(
        name = "Вік", // b.vik
        options = listOf(
            "Будь-який вік" to "",
            "Від 12 років" to "12",
            "Від 16 років" to "16",
            "Від 18 років" to "18",
        ),
    )

class StatusFilter :
    SelectFilter(
        name = "Статус перекладу", // b.tra
        options = listOf(
            "Будь-який статус" to "",
            "Триває" to "Триває",
            "Закінчено" to "Закінчений",
            "Невідомо" to "Невідомо",
            "Покинуто" to "Покинуто",
            "Заморожено" to "Заморожено",
        ),
    )
