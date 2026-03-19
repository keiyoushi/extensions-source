package eu.kanade.tachiyomi.extension.en.mangadotnet

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        name = "Sort",
        values = sortOrders.map { it.first }.toTypedArray(),
        state = Selection(0, false),
    ) {
    val sort get() = sortOrders[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false
}

private val sortOrders = listOf(
    "Relevance" to "relevance",
    "Alphabetical" to "Alphabetical",
    "Latest Update" to "latest",
    "Total Chapters" to "chapters",
    "Most Viewed" to "views",
    "Most Bookmarked" to "bookmarks",
    "Top Rated" to "rating",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = status.map { it.first }.toTypedArray(),
    ) {
    val selected get() = status[state].second
}

private val status = listOf(
    "Any Status" to null,
    "Ongoing" to "Ongoing",
    "Completed" to "Completed",
)

class TypeFilter :
    Filter.Select<String>(
        name = "Type",
        values = types.map { it.first }.toTypedArray(),
    ) {
    val selected get() = types[state].second
}

private val types = listOf(
    "All" to null,
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
    "One Shot" to "ONESHOT",
)

class CheckBoxFilter(name: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<CheckBoxFilter>(
        name = "Genre",
        state = genres.map { CheckBoxFilter(it) },
    ) {
    val checked get() = state.filter { it.state }.map { it.name }
}

val genres = listOf(
    "Action",
    "Adult",
    "Adventure",
    "Aliens",
    "Animals",
    "Avant Garde",
    "Award Winning",
    "Boys Love",
    "Boys' Love",
    "Comedy",
    "Cooking",
    "Crime",
    "Crossdressing",
    "Delinquents",
    "Demons",
    "Doujinshi",
    "Drama",
    "Ecchi",
    "Erotica",
    "Fantasy",
    "Gender Bender",
    "Genderswap",
    "Ghosts",
    "Girls Love",
    "Girls' Love",
    "Gourmet",
    "Gyaru",
    "Harem",
    "Hentai",
    "Historical",
    "Horror",
    "Incest",
    "Isekai",
    "Josei",
    "Loli",
    "Lolicon",
    "Mafia",
    "Magic",
    "Magical Girls",
    "Mahjong",
    "Mahou Shoujo",
    "Martial Arts",
    "Mature",
    "Mecha",
    "Medical",
    "Military",
    "Monster Girls",
    "Monsters",
    "Music",
    "Mystery",
    "Ninja",
    "Office Workers",
    "One Shot",
    "Philosophical",
    "Police",
    "Post-Apocalyptic",
    "Psychological",
    "Reincarnation",
    "Reverse Harem",
    "Romance",
    "Samurai",
    "School Life",
    "Sci-Fi",
    "Seinen",
    "Shota",
    "Shotacon",
    "Shoujo",
    "Shoujo Ai",
    "Shounen",
    "Shounen Ai",
    "Slice of Life",
    "Smut",
    "Sports",
    "Superhero",
    "Supernatural",
    "Survival",
    "Suspense",
    "Thriller",
    "Time Travel",
    "Traditional Games",
    "Tragedy",
    "Vampires",
    "Video Games",
    "Villainess",
    "Virtual Reality",
    "Wuxia",
    "Yaoi",
    "Yuri",
    "Zombies",
)
