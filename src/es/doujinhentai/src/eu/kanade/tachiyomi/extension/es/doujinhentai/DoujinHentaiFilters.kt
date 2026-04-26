package eu.kanade.tachiyomi.extension.es.doujinhentai

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

// Géneros/categorías → /lista-manga-hentai/category/{slug}
class GenreFilter :
    UriPartFilter(
        "Género",
        arrayOf(
            Pair("<todos>", ""),
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("Bikini", "bikini"),
            Pair("Casadas", "casadas"),
            Pair("Chica Con Pene", "chica-con-pene"),
            Pair("Cosplay", "cosplay"),
            Pair("Doble Penetracion", "doble-penetracion"),
            Pair("Ecchi", "ecchi"),
            Pair("Embarazada", "embarazada"),
            Pair("Enfermera", "enfermera"),
            Pair("Escolares", "escolares"),
            Pair("Full Color", "full-colo"),
            Pair("Futanari", "futanari"),
            Pair("Grandes Pechos", "grandes-pechos"),
            Pair("Harem", "harem"),
            Pair("Incesto", "incesto"),
            Pair("Interracial", "interracial"),
            Pair("Juguetes Sexuales", "juguetes-sexuales"),
            Pair("Lolicon", "lolicon"),
            Pair("Maduras", "maduras"),
            Pair("Mamadas", "mamadas"),
            Pair("Masturbacion", "masturbacion"),
            Pair("MILF", "milf"),
            Pair("Orgias", "orgias"),
            Pair("Profesores", "profesores"),
            Pair("Romance", "romance"),
            Pair("Shota", "shota"),
            Pair("Sin Censura", "sin-censura"),
            Pair("Sirvientas", "sirvientas"),
            Pair("Tentaculos", "tentaculos"),
            Pair("Tetonas", "tetonas"),
            Pair("Virgenes", "virgenes"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

// Tipo de obra → /lista-de-{doujin|manga|comic}
class TypeFilter :
    UriPartFilter(
        "Tipo de obra",
        arrayOf(
            Pair("<todos>", ""),
            Pair("Doujin", "doujin"),
            Pair("Manga", "manga"),
            Pair("Comic", "comic"),
        ),
    )

// Ordenación → ?orderby= (solo cuando no hay otro filtro de ruta activo)
class SortFilter :
    UriPartFilter(
        "Ordenar por (sin otros filtros)",
        arrayOf(
            Pair("Alfabético", "alphabet"),
            Pair("Más vistos", "views"),
            Pair("Más recientes", "last"),
        ),
    )

// Primera letra → /lista-manga-hentai/letra/{a-z|0}
class LetterFilter :
    UriPartFilter(
        "Primera letra",
        arrayOf(
            Pair("<todas>", ""),
            Pair("#  (0-9)", "0"),
            Pair("A", "a"), Pair("B", "b"), Pair("C", "c"), Pair("D", "d"),
            Pair("E", "e"), Pair("F", "f"), Pair("G", "g"), Pair("H", "h"),
            Pair("I", "i"), Pair("J", "j"), Pair("K", "k"), Pair("L", "l"),
            Pair("M", "m"), Pair("N", "n"), Pair("Ñ", "ñ"), Pair("O", "o"),
            Pair("P", "p"), Pair("Q", "q"), Pair("R", "r"), Pair("S", "s"),
            Pair("T", "t"), Pair("U", "u"), Pair("V", "v"), Pair("W", "w"),
            Pair("X", "x"), Pair("Y", "y"), Pair("Z", "z"),
        ),
    )

// Artista → /lista-manga-hentai/artist/{nombre}
class ArtistFilter : Filter.Text("Artista (ej: saigado, milftoon)")

// Autor → /lista-manga-hentai/author/{nombre}
class AuthorFilter : Filter.Text("Autor (ej: horori, milftoon)")

// Scanlator/usuario → /user/{nombre}?page=N
// El nombre es sensible a mayúsculas
class ScanlatorFilter : Filter.Text("Scanlator/usuario (ej: NekoCreme, Fritz Translations)")
