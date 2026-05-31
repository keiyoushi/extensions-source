package eu.kanade.tachiyomi.extension.es.ragnascans

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<CheckBoxFilter>(
        "Géneros",
        listOf(
            CheckBoxFilter("Acción", "Acción"),
            CheckBoxFilter("Artes marciales", "Artes marciales"),
            CheckBoxFilter("Aventura", "Aventura"),
            CheckBoxFilter("Comedia", "Comedia"),
            CheckBoxFilter("Drama", "Drama"),
            CheckBoxFilter("Fantasía", "Fantasía"),
            CheckBoxFilter("Josie", "Josie"),
            CheckBoxFilter("Magia", "Magia"),
            CheckBoxFilter("Recuentos de la vida", "Recuentos de la vida"),
            CheckBoxFilter("Romance", "Romance"),
            CheckBoxFilter("Seinen", "Seinen"),
            CheckBoxFilter("Shonen", "Shonen"),
            CheckBoxFilter("Supervivencia", "Supervivencia"),
            CheckBoxFilter("Venganza", "Venganza"),
            CheckBoxFilter("Vida escolar", "Vida escolar"),
        ),
    )

class StatusFilter :
    Filter.Group<CheckBoxFilter>(
        "Estado",
        listOf(
            CheckBoxFilter("En emisión", "emision"),
            CheckBoxFilter("Finalizado", "finalizado"),
            CheckBoxFilter("Hiatus", "hiatus"),
            CheckBoxFilter("Pausado", "pausado"),
            CheckBoxFilter("Cancelado", "cancelado"),
        ),
    )

class TypeFilter :
    Filter.Group<CheckBoxFilter>(
        "Tipo",
        listOf(
            CheckBoxFilter("Manhwa", "manhwa"),
            CheckBoxFilter("Manga", "manga"),
            CheckBoxFilter("Manhua", "manhua"),
            CheckBoxFilter("Novela", "novela"),
        ),
    )

class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        arrayOf(
            "Más recientes",
            "Más populares",
            "Mejor valorados",
            "A — Z",
            "Z — A",
            "Recién agregados",
        ),
        1, // Default is "Más populares" (vistas)
    ) {
    private val queryValues = arrayOf("actualizado", "vistas", "votos", "az", "za", "nuevo")
    val selectedValue get() = queryValues[state]
}
