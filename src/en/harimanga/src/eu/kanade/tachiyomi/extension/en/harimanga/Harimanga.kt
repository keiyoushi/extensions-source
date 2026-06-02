package eu.kanade.tachiyomi.extension.en.harimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit

import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import eu.kanade.tachiyomi.source.model.FilterList

class Harimanga :
    Madara(
        "Harimanga",
        "https://www.harimanga.co.uk",
        "en",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun searchRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {

        val url = "$baseUrl/home/${searchPage(page)}".toHttpUrl().newBuilder()

        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")

        // keep Madara filters working (important)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> if (filter.state.isNotBlank())
                    url.addQueryParameter("author", filter.state)

                is ArtistFilter -> if (filter.state.isNotBlank())
                    url.addQueryParameter("artist", filter.state)

                is YearFilter -> if (filter.state.isNotBlank())
                    url.addQueryParameter("release", filter.state)

                is StatusFilter -> filter.state.forEach {
                    if (it.state) url.addQueryParameter("status[]", it.id)
                }

                is OrderByFilter -> if (filter.state != 0)
                    url.addQueryParameter("m_orderby", filter.toUriPart())

                is AdultContentFilter ->
                    url.addQueryParameter("adult", filter.toUriPart())

                is GenreConditionFilter ->
                    url.addQueryParameter("op", filter.toUriPart())

                is GenreList ->
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("genre[]", it.id)
                    }
            }
        }

        return GET(url.build(), headers)
    }
}