package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(
    defaultPart: String = "latest",
) : UriPartFilter(
    "Trier par",
    arrayOf(
        Pair("Dernières sorties", "latest"),
        Pair("Date d'ajout", "date"),
        Pair("Plus vues", "views"),
        Pair("Mieux notées", "rating"),
        Pair("Titre (A-Z)", "title"),
    ),
    defaultPart,
)

class FormatFilter :
    UriPartFilter(
        "Format",
        arrayOf(
            Pair("Tous", ""),
            Pair("Manga", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Doujinshi", "DOUJINSHI"),
            Pair("One Shot", "ONE_SHOT"),
            Pair("Pornwha", "PORNHWA"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Statut",
        arrayOf(
            Pair("Tous", ""),
            Pair("En cours", "ONGOING"),
            Pair("Terminé", "COMPLETED"),
            Pair("En pause", "HIATUS"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Tous", ""),
            Pair("Action", "Action"),
            Pair("Adultère", "Adultère"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Harem", "Harem"),
            Pair("Isekai", "Isekai"),
            Pair("Mature", "Mature"),
            Pair("Milf", "Milf"),
            Pair("Romance", "Romance"),
            Pair("Slice of Life", "Slice of Life"),
            Pair("Yuri", "Yuri"),
        ),
    )

class GenreTextFilter : Filter.Text("Genre personnalisé (ex. Romance)")

class TagTextFilter : Filter.Text("Tag (ex. Au bureau, Hardcore, NTR)")

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
) {
    fun toUriPart(): String = vals[state].second
}
