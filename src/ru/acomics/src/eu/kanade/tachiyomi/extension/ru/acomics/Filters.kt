package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class Genre(name: String, val id: Int) : Filter.CheckBox(name)
class Rating(name: String, val id: Int) : Filter.CheckBox(name, state = true)

class Categories :
    Filter.Group<Genre>(
        "Категории",
        listOf(
            Genre("Животные", 1),
            Genre("Драма", 2),
            Genre("Фентези", 3),
            Genre("Игры", 4),
            Genre("Юмор", 5),
            Genre("Журнал", 6),
            Genre("Паранормальное", 7),
            Genre("Конец света", 8),
            Genre("Романтика", 9),
            Genre("Фантастика", 10),
            Genre("Бытовое", 11),
            Genre("Стимпанк", 12),
            Genre("Супергерои", 13),
            Genre("Детектив", 14),
            Genre("Историческое", 15),
        ),
    )

class Ratings :
    Filter.Group<Rating>(
        "Возрастная категория",
        listOf(
            Rating("NR", 1),
            Rating("G", 2),
            Rating("PG", 3),
            Rating("PG-13", 4),
            Rating("R", 5),
            Rating("NC-17", 6),
        ),
    )

class ComicType : Filter.Select<String>("Тип комикса", arrayOf("Все", "Оригинальный", "Перевод")) // "0", "orig", "trans"
class Publication : Filter.Select<String>("Публикация", arrayOf("Все", "Завершенный", "Продолжающийся")) // "0", "no", "yes"
class Subscription : Filter.Select<String>("Подписка", arrayOf("Все", "В моей ленте", "Кроме моей ленты")) // "0", "yes", "no"
class MinPages : Filter.Text("Минимум страниц", state = "2")
class Sort(state: Int = 1) : Filter.Select<String>("Сортировка", arrayOf("по дате обновления", "по количеству подписчиков", "по количеству выпусков", "по алфавиту"), state = state) { // "last_update", "subscr_count", "issue_count", "serial_name"
    companion object {
        val LATEST = FilterList(Ratings(), Sort(0))
        val POPULAR = FilterList(Ratings(), Sort(1))
    }
}
