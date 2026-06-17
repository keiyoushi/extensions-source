package eu.kanade.tachiyomi.extension.en.myadultcomics

import eu.kanade.tachiyomi.source.model.Filter

class Filters :
    Filter.Select<String>(
        "Search Type",
        arrayOf("Title", "Parody", "Character", "Tag", "Artist"),
    ) {
    fun selectedValue(): String = when (state) {
        0 -> "title"
        1 -> "1"
        2 -> "2"
        3 -> "3"
        4 -> "4"
        else -> "title"
    }
}
