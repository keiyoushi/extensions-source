package eu.kanade.tachiyomi.extension.uk.comixtopia

import eu.kanade.tachiyomi.source.model.Filter

abstract class OrderByFilter(
    displayName: String,
    val options: List<Pair<String, String>>,
    state: Selection,
) : Filter.Sort(
    displayName,
    options.map { it.first }.toTypedArray(),
    state,
) {
    val selected get() = options[state!!.index].second
    val order get() = if (state!!.ascending) "asc" else "desc"
}

class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

abstract class MultiValueFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = options.map { MultiValueOption(it.first, it.second) },
) {
    val selected get() = state.filter { it.state }.map { it.value }.takeIf { it.isNotEmpty() }
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name = name,
    values = options.map { it.first }.toTypedArray(),
    state = options.indexOfFirst { it.second == defaultValue }.takeIf { it >= 0 } ?: 0,
) {
    val selected get() = options[state].second.takeIf { it.isNotBlank() }
}

internal class OrderBy :
    OrderByFilter(
        "Сортувати за",
        listOf(
            "Датою" to "updated_at",
            "Популярністю" to "metadata(views)",
            "Кількістю перекладених випусків" to "issue_count",
            "Рік випуску" to "release_year",
        ),
        Selection(1, false),
    )

internal class GenreFilter(data: List<Pair<String, String>>) : MultiValueFilter("Жанри", data)
internal class AuthorsFilter(data: List<Pair<String, String>>) : MultiValueFilter("Автори", data)
internal class PublishersFilter(data: List<Pair<String, String>>) : MultiValueFilter("Видавництва", data)

internal class AgeLimit : MultiValueFilter("Вікове обмеження", options) {
    companion object {
        val options = listOf(
            "0+" to "0",
            "6+" to "6",
            "13+" to "13",
            "16+" to "16",
            "18+" to "18",
        )
    }
}

internal class ComicsStatus : SelectFilter("Статус виходу", options, "") {
    companion object {
        val options = listOf(
            "Будь-який статус" to "",
            "Триває" to "ongoing",
            "Закінчено" to "finished",
        )
    }
}
