package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik : Madara(
    "MG Komik",
    "https://mgkomik.org",
    "id",
    SimpleDateFormat("dd MMM yy", Locale.US),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Sec-Fetch-Dest", "document")
        add("Sec-Fetch-Mode", "navigate")
        add("Sec-Fetch-Site", "same-origin")
        add("Upgrade-Insecure-Requests", "1")
        add("X-Requested-With", randomString((1..20).random())) // added for webview, and removed in interceptor for normal use
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(9, 2)
        .build()

    // ================================== Popular ======================================

    override fun popularMangaNextPageSelector() = ".wp-pagenavi span.current + a"

    // ================================== Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = false)
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}", headers)
        }

    // ================================== Search =======================================

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        filters.forEach { filter ->
            when (filter) {
                is GenreContentFilter -> {
                    val url = filter.toUriPart()
                    if (url.isBlank()) {
                        return@forEach
                    }
                    return GET(filter.toUriPart(), headers)
                }
                else -> {}
            }
        }
        return super.searchRequest(page, query, filters)
    }

    override fun searchMangaSelector() = "${super.searchMangaSelector()}, .page-listing-item .page-item-detail"

    override fun searchMangaNextPageSelector() = "a.page.larger"

    // ================================ Chapters ================================

    override val chapterUrlSuffix = ""

    // ================================ Filters ================================

    private var genresList: List<Genre> = emptyList()

    override val fetchGenres = false

    override fun getFilterList(): FilterList {
        launchIO { fetchGenresOption() }

        val filters = super.getFilterList().list.toMutableList()

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_filter_header"]),
                GenreContentFilter(
                    title = intl["genre_filter_title"],
                    options = genresList.map { it.name to it.id },
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

    private class GenreContentFilter(title: String, options: List<Pair<String, String>>) : UriPartFilter(
        title,
        options.toTypedArray(),
    )

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    private var fetchGenresAttempts: Int = 0

    fun fetchGenresOption() {
        if (fetchGenresAttempts < 3 && genresList.isEmpty()) {
            try {
                genresList = client.newCall(genresRequest()).execute()
                    .use { parseGenres(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    override fun parseGenres(document: Document): List<Genre> {
        val genres = mutableListOf<Genre>()
        genres += Genre("All", "")
        genres += document.select(".row.genres li a").map { a ->
            Genre(a.text(), a.absUrl("href"))
        }
        return genres
    }

    // =============================== Utilities ==============================

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('.')
        return List(length) { charPool.random() }.joinToString("")
    }
}
