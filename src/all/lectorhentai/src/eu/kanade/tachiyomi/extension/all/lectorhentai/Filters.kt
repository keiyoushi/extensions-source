package eu.kanade.tachiyomi.extension.all.lectorhentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    TypeFilter(),
    SortFilter(),
    GenreFilter(),
)

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class TypeFilter : SelectFilter("Type", listOf("All" to "all", "Hentai" to "hentai", "Yaoi" to "yaoi", "Yuri" to "yuri"))

class SortFilter : SelectFilter("Sort by", listOf("Popular" to "popular", "Latest" to "latest", "A-Z" to "title", "Z-A" to "titlereverse"))

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<Genre>(
        "Genres",
        listOf(
            "Ahegao", "Big Breasts", "BlowJob", "Femdom", "Mature", "Nympho", "Student", "Bukkake", "Forced", "Orgy", "Pregnant", "Public Sex",
            "Rape", "Anal", "Bondage", "Fetish", "Incest", "Virgin", "Romance", "Vanilla", "Uncensored", "Comedy", "Milf", "Monsters", "Colour",
            "Furry", "Lolicon", "Small Breast", "Domination", "Parody", "Fantasy", "FootJob", "Harem", "Adultery", "Adventure", "Cheating",
            "Netorare", "Tsundere", "Toys", "Futanari", "Sport", "Bestiality", "Horror", "Yandere", "Tentacles", "3D", "Shotacon",
        ).map(::Genre),
    )
