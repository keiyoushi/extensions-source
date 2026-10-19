package eu.kanade.tachiyomi.extension.es.kumanga

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, val param: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) })

class TypeFilter :
    CheckBoxGroup(
        "Tipo",
        "type_filter",
        listOf(
            "Manga" to "1",
            "Manhwa" to "2",
            "Manhua" to "3",
            "One shot" to "4",
            "Doujinshi" to "5",
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "Estado",
        "status_filter",
        listOf(
            "Activo" to "1",
            "Finalizado" to "2",
            "Inconcluso" to "3",
        ),
    )

class GenreFilter :
    CheckBoxGroup(
        "Géneros",
        "category_filter",
        listOf(
            "Acción" to "1",
            "Artes marciales" to "2",
            "Automóviles" to "3",
            "Aventura" to "4",
            "Boys Love" to "44",
            "Ciencia Ficción" to "5",
            "Comedia" to "6",
            "Demonios" to "7",
            "Deportes" to "8",
            "Doujinshi" to "9",
            "Drama" to "10",
            "Ecchi" to "11",
            "Espacio exterior" to "12",
            "Fantasía" to "13",
            "Gender bender" to "14",
            "Girls Love" to "45",
            "Gore" to "46",
            "Harem" to "15",
            "Histórico" to "17",
            "Horror" to "18",
            "Isekai" to "51",
            "Josei" to "19",
            "Juegos" to "20",
            "Locura" to "21",
            "Magia" to "22",
            "Mecha" to "23",
            "Militar" to "24",
            "Misterio" to "25",
            "Música" to "26",
            "Niños" to "27",
            "Parodia" to "28",
            "Policía" to "29",
            "Psicológico" to "30",
            "Recuentos de la vida" to "31",
            "Reencarnación" to "49",
            "Romance" to "32",
            "Samurai" to "33",
            "Seinen" to "34",
            "Shoujo" to "35",
            "Shoujo Ai" to "36",
            "Shounen" to "37",
            "Shounen Ai" to "38",
            "Sobrenatural" to "39",
            "Súperpoderes" to "41",
            "Supervivencia" to "50",
            "Suspenso" to "40",
            "Terror" to "47",
            "Tragedia" to "48",
            "Vampiros" to "42",
            "Vida escolar" to "43",
        ),
    )
