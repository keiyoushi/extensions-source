package eu.kanade.tachiyomi.extension.en.mangahome

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Genre(val name: String, val value: String)

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class ListFilter :
    SelectFilter(
        "List",
        arrayOf("All" to "directory", "Ongoing" to "ongoing", "New" to "new", "Completed" to "completed"),
    )

class SortFilter :
    SelectFilter(
        "Sort",
        arrayOf("Views" to "", "A-Z" to "name.az", "Top rated" to "rating.za", "Update time" to "last_chapter_time.za"),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf("Any" to "", "Japanese Manga" to "manga", "Korean Manhwa" to "manhwa", "Chinese Manhua" to "manhua"),
    )

class AuthorFilter : Filter.Text("Author")

class ArtistFilter : Filter.Text("Artist")

class GenreOption(name: String, val value: String) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<GenreOption>("Genres", genres.map { GenreOption(it.name, it.value) })

class YearMethodFilter : SelectFilter("Released", arrayOf("In" to "eq", "Before" to "lt", "After" to "gt"))

class YearFilter : Filter.Text("Year")

class RatingMethodFilter : SelectFilter("Rating", arrayOf("Is" to "eq", "Less than" to "lt", "More than" to "gt"))

class RatingFilter :
    SelectFilter(
        "Stars",
        arrayOf("Any" to "", "No star" to "0", "1 star" to "1", "2 stars" to "2", "3 stars" to "3", "4 stars" to "4", "5 stars" to "5"),
    )

class CompletedFilter : SelectFilter("Completed series", arrayOf("Either" to "", "Yes" to "1", "No" to "0"))
