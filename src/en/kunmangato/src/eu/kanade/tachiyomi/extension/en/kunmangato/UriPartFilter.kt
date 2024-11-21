package eu.kanade.tachiyomi.extension.en.kunmangato

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    private val filter: KunMangaToFilter,
    private val vals: List<OptionValueOptionNamePair>,
) : Filter.Select<String>(filter.name, vals.map { it.second }.toTypedArray()) {
    fun toQueryParam() = filter.queryParam

    fun toUriPart() = vals[state].first
}
