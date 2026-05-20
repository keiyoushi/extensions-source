package eu.kanade.tachiyomi.extension.es.lmtoonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CheckBoxItem(name: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(name: String, entries: List<String>) : Filter.Group<CheckBoxItem>(name, entries.map { CheckBoxItem(it) })

class GenreFilter(genres: List<String>) : CheckBoxGroup("Géneros", genres)

abstract class SelectFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val selected get() = vals[state].second
}

class StatusFilter(statuses: List<Pair<String, String>>) : SelectFilter("Estado", statuses)

class DemographicFilter(demographics: List<Pair<String, String>>) : SelectFilter("Demografía", demographics)

class TypeFilter(types: List<Pair<String, String>>) : SelectFilter("Tipo", types)

class NsfwFilter(options: List<Pair<String, String>>) : SelectFilter("+18", options)

class OrderFilter(orders: List<Pair<String, String>>) : SelectFilter("Orden", orders)

fun getFilters(): FilterList = FilterList(
    GenreFilter(
        listOf(
            "Acción",
            "Artes Marciales",
            "Aventuras",
            "Carreras",
            "Ciencia Ficción",
            "Comedia",
            "Demencia",
            "Demonios",
            "Deportes",
            "Drama",
            "Ecchi",
            "Escolares",
            "Gore",
            "Harem",
            "Isekai",
            "Juegos",
            "Magia",
            "Mecha",
            "Militar",
            "Misterio",
            "Música",
            "Parodia",
            "Policía",
            "Psicológico",
            "Recuentos de la vida",
            "Romance",
            "Romcom",
            "Samurai",
            "Sobrenatural",
            "Superpoderes",
            "Suspenso",
            "Terror",
            "Vampiros",
            "Yaoi",
            "Yuri",
        ),
    ),
    StatusFilter(
        listOf(
            Pair("Todos", ""),
            Pair("En emisión", "ongoing"),
            Pair("Finalizado", "completed"),
            Pair("Pausado", "paused"),
        ),
    ),
    DemographicFilter(
        listOf(
            Pair("Todos", ""),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
        ),
    ),
    TypeFilter(
        listOf(
            Pair("Todos", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("One-shot", "oneshot"),
        ),
    ),
    NsfwFilter(
        listOf(
            Pair("Todos", ""),
            Pair("Sin +18", "hide"),
            Pair("Solo +18", "only"),
        ),
    ),
    OrderFilter(
        listOf(
            Pair("A-Z", "a-z"),
            Pair("Más recientes", "recents"),
            Pair("Mejor valorados", "views"),
        ),
    ),
)
