package eu.kanade.tachiyomi.extension.ru.hentailib

import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class HentaiLib : LibGroup() {

    override val siteId: Int = 4 // Important in api calls

    // Filters
    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().toMutableList()
        if (filters.size > 7) {
            filters.removeAt(7) // AgeList
        }
        return FilterList(filters)
    }
}
