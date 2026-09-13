package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {

    fun toUriPart() = vals[state].second
}

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class Genre(
    name: String,
    val slug: String,
) : Filter.CheckBox(name)

class GenreFilter(
    genres: List<Genre>,
) : Filter.Group<Genre>("Genres", genres)

fun getGenreList() = listOf(
    Genre("Action", "action"),
    Genre("Adaptation", "adaptation"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Demons", "demons"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Gender Bender", "genderbender"),
    Genre("Gore", "gore"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Magic", "magic"),
    Genre("Martial Arts", "martialarts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Military", "military"),
    Genre("Monsters", "monsters"),
    Genre("Mystery", "mystery"),
    Genre("Post-Apocalyptic", "post-apocalyptic"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("School Life", "schoollife"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujoai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounenai"),
    Genre("Slice of Life", "sliceoflife"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("Thriller", "thriller"),
    Genre("Tragedy", "tragedy"),
    Genre("Video Games", "video-games"),
    Genre("Webtoons", "webtoons"),
    Genre("Wuxia", "wuxia"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)

fun genreLabel(slug: String): String = getGenreList().firstOrNull { it.slug.equals(slug, ignoreCase = true) }?.name
    ?: slug.replaceFirstChar { it.uppercase() }

fun findGenre(query: String): Genre? {
    val normalized = query.replace("-", "").replace(" ", "")
    return getGenreList().firstOrNull { genre ->
        genre.name.equals(query, ignoreCase = true) ||
            genre.slug.equals(query, ignoreCase = true) ||
            genre.name.replace("-", "").replace(" ", "")
                .equals(normalized, ignoreCase = true)
    }
}
