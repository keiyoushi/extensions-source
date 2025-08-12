package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

// Importa NiaddBaseLang de onde estiver declarado
// Se estiver no mesmo pacote, já tá ok

class NiaddEn : NiaddBaseLang(
    name = "Niadd (English)",
    baseUrl = "https://www.niadd.com",
    lang = "en",
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/?keyword=$query&page=$page"
        return GET(url, headers)
    }
}
