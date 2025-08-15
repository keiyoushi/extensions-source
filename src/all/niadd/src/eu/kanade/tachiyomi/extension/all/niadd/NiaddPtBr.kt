package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.extension.all.niadd.Niadd
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import java.net.URLEncoder

class NiaddPtBr : Niadd(
    name = "Niadd",
    baseUrl = "https://www.br.niadd.com",
    langCode = "pt-BR",
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search/?name=$q&page=$page"
        return GET(url, headers)
    }
}
