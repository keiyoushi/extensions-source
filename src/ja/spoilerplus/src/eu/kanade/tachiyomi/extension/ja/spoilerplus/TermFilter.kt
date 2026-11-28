package eu.kanade.tachiyomi.extension.ja.spoilerplus

import eu.kanade.tachiyomi.source.model.Filter

class TermFilter(name: String, val path: String, private val terms: List<Pair<String, String>>) : Filter.Select<String>(name, (listOf("すべて") + terms.map { it.first }).toTypedArray()) {
    val selected get() = terms.getOrNull(state - 1)?.second
}
