package eu.kanade.tachiyomi.extension.pt.pizzariascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Pizzariascan : Madara(
    "Pizzaria Scan",
    "https://pizzariascan.site",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt")),
) {
    override val supportsLatest = false

    // It's used in search request
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint: Boolean = true

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/$mangaSubString/${searchPage(page)}", headers)

    override fun getFilterList() = FilterList()
}
