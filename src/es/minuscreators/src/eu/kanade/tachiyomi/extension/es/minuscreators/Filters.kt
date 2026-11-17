package eu.kanade.tachiyomi.extension.es.minuscreators

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : SelectFilter("Ordenar por", listOf("Más populares" to "populares", "Más recientes" to "recientes"))

class TypeFilter : SelectFilter("Tipo", listOf("Cómics y mangas" to null, "Cómic" to "comic", "Manga" to "manga"))

// "nsfw" is also a category on the site, which turns on its NSFW filter instead
class GenreFilter : SelectFilter("Categoría", listOf("Todas" to null) + GENRES.filterKeys { it != "nsfw" }.map { (value, label) -> label to value })

class NsfwFilter : SelectFilter("NSFW", listOf("Mostrar todo" to "todos", "Ocultar NSFW" to "sin-nsfw", "Solo NSFW" to "solo-nsfw"))

val GENRES = mapOf(
    "accion" to "Acción",
    "aventura" to "Aventura",
    "comedia" to "Comedia",
    "drama" to "Drama",
    "fantasia" to "Fantasía",
    "romance" to "Romance",
    "terror" to "Terror",
    "ciencia-ficcion" to "Ciencia ficción",
    "misterio" to "Misterio",
    "suspenso" to "Suspenso",
    "sobrenatural" to "Sobrenatural",
    "psicologico" to "Psicológico",
    "slice-of-life" to "Slice of life",
    "vida-escolar" to "Vida escolar",
    "deportes" to "Deportes",
    "artes-marciales" to "Artes marciales",
    "mecha" to "Mecha",
    "isekai" to "Isekai",
    "historico" to "Histórico",
    "musica" to "Música",
    "cocina" to "Cocina",
    "magia" to "Magia",
    "superheroes" to "Superhéroes",
    "crimen" to "Crimen",
    "post-apocaliptico" to "Post-apocalíptico",
    "cyberpunk" to "Cyberpunk",
    "steampunk" to "Steampunk",
    "guerra" to "Guerra",
    "parodia" to "Parodia",
    "tragedia" to "Tragedia",
    "reconfortante" to "Reconfortante",
    "novela-grafica" to "Novela gráfica",
    "informativo" to "Informativo",
    "biografico" to "Biográfico",
    "animales" to "Animales",
    "supervivencia" to "Supervivencia",
    "reencarnacion" to "Reencarnación",
    "mitologia" to "Mitología",
    "shonen" to "Shonen",
    "shojo" to "Shojo",
    "seinen" to "Seinen",
    "josei" to "Josei",
    "kodomo" to "Kodomo",
    "boys-love" to "Boys Love",
    "girls-love" to "Girls Love",
    "nsfw" to "NSFW",
)

fun getFilters() = FilterList(SortFilter(), TypeFilter(), GenreFilter(), NsfwFilter())
