package eu.kanade.tachiyomi.extension.all.networkshare

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Sort by",
        arrayOf("Title", "Date modified"),
        Selection(0, true),
    ) {
    val byDate get() = state?.index == 1

    val ascending get() = state?.ascending != false
}
