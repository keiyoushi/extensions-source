package eu.kanade.tachiyomi.extension.en.hentaiyoga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(TypeFilter())

// The site's tabs: "All" and "Manga" live under /manga, "Manhwa" under /manhwa
class TypeFilter : Filter.Select<String>("Type", arrayOf("All", "Manhwa", "Manga")) {
    val path get() = if (state == 1) "manhwa" else "manga"
    val type get() = "manga".takeIf { state == 2 }
}
