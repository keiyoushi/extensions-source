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
import eu.kanade.tachiyomi.util.asJsoup
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

    override fun pageListParse(response: Response): List<Page> {
        val scriptContent = response.asJsoup()
            .select("script:containsData(self.__next_f.push)")
            .joinToString("") { it.data() }

        val startIndex = scriptContent.indexOf("{\\\"API_Response\\\":")
            .takeIf { it != -1 } ?: throw Exception("Could not find start of chapter JSON data")

        var braces = 0
        val endIndex = scriptContent.substring(startIndex).indexOfFirst {
            when (it) { '{' -> braces++; '}' -> braces-- }
            braces == 0
        }.takeIf { it != -1 }?.plus(startIndex) ?: throw Exception("Could not find end of chapter JSON data")

        val jsonString = scriptContent.substring(startIndex, endIndex + 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        return jsonString.parseAs<ChapterImages>()
            .API_Response.chapter.images
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
