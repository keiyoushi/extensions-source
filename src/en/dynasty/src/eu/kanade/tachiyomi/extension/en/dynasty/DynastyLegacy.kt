package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import rx.Observable

class DynastyLegacy(
    override val name: String,
    override val id: Long,
) : Dynasty() {
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        throw Exception(LEGACY_DYNASTY_ERROR)
    }
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        throw Exception(LEGACY_DYNASTY_ERROR)
    }
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        throw Exception(LEGACY_DYNASTY_ERROR)
    }
    override fun getFilterList(): FilterList {
        return FilterList()
    }
}

private const val LEGACY_DYNASTY_ERROR = "Use the `Dynasty Scans` source instead"
