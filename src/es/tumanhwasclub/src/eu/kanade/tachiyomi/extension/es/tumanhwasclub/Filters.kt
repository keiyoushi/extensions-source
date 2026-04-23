package eu.kanade.tachiyomi.extension.es.tumanhwasclub

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun selectedValue() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Ordenar Por",
        arrayOf(
            "Cualquiera" to "",
            "Última Actualización" to "-updated_at",
            "Más Popular" to "-views",
            "A-Z" to "name",
            "Z-A" to "-name",
            "Más Recientes" to "-created_at",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipos",
        arrayOf(
            "Cualquiera" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
            "One-shot" to "one-shot",
            "Doujinshi" to "doujinshi",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Géneros",
        arrayOf(
            "Cualquiera" to "",
            "Academia" to "academia",
            "Accion" to "accion",
            "Amigos con derechos" to "amigos-con-derechos",
            "Amigos de la infancia" to "amigos-de-la-infancia",
            "Apocaliptico" to "apocaliptico",
            "Artes Marciales" to "artes-marciales",
            "AV" to "av",
            "Aventura" to "aventura",
            "Boys Love" to "boys-love",
            "Ciencia Ficción" to "ciencia-ficcion",
            "Comedia" to "comedia",
            "Cultivo" to "cultivo",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Ejercito" to "ejercito",
            "Fantasia" to "fantasia",
            "Girls love" to "girls-love",
            "Gore" to "gore",
            "Harem" to "harem",
            "Historias cortas" to "historias-cortas",
            "Hombres lobo" to "hombres-lobo",
            "Horror" to "horror",
            "Intercambio de parejas" to "intercambio-de-parejas",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Madrastra" to "madrastra",
            "Madre e hija" to "madre-e-hija",
            "Magia" to "magia",
            "Milf" to "milf",
            "Misterio" to "misterio",
            "Mujer casada" to "mujer-casada",
            "Mujer mayor" to "mujer-mayor",
            "NTR" to "ntr",
            "Pareja casada" to "pareja-casada",
            "Primer amor" to "primer-amor",
            "Psicológico" to "psicologico",
            "Rape" to "rape",
            "Realidad virtual" to "realidad-virtual",
            "Recuentos de la vida" to "recuentos-de-la-vida",
            "Reencarnacion" to "reencarnacion",
            "Relacion secreta" to "relacion-secreta",
            "Romance" to "romance",
            "Sistema de Niveles" to "sistema-de-niveles",
            "Sobrenatural" to "sobrenatural",
            "Superpoderes" to "superpoderes",
            "Thriller" to "thriller",
            "Tragedia" to "tragedia",
            "Universidad" to "universidad",
            "Vampiros" to "vampiros",
            "Venganza" to "venganza",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        arrayOf(
            "Cualquiera" to "",
            "En Curso" to "1",
            "Completado" to "2",
            "En Pausa" to "3",
            "Cancelado" to "4",
        ),
    )

class ContentFilter :
    UriPartFilter(
        "Contenido (+18)",
        arrayOf(
            "Cualquiera" to "",
            "No 18+" to "0",
            "18+ Only" to "1",
        ),
    )
