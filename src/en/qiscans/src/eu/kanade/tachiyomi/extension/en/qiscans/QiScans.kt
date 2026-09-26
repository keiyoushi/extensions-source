package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwa
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaSortFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaStatusFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaTypeFilter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class QiScans : EZManhwa() {

    override val apiUrl = "https://api.qimanga.com/api/v1"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = addEZManhwaHeaders()
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-site")

    // QiScans search endpoint ignores filters — only send the query when searching.
    override fun searchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val isSearch = query.isNotBlank()
        val endpoint = if (isSearch) "$apiUrl/series/search" else "$apiUrl/series"
        return endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            if (isSearch) {
                addQueryParameter("q", query)
            } else {
                var sortAdded = false
                for (filter in filters) {
                    when (filter) {
                        is EZManhwaSortFilter -> {
                            addQueryParameter("sort", filter.value)
                            sortAdded = true
                        }
                        is EZManhwaStatusFilter -> if (filter.value.isNotBlank()) addQueryParameter("status", filter.value)
                        is EZManhwaTypeFilter -> if (filter.value.isNotBlank()) addQueryParameter("type", filter.value)
                        is QiScansGenreFilter -> if (filter.value.isNotBlank()) addQueryParameter("genre", filter.value)
                        else -> {}
                    }
                }
                if (!sortAdded) addQueryParameter("sort", "latest")
            }
        }.build()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        EZManhwaSortFilter(),
        EZManhwaStatusFilter(),
        EZManhwaTypeFilter(),
        QiScansGenreFilter(),
    )
}
