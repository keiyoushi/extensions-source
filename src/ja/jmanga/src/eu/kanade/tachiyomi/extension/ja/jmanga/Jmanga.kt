package eu.kanade.tachiyomi.extension.ja.jmanga

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Jmanga : MangaReader("Jmanga", "https://jmanga.codes", "ja") {

    private val baseDefaultUrl = "https://jmanga.codes"
    override var baseUrl = baseDefaultUrl

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            } else {
                addPathSegment("filter")
                val filterList = filters.ifEmpty { getFilterList() }
                filterList.filterIsInstance<MangaReader.UriFilter>().forEach {
                    it.addToUri(this)
                }
            }
            addPage(page, this)
        }.build()

        return GET(url, headers)
    }
}
