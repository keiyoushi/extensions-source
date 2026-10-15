package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf("Last Update", "Views", "Post Date", "Name"),
        Selection(0, false),
    )

class MatchingLogic : Filter.Select<String>("Matching Logic", arrayOf("OR", "AND"), 1)

class AuthorFilter : Filter.Text("Author")

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
