package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

open class Tag(val id: String, name: String) : Filter.CheckBox(name)

class AuthorFilter(title: String) : Filter.Text(title)
class ArtistFilter(title: String) : Filter.Text(title)
class YearFilter(title: String) : Filter.Text(title)
class StatusFilter(title: String, status: List<Tag>) :
    Filter.Group<Tag>(title, status)

class OrderByFilter(title: String, options: List<Pair<String, String>>, state: Int = 0) :
    UriPartFilter(title, options.toTypedArray(), state)

class GenreConditionFilter(title: String, options: Array<String>) : UriPartFilter(
    title,
    options.zip(arrayOf("", "1")).toTypedArray(),
)

class AdultContentFilter(title: String, options: Array<String>) : UriPartFilter(
    title,
    options.zip(arrayOf("", "0", "1")).toTypedArray(),
)

class GenreList(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
