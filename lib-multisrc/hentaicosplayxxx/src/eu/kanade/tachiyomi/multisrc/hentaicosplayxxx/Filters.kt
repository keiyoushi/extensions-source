package eu.kanade.tachiyomi.multisrc.hentaicosplayxxx

import eu.kanade.tachiyomi.source.model.Filter

internal abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

internal class TagFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, options)
