package eu.kanade.tachiyomi.extension.ru.nudemoon

import eu.kanade.tachiyomi.source.model.Filter

internal class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.CheckBox(name.replaceFirstChar { it.uppercaseChar() })
internal class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
internal class OrderBy :
    Filter.Sort(
        "Сортировка",
        order.map { it.first }.toTypedArray(),
        Selection(1, false),
    ) {
    val selected: String get() = order[state?.index ?: 1].second
    companion object {
        val order = listOf(
            "Дата" to "date",
            "Просмотры" to "views",
            "Лайки" to "like",
            "Отзывы" to "com",
            "Количество страниц" to "pages",
        )
    }
}
