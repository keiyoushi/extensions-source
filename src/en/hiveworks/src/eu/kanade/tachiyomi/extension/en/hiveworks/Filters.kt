package eu.kanade.tachiyomi.extension.en.hiveworks

import android.net.Uri
import eu.kanade.tachiyomi.source.model.Filter

internal class OriginalsFilter : Filter.CheckBox("Original Comics")
internal class KidsFilter : Filter.CheckBox("Kids Comics")
internal class CompletedFilter : Filter.CheckBox("Completed Comics")
internal class HiatusFilter : Filter.CheckBox("On Hiatus Comics")

internal interface UriFilter {
    fun addToUri(uri: Uri.Builder)
}

internal open class UriSelectFilter(
    displayName: String,
    val uriParam: String,
    val vals: Array<Pair<String, String>>,
    val firstIsUnspecified: Boolean = true,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
    UriFilter {
    override fun addToUri(uri: Uri.Builder) {
        if (state != 0 || !firstIsUnspecified) {
            uri.appendPath(uriParam)
                .appendPath(vals[state].first)
        }
    }
}

internal class UpdateDay :
    UriSelectFilter(
        "Update Day",
        "update-day",
        arrayOf(
            Pair("all", "All"),
            Pair("monday", "Monday"),
            Pair("tuesday", "Tuesday"),
            Pair("wednesday", "Wednesday"),
            Pair("thursday", "Thursday"),
            Pair("friday", "Friday"),
            Pair("saturday", "Saturday"),
            Pair("sunday", "Sunday"),
        ),
    )

internal class RatingFilter :
    UriSelectFilter(
        "Rating",
        "age",
        arrayOf(
            Pair("all", "All"),
            Pair("everyone", "Everyone"),
            Pair("teen", "Teen"),
            Pair("young-adult", "Young Adult"),
            Pair("mature", "Mature"),
        ),
    )

internal class GenreFilter :
    UriSelectFilter(
        "Genre",
        "genre",
        arrayOf(
            Pair("all", "All"),
            Pair("action/adventure", "Action/Adventure"),
            Pair("animated", "Animated"),
            Pair("autobio", "Autobio"),
            Pair("comedy", "Comedy"),
            Pair("drama", "Drama"),
            Pair("dystopian", "Dystopian"),
            Pair("fairytale", "Fairytale"),
            Pair("fantasy", "Fantasy"),
            Pair("finished", "Finished"),
            Pair("historical-fiction", "Historical Fiction"),
            Pair("horror", "Horror"),
            Pair("lgbt", "LGBT"),
            Pair("mystery", "Mystery"),
            Pair("romance", "Romance"),
            Pair("sci-fi", "Science Fiction"),
            Pair("slice-of-life", "Slice of Life"),
            Pair("steampunk", "Steampunk"),
            Pair("superhero", "Superhero"),
            Pair("urban-fantasy", "Urban Fantasy"),
        ),
    )

internal class TitleFilter :
    UriSelectFilter(
        "Title",
        "alpha",
        arrayOf(
            Pair("all", "All"),
            Pair("a", "A"),
            Pair("b", "B"),
            Pair("c", "C"),
            Pair("d", "D"),
            Pair("e", "E"),
            Pair("f", "F"),
            Pair("g", "G"),
            Pair("h", "H"),
            Pair("i", "I"),
            Pair("j", "J"),
            Pair("k", "K"),
            Pair("l", "L"),
            Pair("m", "M"),
            Pair("n", "N"),
            Pair("o", "O"),
            Pair("p", "P"),
            Pair("q", "Q"),
            Pair("r", "R"),
            Pair("s", "S"),
            Pair("t", "T"),
            Pair("u", "U"),
            Pair("v", "V"),
            Pair("w", "W"),
            Pair("x", "X"),
            Pair("y", "Y"),
            Pair("z", "Z"),
            Pair("numbers-symbols", "Numbers / Symbols"),
        ),
    )

internal class SortFilter :
    UriSelectFilter(
        "Sort By",
        "sortby",
        arrayOf(
            Pair("none", "None"),
            Pair("a-z", "A-Z"),
            Pair("z-a", "Z-A"),
        ),
    )
