package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val slug: String) : Filter.TriState(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
class StatusFilter : Filter.Select<String>("Status", STATUS_VALUES.map { it.first }.toTypedArray())
class AuthorFilter : Filter.Text("Author")
class GroupFilter : Filter.Text("Scanlation Group")
class SortFilter : Filter.Sort("Sort by", SORT_VALUES.map { it.first }.toTypedArray(), Selection(1, false))

val STATUS_VALUES = arrayOf(
    "All Status" to "",
    "Ongoing" to "2",
    "Completed" to "1",
    "Paused" to "3",
)

val SORT_VALUES = arrayOf(
    "Name" to "name",
    "Most Views" to "views",
    "Last update" to "last_update",
)
