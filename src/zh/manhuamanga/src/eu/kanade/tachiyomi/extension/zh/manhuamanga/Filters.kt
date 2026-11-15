package eu.kanade.tachiyomi.extension.zh.manhuamanga

import eu.kanade.tachiyomi.multisrc.mccms.GenreFilter
import eu.kanade.tachiyomi.multisrc.mccms.Intl
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSFilter
import eu.kanade.tachiyomi.multisrc.mccms.WebStatusFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// The site has no score order
class SortFilter : MCCMSFilter(Intl.sort, arrayOf(Intl.popular, Intl.latest), arrayOf("order/hits", "order/addtime"))

fun getFilters(genres: List<Pair<String, String>>?): FilterList {
    val filters = mutableListOf<Filter<*>>(Filter.Header(Intl.categoryWeb), WebStatusFilter(), SortFilter())
    if (!genres.isNullOrEmpty()) filters += GenreFilter(genres).webFilter
    return FilterList(filters)
}
