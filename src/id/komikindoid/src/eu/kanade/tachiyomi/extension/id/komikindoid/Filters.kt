package eu.kanade.tachiyomi.extension.id.komikindoid

import eu.kanade.tachiyomi.source.model.Filter

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
        ),
    )

class OriginalLanguageFilter :
    UriPartFilter(
        "Original language",
        arrayOf(
            Pair("All", ""),
            Pair("Japanese (Manga)", "Manga"),
            Pair("Chinese (Manhua)", "Manhua"),
            Pair("Korean (Manhwa)", "Manhwa"),
        ),
    )

class FormatFilter :
    UriPartFilter(
        "Format",
        arrayOf(
            Pair("All", ""),
            Pair("Black & White", "bw"),
            Pair("Full Color", "colorized"),
        ),
    )

class Demographic(name: String, val id: String = name) : Filter.CheckBox(name)
class DemographicFilter(demographicList: List<Demographic>) : Filter.Group<Demographic>("Publication Demographic", demographicList)
fun getDemographic() = listOf(
    Demographic("Josei", "josei"),
    Demographic("Seinen", "seinen"),
    Demographic("Shoujo", "shoujo"),
    Demographic("Shounen", "shounen"),
)

class ContentRating(name: String, val id: String = name) : Filter.CheckBox(name)
class ContentRatingFilter(contentRating: List<ContentRating>) : Filter.Group<ContentRating>("Content Rating", contentRating)
fun getContentRating() = listOf(
    ContentRating("Ecchi", "ecchi"),
    ContentRating("Erotica", "erotica"),
    ContentRating("Gore", "gore"),
    ContentRating("Sexual Violence", "sexual-violence"),
    ContentRating("Smut", "smut"),
)

class Theme(name: String, val id: String = name) : Filter.CheckBox(name)
class ThemeFilter(themeList: List<Theme>) : Filter.Group<Theme>("Story Theme", themeList)
fun getTheme() = listOf(
    Theme("Alien", "aliens"),
    Theme("Animal", "animals"),
    Theme("Cooking", "cooking"),
    Theme("Crossdressing", "crossdressing"),
    Theme("Delinquent", "delinquents"),
    Theme("Demon", "demons"),
    Theme("Ecchi", "ecchi"),
    Theme("Gal", "gyaru"),
    Theme("Genderswap", "genderswap"),
    Theme("Ghost", "ghosts"),
    Theme("Gore", "gore"),
    Theme("Harem", "harem"),
    Theme("Incest", "incest"),
    Theme("Loli", "loli"),
    Theme("Mafia", "mafia"),
    Theme("Magic", "magic"),
    Theme("Martial Arts", "martial-arts"),
    Theme("Military", "military"),
    Theme("Monster Girls", "monster-girls"),
    Theme("Monsters", "monsters"),
    Theme("Music", "music"),
    Theme("Ninja", "ninja"),
    Theme("Office Workers", "office-workers"),
    Theme("Police", "police"),
    Theme("Post-Apocalyptic", "post-apocalyptic"),
    Theme("Reincarnation", "reincarnation"),
    Theme("Reverse Harem", "reverse-harem"),
    Theme("Samurai", "samurai"),
    Theme("School Life", "school-life"),
    Theme("Shota", "shota"),
    Theme("Smut", "smut"),
    Theme("Supernatural", "supernatural"),
    Theme("Survival", "survival"),
    Theme("Time Travel", "time-travel"),
    Theme("Traditional Games", "traditional-games"),
    Theme("Vampires", "vampires"),
    Theme("Video Games", "video-games"),
    Theme("Villainess", "villainess"),
    Theme("Virtual Reality", "virtual-reality"),
    Theme("Zombies", "zombies"),
)

class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
class GenreFilter(genreList: List<Genre>) : Filter.Group<Genre>("Genre", genreList)
fun getGenre() = listOf(
    Genre("Action", "action"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Arts", "arts"),
    Genre("Boys' Love", "boys-love"),
    Genre("Comedy", "comedy"),
    Genre("Crime", "crime"),
    Genre("Demons", "demons"),
    Genre("Drama", "drama"),
    Genre("Drama Supernatural", "drama-supernatural"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Game", "game"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Girls' Love", "girls-love"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Life", "life"),
    Genre("Magic", "magic"),
    Genre("Magical Girls", "magical-girls"),
    Genre("Martial", "martial"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Medical", "medical"),
    Genre("Music", "music"),
    Genre("Mystery", "mystery"),
    Genre("Philosophical", "philosophical"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Romance", "romance"),
    Genre("School", "school"),
    Genre("School Life", "school-life"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shotacon", "shotacon"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Superhero", "superhero"),
    Genre("Supernatural", "supernatural"),
    Genre("Thriller", "thriller"),
    Genre("Traged", "traged"),
    Genre("Tragedy", "tragedy"),
    Genre("Wuxia", "wuxia"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
