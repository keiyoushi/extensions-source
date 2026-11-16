package eu.kanade.tachiyomi.extension.es.animebbg

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Select<String>("Ordenar por", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class FacetCheckBox(name: String, val value: String) : Filter.CheckBox(name)

// Each group goes in the search as <parameter>[]: the site matches works with any of the checked values
open class FacetGroup(name: String, val parameter: String, values: List<Pair<String, String>>) : Filter.Group<FacetCheckBox>(name, values.map { FacetCheckBox(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.value }
}

fun getFilters() = FilterList(
    SortFilter(),
    Filter.Header("Dentro de cada grupo, basta con uno de los valores marcados"),
    FacetGroup("Origen", "origins", ORIGINS),
    FacetGroup("Demografía", "demo", DEMOGRAPHICS),
    FacetGroup("Estado", "status", STATUSES),
    FacetGroup("Género", "genres", GENRES),
    FacetGroup("Tema", "genres", THEMES),
    FacetGroup("Formato", "genres", FORMATS),
)

private val SORTS = listOf(
    "Más seguidos" to "follows",
    "Relevancia" to "relevance",
    "Mejor valorados" to "rating",
    "Más recientes" to "newest",
    "Más capítulos" to "chapters",
    "A-Z" to "az",
)

private val ORIGINS = listOf("Manga" to "manga", "Manhwa" to "manhwa", "Manhua" to "manhua")

private val DEMOGRAPHICS = listOf("Shounen" to "shounen", "Seinen" to "seinen", "Josei" to "josei", "Shoujo" to "shoujo")

private val STATUSES = listOf("En curso" to "ongoing", "Finalizado" to "completed", "En pausa" to "hiatus", "Cancelado" to "cancelled")

// The site's genre, theme and format ids, without those no work has yet
private val GENRES = listOf(
    "Acción" to "1",
    "Aventura" to "2",
    "Boys' Love" to "15",
    "Ciencia Ficción" to "4",
    "Comedia" to "5",
    "Crimen" to "16",
    "Deportes" to "17",
    "Drama" to "6",
    "Fantasía" to "7",
    "Girls' Love" to "18",
    "Histórico" to "19",
    "Horror" to "8",
    "Misterio" to "9",
    "Psicológico" to "11",
    "Romance" to "12",
    "Slice of Life" to "13",
    "Thriller" to "14",
    "Tragedia" to "21",
    "Wuxia" to "22",
)

private val THEMES = listOf(
    "Academia" to "23",
    "Acoso escolar" to "42",
    "Apocalíptico" to "43",
    "Artes marciales" to "24",
    "Cazador" to "25",
    "Cocina" to "45",
    "De débil a fuerte" to "46",
    "Delincuente juvenil" to "47",
    "Demonio" to "27",
    "Escolar" to "28",
    "Guerra" to "48",
    "Harem" to "29",
    "Isekai" to "30",
    "Juego mortal" to "50",
    "Mafia" to "51",
    "Magia" to "31",
    "Maldición" to "52",
    "Matrimonio concertado" to "53",
    "Mazmorra" to "32",
    "Mecha" to "54",
    "Medieval" to "55",
    "Militar" to "56",
    "Monstruo" to "57",
    "Nobleza" to "33",
    "Protagonista femenina" to "59",
    "Reencarnación" to "34",
    "Regresión" to "35",
    "Samurái" to "61",
    "Sistema" to "36",
    "Superpoderes" to "37",
    "Supervivencia" to "38",
    "Torre" to "62",
    "Triángulo amoroso" to "63",
    "Venganza" to "40",
    "Viaje en el tiempo" to "64",
    "Villana" to "41",
    "Zombi" to "65",
)

private val FORMATS = listOf(
    "Formato vertical" to "73",
    "A todo color" to "75",
    "Coloreado oficial" to "76",
    "Adaptación de novela" to "81",
)
