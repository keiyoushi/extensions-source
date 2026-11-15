package eu.kanade.tachiyomi.extension.es.colorcitoscan

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        arrayOf("Popularidad", "Actualización", "Nombre", "Valoración"),
        Selection(0, false),
    )

class StatusFilter :
    Filter.Select<String>(
        "Estado",
        STATUSES,
    ) {
    val selected get() = STATUS_VALUES[state]

    companion object {
        private val STATUSES = arrayOf(
            "Todos",
            "En emisión",
            "En pausa",
            "Abandonado",
            "Finalizado",
            "Cancelado",
        )
        private val STATUS_VALUES = arrayOf(
            null,
            1,
            2,
            3,
            4,
            5,
        )
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        TYPES,
    ) {
    val selected get() = TYPES[state].takeIf { state > 0 }

    companion object {
        private val TYPES = arrayOf(
            "Todos",
            "Manhwa +19",
            "Manhwa",
            "Manhwa BL",
            "Manga",
            "Webtoon",
            "Manhua",
            "Novela",
        )
    }
}

class Genre(name: String) : Filter.TriState(name)

class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

fun getGenreList() = listOf(
    Genre("Accion"),
    Genre("Adulto"),
    Genre("Animación"),
    Genre("Apocaliptico"),
    Genre("Artes Marciales"),
    Genre("Aventura"),
    Genre("Bestias"),
    Genre("Boys Love"),
    Genre("Ciberpunk"),
    Genre("Ciencia Ficción"),
    Genre("Comedia"),
    Genre("Crimen"),
    Genre("Demonios"),
    Genre("Deporte"),
    Genre("Dragones"),
    Genre("Drama"),
    Genre("Ecchi"),
    Genre("Extranjero"),
    Genre("Familia"),
    Genre("Fantasia"),
    Genre("Fantasmas"),
    Genre("Género Bender"),
    Genre("Girls Love"),
    Genre("Gore"),
    Genre("Guerra"),
    Genre("Harem"),
    Genre("Historia"),
    Genre("Horror"),
    Genre("Isekai"),
    Genre("Magia"),
    Genre("Mazmorra"),
    Genre("Mecha"),
    Genre("Militar"),
    Genre("Misterio"),
    Genre("Musica"),
    Genre("Niños"),
    Genre("Oeste"),
    Genre("Parodia"),
    Genre("Policiaco"),
    Genre("Psicológico"),
    Genre("Realidad"),
    Genre("Realidad Virtual"),
    Genre("Recuentos de la vida"),
    Genre("Reencarnación"),
    Genre("Regresión"),
    Genre("Romance"),
    Genre("Samurái"),
    Genre("Sistemas"),
    Genre("Smut"),
    Genre("Supernatural"),
    Genre("Superpoderes"),
    Genre("Supervivencia"),
    Genre("Telenovela"),
    Genre("Thriller"),
    Genre("Tragedia"),
    Genre("Transmigración"),
    Genre("Vampiros"),
    Genre("Venganza"),
    Genre("Vida Escolar"),
    Genre("Video Juegos"),
    Genre("Villana"),
)
