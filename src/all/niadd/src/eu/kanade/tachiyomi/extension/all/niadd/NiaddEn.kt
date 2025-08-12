package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.extension.all.niadd.Niadd
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class NiaddEn : Niadd(
    name = "Niadd (English)",
    baseUrl = "https://www.niadd.com",
    langCode = "en"
) {
    // Override do searchMangaRequest para garantir assinatura correta
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/?name=$query&page=$page"
        return GET(url, headers)
    }
}
