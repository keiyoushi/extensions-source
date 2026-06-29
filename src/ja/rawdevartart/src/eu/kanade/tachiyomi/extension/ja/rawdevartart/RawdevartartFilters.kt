package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val query: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, vals[state].second)
    }
}

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class SortFilter(state: Int = 1) :
    UriPartFilter(
        "Sort by",
        "sort",
        arrayOf(
            "Recently updated" to "",
            "Most viewed" to "most_viewed",
            "Most viewed today" to "most_viewed_today",
        ),
        state,
    )

data class Genre(val name: String, val path: String) {
    override fun toString() = name
}

class GenreFilter(genres: Array<Genre>) : Filter.Select<Genre>("Genre", genres)

/**
 * https://rawdevart.art/genre/all
 copy(
 [...document.querySelectorAll(".genre-list a.__link")]
 .map((e) => {
 const title = e.getAttribute("title");
 const capitalized = title
 .split(" ")
 .map((w) => w[0].toUpperCase() + w.slice(1).toLowerCase())
 .join(" ");
 const id = e.id || e.pathname.split("/")[2].replace(/^ne/, "");

 return `Genre("${capitalized}", "${id}"),`;
 })
 .join("\n"),
 );
 */
val genres = arrayOf(
    Genre("All", "all"),
    Genre("Action", "85"),
    Genre("Adaptions", "163"),
    Genre("Adult", "139"),
    Genre("Adventure", "86"),
    Genre("Alternative World", "149"),
    Genre("Animals", "168"),
    Genre("Animated", "140"),
    Genre("Comedy", "87"),
    Genre("Cooking", "134"),
    Genre("Crime", "165"),
    Genre("Drama", "114"),
    Genre("Ecchi", "88"),
    Genre("Elves", "150"),
    Genre("Fantasy", "89"),
    Genre("Food", "152"),
    Genre("Game", "155"),
    Genre("Gender Bender", "111"),
    Genre("Girls' Love", "167"),
    Genre("Harem", "90"),
    Genre("Hentai", "169"),
    Genre("Historical", "115"),
    Genre("Horror", "127"),
    Genre("Isekai", "144"),
    Genre("Josei", "130"),
    Genre("Loli", "91"),
    Genre("Lolicon", "148"),
    Genre("Magic", "151"),
    Genre("Manhua", "128"),
    Genre("Manhwa", "125"),
    Genre("Martial Arts", "126"),
    Genre("Mature", "112"),
    Genre("Mecha", "143"),
    Genre("Medical", "132"),
    Genre("Moe", "141"),
    Genre("Mystery", "121"),
    Genre("N/a", "156"),
    Genre("One Shot", "142"),
    Genre("Oneshot", "157"),
    Genre("Philosophical", "170"),
    Genre("Police", "166"),
    Genre("Psychological", "119"),
    Genre("Romance", "106"),
    Genre("School Life", "108"),
    Genre("Sci Fi", "122"),
    Genre("Sci-fi", "146"),
    Genre("Seinen", "107"),
    Genre("Shotacon", "154"),
    Genre("Shoujo", "120"),
    Genre("Shoujo Ai", "131"),
    Genre("Shounen", "118"),
    Genre("Shounen Ai", "109"),
    Genre("Slice Of Life", "92"),
    Genre("Smut", "123"),
    Genre("Sports", "124"),
    Genre("Supernatural", "93"),
    Genre("Thriller", "164"),
    Genre("Tragedy", "135"),
    Genre("Trap (crossdressing)", "138"),
    Genre("Updating", "147"),
    Genre("War", "153"),
    Genre("Webtoons", "116"),
    Genre("Yaoi", "161"),
    Genre("Yuri", "110"),
)
