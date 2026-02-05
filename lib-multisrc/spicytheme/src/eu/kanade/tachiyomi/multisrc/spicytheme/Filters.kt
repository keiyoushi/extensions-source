package eu.kanade.tachiyomi.multisrc.spicytheme

import eu.kanade.tachiyomi.source.model.Filter

class FilterOption(val name: String, val value: String)

class OptionCheckBox(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    displayName: String,
    val queryParameter: String,
    options: List<FilterOption>,
) : Filter.Group<OptionCheckBox>(
    displayName,
    options.map { OptionCheckBox(it.name, it.value) },
) {
    fun toUriPart(): String = state.filter { it.state }.joinToString(",") { it.value }
}

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        sortables.map { it.name }.toTypedArray(),
        Selection(defaultIndex, false),
    ) {
    companion object {
        const val ID_POPULAR = "6"
        const val ID_LATEST = "3"

        private val sortables = listOf(
            // Disabled: server returns 500 for this sort option
            // FilterOption("Vistas", "1"),

            FilterOption("Nombre", "2"),
            FilterOption("Actualización", "3"),
            FilterOption("Recientemente agregados", "4"),
            FilterOption("N° capítulos", "5"),
            FilterOption("N° seguidores", "6"),
        )

        private val defaultIndex = sortables.indexOfFirst { it.value == ID_LATEST }
    }

    fun toUriPart(): String {
        val index = state?.index ?: defaultIndex
        return sortables[index].value
    }

    fun getSortDirection(): String = if (state?.ascending == true) "asc" else "desc"
}

class GenreFilter :
    UriMultiSelectFilter(
        "Géneros",
        "gendersId",
        listOf(
            FilterOption("Accion", "1"),
            FilterOption("Adulto", "56"),
            FilterOption("Animación", "3"),
            FilterOption("Apocaliptico", "4"),
            FilterOption("Artes Marciales", "23"),
            FilterOption("Aventura", "2"),
            FilterOption("Bestias", "64"),
            FilterOption("Boys Love", "5"),
            FilterOption("Boys Love", "61"),
            FilterOption("Ciberpunk", "8"),
            FilterOption("Ciencia Ficción", "36"),
            FilterOption("Comedia", "6"),
            FilterOption("Crimen", "7"),
            FilterOption("Demonios", "9"),
            FilterOption("Deporte", "39"),
            FilterOption("Dragones", "63"),
            FilterOption("Drama", "10"),
            FilterOption("Ecchi", "11"),
            FilterOption("Extranjero", "14"),
            FilterOption("Familia", "12"),
            FilterOption("Fantasia", "13"),
            FilterOption("Fantasmas", "62"),
            FilterOption("Género Bender", "15"),
            FilterOption("Girls Love", "16"),
            FilterOption("Gore", "17"),
            FilterOption("Guerra", "47"),
            FilterOption("Harem", "18"),
            FilterOption("Historia", "19"),
            FilterOption("Horror", "20"),
            FilterOption("Isekai", "53"),
            FilterOption("Magia", "22"),
            FilterOption("Mazmorra", "49"),
            FilterOption("Mecha", "24"),
            FilterOption("Militar", "25"),
            FilterOption("Misterio", "26"),
            FilterOption("Musica", "27"),
            FilterOption("Niños", "21"),
            FilterOption("Oeste", "48"),
            FilterOption("Parodia", "28"),
            FilterOption("Policiaco", "29"),
            FilterOption("Psicológico", "30"),
            FilterOption("Realidad", "31"),
            FilterOption("Realidad Virtual", "46"),
            FilterOption("Recuentos de la vida", "37"),
            FilterOption("Reencarnación", "32"),
            FilterOption("Regresion", "52"),
            FilterOption("Regresión", "58"),
            FilterOption("Romance", "33"),
            FilterOption("Samurái", "34"),
            FilterOption("Sistemas", "50"),
            FilterOption("Smut", "57"),
            FilterOption("Supernatural", "40"),
            FilterOption("Superpoderes", "41"),
            FilterOption("Supervivencia", "42"),
            FilterOption("Telenovela", "38"),
            FilterOption("Thriller", "43"),
            FilterOption("Tragedia", "44"),
            FilterOption("Transmigración", "59"),
            FilterOption("Vampiros", "45"),
            FilterOption("Venganza", "51"),
            FilterOption("Vida Escolar", "35"),
            FilterOption("Video Juegos", "54"),
            FilterOption("Villana", "55"),
        ),
    )

class StatusFilter :
    UriMultiSelectFilter(
        "Estado",
        "state",
        listOf(
            FilterOption("En emisión", "1"),
            FilterOption("En pausa", "2"),
            FilterOption("Abandonado", "3"),
            FilterOption("Finalizado", "4"),
            FilterOption("Cancelado", "5"),
        ),
    )

class OriginFilter :
    UriMultiSelectFilter(
        "Origen",
        "origin",
        listOf(
            FilterOption("Manhwa", "2"),
            FilterOption("Manhua", "3"),
            FilterOption("Manga", "4"),
            FilterOption("Webtoon", "1"),
            FilterOption("Novela", "9"),
            FilterOption("Manhwa +19", "5"),
            FilterOption("+19 Sin Censura", "6"),
            FilterOption("BL Sin Censura", "7"),
            FilterOption("Manhwa BL", "8"),
            FilterOption("Manhua BL", "10"),
            FilterOption("Novela visual", "11"),
        ),
    )
