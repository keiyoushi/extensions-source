package eu.kanade.tachiyomi.extension.fr.scansfr

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun getFilters() = FilterList(
    HasChaptersFilter(),
    SortFilter(),
    TypeFilter(),
    StatusFilter(),
    GenreFilter(),
)

internal class HasChaptersFilter : Filter.CheckBox("Avec chapitres uniquement")

internal class SortFilter :
    SelectFilter(
        "Trier par",
        listOf(
            Pair("Populaire", "popular"),
            Pair("Nouveautés", "latest"),
            Pair("Mis à jour", "updated"),
            Pair("Note", "rating"),
            Pair("A-Z", "alphabetical"),
        ),
    )

internal class TypeFilter :
    SelectFilter(
        "Type",
        listOf(
            Pair("Tous", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Webtoon", "webtoon"),
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Statut",
        listOf(
            Pair("Tous", ""),
            Pair("En cours", "ongoing"),
            Pair("Terminé", "completed"),
            Pair("En pause", "hiatus"),
            Pair("Abandonné", "cancelled"),
        ),
    )

internal class GenreFilter :
    SelectFilter(
        "Genre",
        listOf(Pair("Tous", "")) + (GENRES + NSFW_GENRES).sorted().map { Pair(it, it) },
    )

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

private val GENRES = listOf(
    "Action", "Arts Martiaux", "Aventure", "Comédie", "Drame",
    "Fantasy", "Historique", "Horreur", "Isekai", "Josei", "Mecha",
    "Mystère", "Psychologique", "Romance", "Sci-Fi", "Seinen",
    "Shojo", "Shonen", "Slice of Life", "Sport", "Surnaturel",
    "Thriller", "Tragédie", "Vie Scolaire",
)

private val NSFW_GENRES = listOf(
    "Boy's Love",
    "Ecchi",
    "Harem",
    "Hentai",
    "Mature",
    "Smut",
    "Yuri",
)
