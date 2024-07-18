package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class InfernalVoidScans : MangaThemesia(
    "Infernal Void Scans",
    "https://hivetoon.com",
    "en",
) {
    override val pageSelector = "div#readerarea > p > img"

    override val hasProjectPage = true

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
            .removeAllQueryParameters("title")

        // Filters are not loaded with the ‘s’ parameter. Fix genres filter
        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        }
        return GET(url.build(), headers)
    }
}
