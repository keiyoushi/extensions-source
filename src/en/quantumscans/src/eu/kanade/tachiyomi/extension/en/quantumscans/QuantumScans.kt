package eu.kanade.tachiyomi.extension.en.quantumscans

import eu.kanade.tachiyomi.multisrc.iken.GenreFilter
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.SelectFilter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.concurrent.TimeUnit

// Moved from HeanCms to Iken
class QuantumScans : Iken(
    "Quantum Toon",
    "en",
    "https://quantumtoon.com",
    "https://vapi.quantumtoon.com",
) {
    override val versionId = 4

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "18")
            addQueryParameter("orderBy", "totalViews")
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply { // 'query' instead of 'posts'
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "18")
            addQueryParameter("orderBy", "updatedAt")
        }.build()
        return GET(url, headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headersBuilder().add("rsc", "1").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.body.string().lines()
            .mapNotNull { line ->
                val jsonStartIndex = line.indexOf('{').takeIf { it != -1 } ?: return@mapNotNull null
                val jsonString = line.substring(jsonStartIndex)
                try {
                    jsonString.parseAs<PageDto>().takeIf { it.url.isNotEmpty() }
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.order }
            .mapIndexed { i, p -> Page(i, imageUrl = p.url) }
    }

    private var genresList: List<Pair<String, String>> = emptyList()
    private var fetchGenresAttempts = 0

    private fun fetchGenres() {
        try {
            val response = client.newCall(GET("$apiUrl/api/genres", headers)).execute()
            genresList = response.parseAs<List<GenreDto>>()
                .map { Pair(it.name, it.id.toString()) }
        } catch (e: Throwable) {} finally {
            fetchGenresAttempts++
        }
    }

    override fun getFilterList(): FilterList {
        if (genresList.isEmpty() && fetchGenresAttempts < 3) {
            Observable.fromCallable { fetchGenres() }
                .subscribeOn(rx.schedulers.Schedulers.io())
                .subscribe()
        }

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
        )

        if (genresList.isNotEmpty()) {
            filters.add(GenreFilter(genresList))
        } else {
            filters.add(Filter.Header("Press 'Reset' to attempt to load genres"))
        }
        return FilterList(filters)
    }

    private class SortFilter : SelectFilter(
        "Sort",
        "orderBy",
        listOf(
            Pair("Popularity", "totalViews"),
            Pair("Latest", "updatedAt"),
        ),
    )

    private class StatusFilter : SelectFilter(
        "Status",
        "seriesStatus",
        listOf(
            Pair("All", ""),
            Pair("Ongoing", "ONGOING"),
            Pair("Hiatus", "HIATUS"),
            Pair("Completed", "COMPLETED"),
            Pair("Dropped", "DROPPED"),
        ),
    )
}
