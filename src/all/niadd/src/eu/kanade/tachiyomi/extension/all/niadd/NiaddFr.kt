package eu.kanade.tachiyomi.extension.fr.niadd

import eu.kanade.tachiyomi.extension.all.niadd.Niadd
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class NiaddEn : Niadd(
    name = "Niadd",
    baseUrl = "https://www.fr.niadd.com",
    langCode = "Fr",
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/?name=$query&page=$page"
        return GET(url, headers)
    }
}
