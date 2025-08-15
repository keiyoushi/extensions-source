package eu.kanade.tachiyomi.extension.es.niadd

import eu.kanade.tachiyomi.extension.all.niadd.Niadd
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class NiaddEs : Niadd(
    name = "Niadd",
    baseUrl = "https://www.es.niadd.com",
    langCode = "es",
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/?name=$query&page=$page"
        return GET(url, headers)
    }
}
