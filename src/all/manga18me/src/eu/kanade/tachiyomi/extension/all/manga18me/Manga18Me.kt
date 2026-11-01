package eu.kanade.tachiyomi.extension.all.manga18me

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement

@Source
abstract class Manga18Me : YiiMadara() {
    override val rawPath = "manhwa-raw"

    // The site doesn't link its genre pages anywhere
    override val supportsFilterFetching = false

    override fun genres(data: JsonElement?) = listOf(
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "BL" to "bl",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Family" to "family",
        "Fantasy" to "fantasy",
        "Gender Bender" to "gender-bender",
        "GL" to "gl",
        "Harem" to "harem",
        "Hentai" to "hentai",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Magic" to "magic",
        "Manhua" to "manhua",
        "Manhwa" to "manhwa",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-Fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Thriller" to "thriller",
        "Tragedy" to "tragedy",
        "Uncensored" to "uncensored",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    ).map { (name, slug) -> name to "genre/$slug" }
}
