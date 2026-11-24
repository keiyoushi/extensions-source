package eu.kanade.tachiyomi.extension.en.babesrater

import eu.kanade.tachiyomi.source.model.Filter

class ListFilter : Filter.Select<String>("List", arrayOf("Hottest babes", "Hottest pictures") + ('A'..'Z').map(Char::toString)) {
    val hottestPictures get() = state == 1

    val letter get() = if (state < 2) null else values[state].lowercase()
}
