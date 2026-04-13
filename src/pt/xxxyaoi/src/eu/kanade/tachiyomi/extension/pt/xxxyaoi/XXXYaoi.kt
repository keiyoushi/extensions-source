package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.plusAssign

class XXXYaoi :
    Madara(
        "XXX Yaoi",
        "https://3xyaoi.com",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Upgrade-Insecure-Requests", "1")
        .set("Sec-GPC", "1")
        .set("Sec-Fetch-User", "?1")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Dest", "document")
        .set("Priority", "u=0, i")
        .set("Pragma", "no-cache")

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorAuthor = mangaDetailsSelectorArtist

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"

    override val statusFilterOptions: Map<String, String> =
        mapOf(
            intl["status_filter_completed"] to "end",
        )

    override fun searchMangaSelector() = ".page-item-detail.manga"

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        loop@ for (filter in filters) {
            when (filter) {
                is StatusFilter -> {
                    filter.state.firstOrNull { it.state }?.let {
                        url.addPathSegment(it.name)
                        break@loop
                    }
                }

                is GenreOptions -> {
                    val selected = filter.selected()
                    if (selected.isNotBlank()) {
                        url.addPathSegment("genero")
                            .addPathSegment(selected)
                        break@loop
                    }
                }

                else -> {}
            }
        }

        url.addPathSegments(searchPage(page))
        return GET(url.build(), headers)
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters: MutableList<Filter<out Any>> = mutableListOf(
            StatusFilter(
                title = intl["status_filter_title"],
                status = statusFilterOptions.map { Tag(it.key, it.value) },
            ),
        )

        if (genresList.isNotEmpty()) {
            val options: Array<Pair<String, String>> = arrayOf("Todos" to "") + genresList.map { it.name to it.id }.toTypedArray()
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_filter_header"]),
                GenreOptions(
                    displayName = intl["genre_filter_title"],
                    vals = options,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    class GenreOptions(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected() = vals[state].second
    }
}
