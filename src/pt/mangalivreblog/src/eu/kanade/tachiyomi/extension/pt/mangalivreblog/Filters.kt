package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    RatingMinFilter(),
    SortFilter(),
    OrderFilter(),
)

interface UrlQueryFilter {
    fun selectedValue(): String
}

class RatingMinFilter :
    Filter.Select<String>("Avaliação Mínima", ratingMins.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = ratingMins[state].second
}

class SortFilter :
    Filter.Select<String>("Ordenar por", sorts.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = sorts[state].second
}

class OrderFilter :
    Filter.Select<String>("Ordem", orders.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun selectedValue() = orders[state].second
}

private val ratingMins = listOf(
    Pair("Qualquer avaliação", "0"),
    Pair("1 ou mais", "1"),
    Pair("2 ou mais", "2"),
    Pair("3 ou mais", "3"),
    Pair("4 ou mais", "4"),
    Pair("5 ou mais", "5"),
    Pair("6 ou mais", "6"),
    Pair("7 ou mais", "7"),
    Pair("8 ou mais", "8"),
    Pair("9 ou mais", "9"),
    Pair("10 ou mais", "10"),
)

private val sorts = listOf(
    Pair("Título", "title"),
    Pair("Data de adição", "date"),
    Pair("Avaliação", "rating"),
)

private val orders = listOf(
    Pair("Crescente", "asc"),
    Pair("Decrescente", "desc"),
)
