package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class ColorFilter :
    UriPartFilter(
        "Color",
        arrayOf(
            "All" to "",
            "Full Color" to "Full Color",
            "B&W" to "B&W",
        ),
    )

class ReadingFilter :
    UriPartFilter(
        "Reading",
        arrayOf(
            "All" to "",
            "Vertical Scroll" to "Vertical Scroll",
            "Page" to "Page",
        ),
    )

class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Latest", "Popular", "Project"),
        Selection(0, ascending = false),
    ) {
    fun toSortPart() = when (state?.index) {
        0 -> "latest"
        1 -> "popular"
        2 -> "project"
        else -> "latest"
    }

    fun toOrderPart() = if (state?.ascending == true) "asc" else "desc"
}

class ProjectOnlyFilter : Filter.CheckBox("Project Only", false)

class AuthorFilter : Filter.Text("Author")
class ArtistFilter : Filter.Text("Artist")
class PublisherFilter : Filter.Text("Publisher")

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreGroup(genres: List<Pair<String, String>>) : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.first, it.second) })

val genreList = listOf(
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Crime" to "crime",
    "Crossdressing" to "crossdressing",
    "Cultivation" to "cultivation",
    "Demon" to "demon",
    "Demons" to "demons",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantas" to "fantas",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Girls Love" to "girls-love",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Magic" to "magic",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Medical" to "medical",
    "Military" to "military",
    "Mystery" to "mystery",
    "One Shot" to "one-shot",
    "Project" to "project",
    "Regression" to "regression",
    "Reincarnation" to "reincarnation",
    "Reverse Harem" to "reverse-harem",
    "Romance" to "romance",
    "School" to "school",
    "School Life" to "school-life",
    "Sci Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shoujo Ai" to "shoujo-ai",
    "Shounen" to "shounen",
    "Slice Of Life" to "slice-of-life",
    "Smut" to "smut",
    "Supernatural" to "supernatural",
    "Survival" to "survival",
    "Thriller" to "thriller",
    "Webtoon" to "webtoon",
    "Webtoons" to "webtoons",
    "Wuxia" to "wuxia",
    "Xianxia" to "xianxia",
)
