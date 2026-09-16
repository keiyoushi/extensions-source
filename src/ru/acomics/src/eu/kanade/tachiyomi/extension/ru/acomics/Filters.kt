package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.source.model.Filter

// ============================== Order ===============================
abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeIf { it.isNotBlank() }
}

// ============================== Multi Value ===============================
internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = options.map { MultiValueOption(it.first, it.second) },
) {
    val selected get() = state.filter { it.state }.map { it.value }.takeIf { it.isNotEmpty() }
}

// ============================== Filter data ===============================
internal class OrderBy(data: List<Pair<String, String>>) : SelectFilter("Сортировка", data, "subscr_count")
internal class ComicType(data: List<Pair<String, String>>) : SelectFilter("Тип комикса", data, "0")
internal class Publication(data: List<Pair<String, String>>) : SelectFilter("Публикация", data, "0")
internal class Subscription(data: List<Pair<String, String>>) : SelectFilter("Подписка", data, "0")
internal class MinPages : Filter.Text("Минимум страниц", state = "2")
internal class Genres(data: List<Pair<String, String>>) : MultiValueFilter("Категории", data)
internal class AgeRatings(data: List<Pair<String, String>>) : MultiValueFilter("Возрастная категория", data) {
    init {
        state.forEach { filter -> if (filter.name != "NC-17") filter.state = true }
    }
}
internal class Categories : SelectFilter("Разделы поиска", data, "comics") {
    companion object {
        val data = listOf(
            "Песочница" to "sandbox",
            "Каталог" to "comics",
            "Рекомендуемые" to "featured",
        )
    }
}
