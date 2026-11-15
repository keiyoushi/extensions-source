package eu.kanade.tachiyomi.extension.pt.hentaidatia

import eu.kanade.tachiyomi.source.model.Filter

open class TaxonomyFilter(name: String, private val options: List<TaxonomyDto>) : Filter.Select<String>(name, arrayOf("Todas") + options.map { it.name }) {
    val selected get() = options.getOrNull(state - 1)?.slug
}

class TagFilter(options: List<TaxonomyDto>) : TaxonomyFilter("Tag", options)

class ParodyFilter(options: List<TaxonomyDto>) : TaxonomyFilter("Paródia", options)
