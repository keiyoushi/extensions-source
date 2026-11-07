package eu.kanade.tachiyomi.extension.en.babesrater

import eu.kanade.tachiyomi.source.model.Filter

class LetterFilter : Filter.Select<String>("Babe list", arrayOf("Any") + ('A'..'Z').map(Char::toString)) {
    val selected get() = if (state == 0) null else values[state].lowercase()
}
