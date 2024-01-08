package eu.kanade.tachiyomi.extension.en.kouhaiwork

import eu.kanade.tachiyomi.source.model.Filter

class Genre(val id: Int, name: String) : Filter.CheckBox(name)

private val genres: List<Genre>
    get() = listOf(
        Genre(1, "Romance"),
        Genre(2, "Comedy"),
        Genre(3, "Slice of Life"),
        Genre(4, "Fantasy"),
        Genre(5, "Sci-Fi"),
        Genre(6, "Psychological"),
        Genre(7, "Horror"),
        Genre(8, "Mystery"),
        Genre(9, "Girls' Love"),
        Genre(10, "Drama"),
        Genre(11, "Action"),
        Genre(12, "Ecchi"),
        Genre(13, "Adventure"),
    )

class GenresFilter(values: List<Genre> = genres) :
    Filter.Group<Genre>("Genres", values)

class Theme(val id: Int, name: String) : Filter.CheckBox(name)

private val themes: List<Theme>
    get() = listOf(
        Theme(1, "Office Workers"),
        Theme(2, "Family"),
        Theme(3, "Supernatural"),
        Theme(4, "Demons"),
        Theme(5, "Magic"),
        Theme(6, "Aliens"),
        Theme(7, "Suggestive"),
        Theme(8, "Doujinshi"),
        Theme(9, "School Life"),
        Theme(10, "Police"),
    )

class ThemesFilter(values: List<Theme> = themes) :
    Filter.Group<Theme>("Themes", values)

private val demographics: Array<String>
    get() = arrayOf("Any", "Shounen", "Shoujo", "Seinen")

class DemographicsFilter(values: Array<String> = demographics) :
    Filter.Select<String>("Demographic", values)

private val statuses: Array<String>
    get() = arrayOf("Any", "Ongoing", "Finished", "Axed/Dropped")

class StatusFilter(values: Array<String> = statuses) :
    Filter.Select<String>("Status", values)

inline fun <reified T> List<Filter<*>>.find() = find { it is T } as? T
