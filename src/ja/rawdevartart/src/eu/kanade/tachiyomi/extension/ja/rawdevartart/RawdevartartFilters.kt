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
) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, vals[state].second)
    }
}

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        "All" to "",
        "Ongoing" to "ongoing",
        "Completed" to "completed",
    ),
)

class SortFilter(state: Int = 1) : UriPartFilter(
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

// copy([...$0.querySelectorAll("option")].filter((e) => e.value !== "/all").map((e) => `Genre("${e.textContent}", "${e.value.split("/").slice(1, 3).join("/")}"),`).join("\n"))
val genres = arrayOf(
    Genre("All", "genres"),
    Genre("action", "genre/85"),
    Genre("adult", "genre/139"),
    Genre("adventure", "genre/86"),
    Genre("Alternative World", "genre/149"),
    Genre("animated", "genre/140"),
    Genre("comedy", "genre/87"),
    Genre("cooking", "genre/134"),
    Genre("drama", "genre/114"),
    Genre("ecchi", "genre/88"),
    Genre("Elves", "genre/150"),
    Genre("fantasy", "genre/89"),
    Genre("Food", "genre/152"),
    Genre("Game", "genre/155"),
    Genre("gender bender", "genre/111"),
    Genre("harem", "genre/90"),
    Genre("historical", "genre/115"),
    Genre("horror", "genre/127"),
    Genre("Isekai", "genre/144"),
    Genre("josei", "genre/130"),
    Genre("loli", "genre/91"),
    Genre("Lolicon", "genre/148"),
    Genre("Magic", "genre/151"),
    Genre("manhua", "genre/128"),
    Genre("manhwa", "genre/125"),
    Genre("martial arts", "genre/126"),
    Genre("mature", "genre/112"),
    Genre("mecha", "genre/143"),
    Genre("medical", "genre/132"),
    Genre("moe", "genre/141"),
    Genre("mystery", "genre/121"),
    Genre("N/A", "genre/156"),
    Genre("one shot", "genre/142"),
    Genre("Oneshot", "genre/157"),
    Genre("psychological", "genre/119"),
    Genre("romance", "genre/106"),
    Genre("school life", "genre/108"),
    Genre("sci fi", "genre/122"),
    Genre("Sci-fi", "genre/146"),
    Genre("seinen", "genre/107"),
    Genre("Shotacon", "genre/154"),
    Genre("shoujo", "genre/120"),
    Genre("shoujo ai", "genre/131"),
    Genre("shounen", "genre/118"),
    Genre("shounen ai", "genre/109"),
    Genre("slice of life", "genre/92"),
    Genre("smut", "genre/123"),
    Genre("sports", "genre/124"),
    Genre("supernatural", "genre/93"),
    Genre("tragedy", "genre/135"),
    Genre("trap (crossdressing)", "genre/138"),
    Genre("Updating", "genre/147"),
    Genre("War", "genre/153"),
    Genre("webtoons", "genre/116"),
    Genre("Yaoi", "genre/161"),
    Genre("yuri", "genre/110"),
)
