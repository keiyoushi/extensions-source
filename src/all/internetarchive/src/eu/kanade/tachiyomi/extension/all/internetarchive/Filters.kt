package eu.kanade.tachiyomi.extension.all.internetarchive

import eu.kanade.tachiyomi.source.model.Filter

class CollectionFilter : Filter.Select<String>("Collection", COLLECTIONS.map { it.first }.toTypedArray()) {
    val collection get() = COLLECTIONS[state].second
}

class SortFilter(default: String = "week") : Filter.Sort("Sort", SORTS.map { it.first }.toTypedArray(), Selection(SORTS.indexOfFirst { it.second == default }, false)) {
    val sort get() = state?.let { "${SORTS[it.index].second} ${if (it.ascending) "asc" else "desc"}" }
}

class FieldFilter(name: String, val field: String) : Filter.Text(name)

private val COLLECTIONS = listOf(
    "Comic Books and Graphic Novels" to "comics",
    "The Manga Library" to "manga_library",
    "Manga: Unsorted Collection" to "manga_unsorted",
    "Dojinshi" to "manga_dojinshi",
    "Manga: Artbooks and Art Collections" to "manga_artbooks",
    "Anime and Manga Magazines" to "animemangamagazines",
    "Webcomic Universe" to "webcomicuniverse",
    "SmackJeeves Web Comics" to "smackjeeves-web-comics",
    "Adult and Mature Comics" to "adultcomics",
    "Adult-Oriented Manga" to "adultmanga",
    "All collections" to null,
)

private val SORTS = listOf(
    "Weekly views" to "week",
    "All-time views" to "downloads",
    "Date archived" to "addeddate",
    "Date published" to "date",
    "Title" to "titleSorter",
    "Creator" to "creatorSorter",
    "Rating" to "avg_rating",
)
