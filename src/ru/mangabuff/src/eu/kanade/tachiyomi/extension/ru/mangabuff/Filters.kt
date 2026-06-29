package eu.kanade.tachiyomi.extension.ru.mangabuff

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }.takeUnless { it.isEmpty() }
}

class SortFilter(defaultOrder: String? = null) : SelectFilter("Сортировать по", sort, defaultOrder) {
    companion object {
        private val sort = listOf(
            Pair("Популярные", "views"),
            Pair("Обновленные", "updated_at"),
            Pair("По рейтингу", "rating"),
            Pair("По новинкам", "created_at"),
        )

        val POPULAR = FilterList(SortFilter("views"))
        val LATEST = FilterList(SortFilter("updated_at"))
    }
}

class GenreFilter : TriStateGroup("Жанр", genres) {
    companion object {
        private val genres = listOf(
            Pair("Арт", "1"),
            Pair("Боевик", "2"),
            Pair("Боевые искусства", "4"),
            Pair("Вампиры", "5"),
            Pair("Гарем", "6"),
            Pair("Гендерная интрига", "7"),
            Pair("Героическое фэнтези", "8"),
            Pair("Детектив", "9"),
            Pair("Дзёсэй", "10"),
            Pair("Додзинси", "11"),
            Pair("Драма", "12"),
            Pair("Ёнкома", "39"),
            Pair("Игра", "18"),
            Pair("История", "13"),
            Pair("Киберпанк", "21"),
            Pair("Кодомо", "40"),
            Pair("Комедия", "14"),
            Pair("Махо-сёдзе", "20"),
            Pair("Меха", "15"),
            Pair("Мистика", "16"),
            Pair("Научная фантастика", "17"),
            Pair("Повседневность", "19"),
            Pair("Постапокалиптика", "22"),
            Pair("Приключения", "24"),
            Pair("Психология", "25"),
            Pair("Романтика", "26"),
            Pair("Самурайский боевик", "28"),
            Pair("Сверхъестественное", "30"),
            Pair("Сёдзё", "31"),
            Pair("Сёнэн", "29"),
            Pair("Спорт", "32"),
            Pair("Сэйнэн", "33"),
            Pair("Трагедия", "23"),
            Pair("Триллер", "34"),
            Pair("Ужасы", "35"),
            Pair("Фантастика", "27"),
            Pair("Фэнтези", "36"),
            Pair("Школа", "3"),
            Pair("Эротика", "37"),
            Pair("Этти", "38"),
        )
    }
}

class TypeFilter : TriStateGroup("Тип", types) {
    companion object {
        private val types = listOf(
            Pair("Манга", "1"),
            Pair("OEL-манга", "2"),
            Pair("Манхва", "3"),
            Pair("Маньхуа", "4"),
            Pair("Сингл", "5"),
            Pair("Руманга", "6"),
            Pair("Комикс западный", "7"),
        )
    }
}

class TagFilter : TriStateGroup("теги", tags) {
    companion object {
        private val tags = listOf(
            Pair("Азартные игры", "7759"),
            Pair("Алхимия", "7750"),
            Pair("Амнезия / Потеря памяти", "7776"),
            Pair("амнезия/потеря памяти", "7780"),
            Pair("Ангелы", "7744"),
            Pair("Антигерой", "7691"),
            Pair("Антиутопия", "7755"),
            Pair("Апокалипсис", "7774"),
            Pair("Армия", "7767"),
            Pair("Артефакты", "7727"),
            Pair("Боги", "7679"),
            Pair("Бои на мечах", "7700"),
            Pair("Борьба за власть", "7734"),
            Pair("Брат и сестра", "7725"),
            Pair("Будущее", "7756"),
            Pair("в первый раз", "7695"),
            Pair("Ведьма", "7772"),
            Pair("Вестерн", "7771"),
            Pair("Видеоигры", "7704"),
            Pair("Виртуальная реальность", "7760"),
            Pair("Владыка демонов", "7743"),
            Pair("Военные", "7676"),
            Pair("Война", "7770"),
            Pair("Волшебники / маги", "7680"),
            Pair("Волшебные существа", "7721"),
            Pair("Воспоминания из другого мира", "7713"),
            Pair("Выживание", "7739"),
            Pair("ГГ женщина", "7702"),
            Pair("ГГ имба", "7709"),
            Pair("ГГ мужчина", "7681"),
            Pair("Геймеры", "7758"),
            Pair("Гильдии", "7762"),
            Pair("Глупый ГГ", "7718"),
            Pair("Гоблины", "7766"),
            Pair("Горничные", "7753"),
            Pair("Гяру", "7773"),
            Pair("Демоны", "7682"),
            Pair("Драконы", "7751"),
            Pair("Дружба", "7703"),
            Pair("Жестокий мир", "7728"),
            Pair("Жестокость", "7784"),
            Pair("Животные компаньоны", "7752"),
            Pair("Завоевание мира", "7748"),
            Pair("Зверолюди", "7707"),
            Pair("Злые духи", "7683"),
            Pair("Зомби", "7726"),
            Pair("Игровые элементы", "7723"),
            Pair("Империи", "7711"),
            Pair("Квесты", "7735"),
            Pair("Космос", "7749"),
            Pair("Кулинария", "7740"),
            Pair("Культивация", "7731"),
            Pair("Легендарное оружие", "7714"),
            Pair("Лоли", "7791"),
            Pair("Магическая академия", "7684"),
            Pair("Магия", "7677"),
            Pair("Мафия", "7690"),
            Pair("Медицина", "7761"),
            Pair("Месть", "7741"),
            Pair("Монстр Девушки", "7719"),
            Pair("Монстродевушки", "7720"),
            Pair("Монстры", "7685"),
            Pair("Музыка", "7675"),
            Pair("Навыки / способности", "7715"),
            Pair("Наёмники", "7764"),
            Pair("Насилие / жестокость", "7692"),
            Pair("Нежить", "7686"),
            Pair("Ниндзя", "7732"),
            Pair("Обмен телами", "7757"),
            Pair("Обратный Гарем", "7705"),
            Pair("Огнестрельное оружие", "7777"),
            Pair("Офисные Работники", "7754"),
            Pair("Пародия", "7745"),
            Pair("Пираты", "7724"),
            Pair("Подземелья", "7722"),
            Pair("Политика", "7736"),
            Pair("Полиция", "7693"),
            Pair("Преступники / Криминал", "7733"),
            Pair("Призраки / Духи", "7687"),
            Pair("Путешествие во времени", "7710"),
            Pair("Путешествия во времени", "7730"),
            Pair("Рабы", "7765"),
            Pair("Разумные расы", "7688"),
            Pair("Ранги силы", "7746"),
            Pair("Реинкарнация", "7706"),
            Pair("Роботы", "7769"),
            Pair("Рыцари", "7701"),
            Pair("Самураи", "7698"),
            Pair("Система", "7737"),
            Pair("Скрытие личности", "7708"),
            Pair("Спасение мира", "7747"),
            Pair("Спортивное тело", "7742"),
            Pair("Средневековье", "7699"),
            Pair("Стимпанк", "7781"),
            Pair("Супергерои", "7775"),
            Pair("Традиционные игры", "7768"),
            Pair("Умный ГГ", "7716"),
            Pair("Учитель / ученик", "7717"),
            Pair("Философия", "7729"),
            Pair("Хикикомори", "7763"),
            Pair("Холодное оружие", "7738"),
            Pair("Шантаж", "7778"),
            Pair("Эльфы", "7678"),
            Pair("юные", "7696"),
            Pair("Якудза", "7689"),
            Pair("Яндере", "7779"),
            Pair("Япония", "7674"),
        )
    }
}

class StatusFilter : CheckBoxGroup("Статус", statuses) {
    companion object {
        private val statuses = listOf(
            Pair("Завершен", "1"),
            Pair("Продолжается", "2"),
            Pair("Заморожен", "3"),
            Pair("Заброшен", "4"),
        )
    }
}

class AgeFilter : CheckBoxGroup("Возрастной рейтинг", ages) {
    companion object {
        private val ages = listOf(
            Pair("18+", "18+"),
            Pair("16+", "16+"),
        )
    }
}

class RatingFilter : CheckBoxGroup("Рейтинг", ratings) {
    companion object {
        private val ratings = listOf(
            Pair("Рейтинг 50%+", "5"),
            Pair("Рейтинг 60%+", "6"),
            Pair("Рейтинг 70%+", "7"),
            Pair("Рейтинг 80%+", "8"),
            Pair("Рейтинг 90%+", "9"),
        )
    }
}

class YearFilter : CheckBoxGroup("Год выпуска", years) {
    companion object {
        private val years = listOf(
            Pair("2024", "2024"),
            Pair("2023", "2023"),
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("2017", "2017"),
            Pair("2016", "2016"),
        )
    }
}

class ChapterCountFilter : CheckBoxGroup("Колличество глав", chapters) {
    companion object {
        private val chapters = listOf(
            Pair("<50", "0"),
            Pair("50-100", "50"),
            Pair("100-200", "100"),
            Pair(">200", "200"),
        )
    }
}
