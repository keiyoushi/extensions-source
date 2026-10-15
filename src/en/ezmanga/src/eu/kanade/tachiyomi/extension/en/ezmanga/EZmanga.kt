package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwa
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaSortFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaStatusFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaTypeFilter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class EZmanga : EZManhwa() {

    override val apiUrl = "https://vapi.ezmanga.org/api/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

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
                        is EZmangaGenreFilter -> if (filter.value.isNotBlank()) addQueryParameter("genre", filter.value)
                        else -> {}
                    }
                }
                if (!sortAdded) addQueryParameter("sort", "latest")
            }
        }.build()
    }

    override fun pageListUrl(chapter: SChapter) = if (chapter.url.contains("/chapters/")) {
        "$apiUrl/${chapter.url}"
    } else {
        val path = chapter.url.removePrefix("/series/")
        val slash = path.indexOf('/')
        "$apiUrl/series/${path.substring(0, slash)}/chapters/${path.substring(slash + 1)}"
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        EZManhwaSortFilter(),
        EZManhwaStatusFilter(),
        EZManhwaTypeFilter(),
        EZmangaGenreFilter(),
    )
}
