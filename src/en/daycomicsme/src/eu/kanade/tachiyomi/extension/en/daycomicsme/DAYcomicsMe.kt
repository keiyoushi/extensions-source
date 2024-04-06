package eu.kanade.tachiyomi.extension.en.daycomicsme

import eu.kanade.tachiyomi.multisrc.hotcomics.HotComics

class DAYcomicsMe : HotComics(
    "DAYcomics.me",
    "en",
    "https://daycomics.me",
) {
    override val browseList = listOf(
        Pair("Home", "en"),
        Pair("Weekly", "en/weekly"),
        Pair("New", "en/new"),
        Pair("Genre: All", "en/genres"),
        Pair("Genre: Romance", "en/genres/Romance"),
        Pair("Genre: Office", "en/genres/Office"),
        Pair("Genre: College", "en/genres/College"),
        Pair("Genre: Drama", "en/genres/Drama"),
        Pair("Genre: Isekai", "en/genres/Isekai"),
        Pair("Genre: UNCENSORED", "en/genres/UNCENSORED"),
        Pair("Genre: Action", "en/genres/Action"),
        Pair("Genre: BL", "en/genres/BL"),
        Pair("Genre: New", "en/genres/New"),
        Pair("Genre: Slice of Life", "en/genres/Slice_of_Life"),
        Pair("Genre: Supernatural", "en/genres/Supernatural"),
        Pair("Genre: Historical", "en/genres/Historical"),
        Pair("Genre: School Life", "en/genres/School_Life"),
        Pair("Genre: Horror Thriller", "en/genres/Horror_Thriller"),
    )
}
