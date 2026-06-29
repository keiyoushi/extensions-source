package eu.kanade.tachiyomi.extension.pt.wolftoon

import eu.kanade.tachiyomi.source.model.Filter

abstract class Select<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

class StatusFilter(name: String, values: Array<String>) : Select<String>(name, values)

class TypeFilter(name: String, values: Array<String>) : Select<String>(name, values)

class GenreFilter(name: String, values: Array<String>) : Select<String>(name, values)

class OrderBy(name: String, values: Array<String>) : Select<String>(name, values)

val statusList: Array<String> = arrayOf(
    "Todos",
    "Em andamento",
    "Completo",
)
val typeList: Array<String> = arrayOf(
    "Todos",
    "Manhwa",
    "Manhua",
    "Mangá",
)

val orderByList: Array<String> = arrayOf(
    "Todos",
    "Mais Popular",
    "Mais Recentes",
    "Melhor Avaliado",
)
