package eu.kanade.tachiyomi.extension.en.asiatoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal val browseEntries = listOf(
    "Home" to "en",
    "New" to "en/genres/New",
    "Completed" to "en/completed",
    "Page: Honey Toon" to "en/pages/honey-toon",
    "Page: Manhwa toon" to "en/pages/manhwa-toon",
    "Page: Manga toon" to "en/pages/manga-toon",
    "Page: Comics toon" to "en/pages/comics-toon",
    "Page: Toon God" to "en/pages/toon-god",
    "Page: Toon Porn" to "en/pages/toon-porn",
    "Genre: All" to "en/genres",
    "Genre: Vanilla" to "en/genres/Vanilla",
    "Genre: Monster Girls" to "en/genres/Monster_Girls",
    "Genre: School Life" to "en/genres/School_Life",
    "Genre: Horror Thriller" to "en/genres/Horror_Thriller",
    "Genre: Slice of Life" to "en/genres/Slice_of_Life",
    "Genre: Supernatural" to "en/genres/Supernatural",
    "Genre: Office" to "en/genres/Office",
    "Genre: Sexy" to "en/genres/Sexy",
    "Genre: MILF" to "en/genres/MILF",
    "Genre: In-Law" to "en/genres/In-Law",
    "Genre: Harem" to "en/genres/Harem",
    "Genre: Cheating" to "en/genres/Cheating",
    "Genre: College" to "en/genres/College",
    "Genre: Isekai" to "en/genres/Isekai",
    "Genre: UNCENSORED" to "en/genres/UNCENSORED",
    "Genre: GL" to "en/genres/GL",
    "Genre: sexy comics" to "en/genres/sexy_comics",
    "Genre: Sci-fi" to "en/genres/Sci-fi",
    "Genre: Sports" to "en/genres/Sports",
    "Genre: School life" to "en/genres/School_life",
    "Genre: Historical" to "en/genres/Historical",
    "Genre: Action" to "en/genres/Action",
    "Genre: Thriller" to "en/genres/Thriller",
    "Genre: Horror" to "en/genres/Horror",
    "Genre: Fantasy" to "en/genres/Fantasy",
    "Genre: Comedy" to "en/genres/Comedy",
    "Genre: Drama" to "en/genres/Drama",
    "Genre: BL" to "en/genres/BL",
    "Genre: Romance" to "en/genres/Romance",
)

internal class BrowseFilter :
    Filter.Select<String>(
        "Browse",
        browseEntries.map { it.first }.toTypedArray(),
    ) {
    val selected get() = browseEntries[state].second
}

internal fun browseFilters() = FilterList(
    Filter.Header("Doesn't work with Text search"),
    Filter.Separator(),
    BrowseFilter(),
)
