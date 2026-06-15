package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

open class TextFilter(name: String) : Filter.Text(name)

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
            "Novel" to "NOVEL",
            "One-Shot" to "ONE-SHOT",
            "Doujinshi" to "DOUJINSHI",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Releasing" to "releasing",
        ),
    )

class MinChaptersFilter :
    UriPartFilter(
        "Min Chapters",
        arrayOf(
            "Any" to "",
            "10+" to "10",
            "50+" to "50",
            "100+" to "100",
            "200+" to "200",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            "Newest" to "created_at",
            "Most Viewed" to "view",
            "Release Date" to "release_date",
            "Top Rated (Likes)" to "like_count",
            "Name A-Z" to "title",
            "Top Rated (Score)" to "vote_average",
            "Fan Favorites" to "fan_favorites",
        ),
    ) {
    init {
        state = 1
    }
}

class GroupFilter : TextFilter("Group")
class ReleaseStartFilter : TextFilter("Release Start Date (YYYY-MM-DD)")
class ReleaseEndFilter : TextFilter("Release End Date (YYYY-MM-DD)")

class GenreTriState(name: String, val id: String) : Filter.TriState(name)
class GenreFilter(genres: List<GenreTriState>) : Filter.Group<GenreTriState>("Genres", genres)

val genreList = listOf(
    Pair("Action", "1"), Pair("Adaptation", "61"), Pair("Adult", "67"), Pair("Adventure", "6"),
    Pair("Aliens", "84"), Pair("Avant Garde", "43"), Pair("Award Winning", "78"), Pair("Boys Love", "31"),
    Pair("Comedy", "2"), Pair("Comics", "90"), Pair("Crazy MC", "59"), Pair("Crime", "98"),
    Pair("Demon", "57"), Pair("Demons", "5"), Pair("Doujinshi", "79"), Pair("Drama", "15"),
    Pair("Dungeons", "56"), Pair("Ecchi", "29"), Pair("Erotica", "68"), Pair("Fantasy", "7"),
    Pair("Full Color", "62"), Pair("Game", "46"), Pair("Gender Bender", "75"), Pair("Genderswap", "63"),
    Pair("Genius MC", "49"), Pair("Girls Love", "28"), Pair("Gore", "80"), Pair("Gourmet", "42"),
    Pair("Harem", "37"), Pair("Hentai", "76"), Pair("Historical", "66"), Pair("Horror", "16"),
    Pair("Isekai", "3"), Pair("Iyashikei", "34"), Pair("Josei", "35"), Pair("Kids", "38"),
    Pair("Lolicon", "70"), Pair("Long Strip", "64"), Pair("Magic", "8"), Pair("Magical Girls", "99"),
    Pair("Mahou Shoujo", "41"), Pair("Martial Arts", "11"), Pair("Mature", "45"), Pair("Mecha", "36"),
    Pair("Medical", "101"), Pair("Military", "17"), Pair("Monster Girls", "88"), Pair("Monsters", "81"),
    Pair("Murim", "47"), Pair("Music", "30"), Pair("Mystery", "19"), Pair("Necromancer", "54"),
    Pair("Overpowered", "55"), Pair("Parody", "12"), Pair("Philosophical", "100"), Pair("Post-Apocalyptic", "85"),
    Pair("Psychological", "18"), Pair("Regression", "52"), Pair("Reincarnation", "48"), Pair("Revenge", "51"),
    Pair("Reverse Harem", "44"), Pair("Romance", "20"), Pair("Samurai", "86"), Pair("School", "21"),
    Pair("School Life", "24"), Pair("Sci-Fi", "13"), Pair("Seinen", "14"), Pair("Self-Published", "82"),
    Pair("Shotacon", "77"), Pair("Shoujo", "27"), Pair("Shoujo Ai", "73"), Pair("Shounen", "4"),
    Pair("Shounen Ai", "72"), Pair("Slice of Life", "26"), Pair("Smut", "69"), Pair("Space", "22"),
    Pair("Sports", "32"), Pair("Super Power", "9"), Pair("Superhero", "89"), Pair("Supernatural", "10"),
    Pair("Survival", "87"), Pair("Suspense", "39"), Pair("System", "50"), Pair("Thriller", "40"),
    Pair("Time Travel", "23"), Pair("Tower", "58"), Pair("Tragedy", "25"), Pair("Vampire", "33"),
    Pair("Villain", "53"), Pair("Violence", "60"), Pair("Web Comic", "65"), Pair("Wuxia", "113"),
    Pair("Yaoi", "74"), Pair("Yuri", "71"),
)
