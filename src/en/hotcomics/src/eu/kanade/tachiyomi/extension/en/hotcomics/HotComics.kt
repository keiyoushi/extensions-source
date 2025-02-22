package eu.kanade.tachiyomi.extension.en.hotcomics

import eu.kanade.tachiyomi.multisrc.hotcomics.HotComics

class HotComics : HotComics(
    "HotComics",
    "en",
    "https://hotcomics.me",
) {
    override val browseList = listOf(
        Pair("Home", "en"),
        Pair("Weekly", "en/weekly"),
        Pair("New", "en/new"),
        Pair("Genre: All", "en/genres"),
        Pair("Genre: Sports", "en/genres/Sports"),
        Pair("Genre: Historical", "en/genres/Historical"),
        Pair("Genre: Drama", "en/genres/Drama"),
        Pair("Genre: BL", "en/genres/BL"),
        Pair("Genre: Thriller", "en/genres/Thriller"),
        Pair("Genre: School life", "en/genres/School_life"),
        Pair("Genre: Comedy", "en/genres/Comedy"),
        Pair("Genre: GL", "en/genres/GL"),
        Pair("Genre: Action", "en/genres/Action"),
        Pair("Genre: Sci-fi", "en/genres/Sci-fi"),
        Pair("Genre: Horror", "en/genres/Horror"),
        Pair("Genre: Fantasy", "en/genres/Fantasy"),
        Pair("Genre: Romance", "en/genres/Romance"),
    )
}
