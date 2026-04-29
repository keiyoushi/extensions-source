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

class OriginalLanguage(name: String, val id: String = name) : Filter.CheckBox(name)
class OriginalLanguageFilter(originalLanguage: List<OriginalLanguage>) : Filter.Group<OriginalLanguage>("Original language", originalLanguage)
fun getOriginalLanguage() = listOf(
    OriginalLanguage("Japanese (Manga)", "Manga"),
    OriginalLanguage("Chinese (Manhua)", "Manhua"),
    OriginalLanguage("Korean (Manhwa)", "Manhwa"),
)

class Format(name: String, val id: String = name) : Filter.CheckBox(name)
class FormatFilter(formatList: List<Format>) : Filter.Group<Format>("Format", formatList)
fun getFormat() = listOf(
    Format("Black & White", "0"),
    Format("Full Color", "1"),
)

class Demographic(name: String, val id: String = name) : Filter.CheckBox(name)
class DemographicFilter(demographicList: List<Demographic>) : Filter.Group<Demographic>("Publication Demographic", demographicList)
fun getDemographic() = listOf(
    Demographic("Josei", "josei"),
    Demographic("Seinen", "seinen"),
    Demographic("Shoujo", "shoujo"),
    Demographic("Shounen", "shounen"),
)

class Status(name: String, val id: String = name) : Filter.CheckBox(name)
class StatusFilter(statusList: List<Status>) : Filter.Group<Status>("Status", statusList)
fun getStatus() = listOf(
    Status("Ongoing", "Ongoing"),
    Status("Completed", "Completed"),
)

class ContentRating(name: String, val id: String = name) : Filter.CheckBox(name)
class ContentRatingFilter(contentRating: List<ContentRating>) : Filter.Group<ContentRating>("Content Rating", contentRating)
fun getContentRating() = listOf(
    ContentRating("Ecchi", "ecchi"),
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
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Crime", "crime"),
    Genre("Drama", "drama"),
    Genre("Fantasy", "fantasy"),
    Genre("Girls Love", "girls-love"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Magical Girls", "magical-girls"),
    Genre("Mecha", "mecha"),
    Genre("Medical", "medical"),
    Genre("Philosophical", "philosophical"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Sports", "sports"),
    Genre("Superhero", "superhero"),
    Genre("Thriller", "thriller"),
    Genre("Tragedy", "tragedy"),
    Genre("Wuxia", "wuxia"),
    Genre("Yuri", "yuri"),
)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
