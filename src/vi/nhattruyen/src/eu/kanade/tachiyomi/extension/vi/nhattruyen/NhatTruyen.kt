package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NhatTruyen : WPComics(
    "NhatTruyen",
    "https://nhattruyenvn.com",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val searchPath = "the-loai"

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().endsWith("/$searchPath")) {
            return MangasPage(mangas = emptyList(), hasNextPage = false)
        }
        return super.searchMangaParse(response)
    }

    // Advanced search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            val url = "$baseUrl/tim-truyen-nang-cao".toHttpUrl().newBuilder()

            filters.forEach { filter ->
                when (filter) {
                    is AdvancedGenreFilter -> {
                        filter.included.let { url.addQueryParameter("genres", it.joinToString(",")) }
                        filter.excluded.let { url.addQueryParameter("notgenres", it.joinToString(",")) }
                    }
                    is AdvancedStatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                    is ChaptersNumFilter -> filter.toUriPart()?.let { url.addQueryParameter("minchapter", it) }
                    is GenderFilter -> filter.toUriPart()?.let { url.addQueryParameter("gender", it) }
                    is OrderFilter -> filter.toUriPart()?.let { url.addQueryParameter("sort", it) }
                    else -> {}
                }
            }

            url.apply {
                addQueryParameter("page", page.toString())
            }

            return GET(url.toString(), headers)
        } else {
            return super.searchMangaRequest(page, query, filters)
        }
    }

    private class AdvancedGenre(val id: String, name: String) : Filter.TriState(name)

    private class AdvancedGenreFilter(name: String, advancedGenres: List<AdvancedGenre>) : Filter.Group<AdvancedGenre>(
        name,
        advancedGenres.map { AdvancedGenre(it.id, it.name) },
    ) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.id }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.id }
    }

    private var advancedGenresList: List<AdvancedGenre> = emptyList()

    private var fetchAdvancedGenresAttempts: Int = 0

    private fun fetchAdvancedGenres() {
        if (fetchAdvancedGenresAttempts < 3 && advancedGenresList.isEmpty()) {
            try {
                advancedGenresList =
                    client.newCall(advancedGenresRequest()).execute()
                        .asJsoup()
                        .let(::parseAdvancedGenres)
            } catch (_: Exception) {
            } finally {
                fetchAdvancedGenresAttempts++
            }
        }
    }

    private fun advancedGenresRequest() = GET("$baseUrl/tim-truyen-nang-cao", headers)

    private fun parseAdvancedGenres(document: Document): List<AdvancedGenre> {
        val items = document.select(".advsearch-form .genre-item")
        return buildList(items.size) {
            items.mapTo(this) {
                AdvancedGenre(
                    it.select("span").attr("data-id"),
                    it.ownText(),
                )
            }
        }
    }

    private class ChaptersNumFilter : UriPartFilter(
        "Số lượng chapter",
        listOf(
            Pair("1", "> 0 chapter"),
            Pair("50", ">= 50 chapter"),
            Pair("100", ">= 100 chapter"),
            Pair("200", ">= 200 chapter"),
            Pair("300", ">= 300 chapter"),
            Pair("400", ">= 400 chapter"),
            Pair("500", ">= 500 chapter"),
        ),
    )

    private class AdvancedStatusFilter(name: String, pairs: List<Pair<String?, String>>) : UriPartFilter(name, pairs)

    private fun getAdvancedStatusList(): List<Pair<String?, String>> =
        listOf(
            Pair("-1", intl["STATUS_ALL"]),
            Pair("1", intl["STATUS_ONGOING"]),
            Pair("2", intl["STATUS_COMPLETED"]),
        )

    private class GenderFilter : UriPartFilter(
        "Dành cho",
        listOf(
            Pair("-1", "Tất cả"),
            Pair("1", "Con gái"),
            Pair("2", "Con trai"),
        ),
    )

    private class OrderFilter : UriPartFilter(
        "Sắp xếp theo",
        listOf(
            Pair("0", "Chapter mới"),
            Pair("15", "Truyện mới"),
            Pair("10", "Xem nhiều nhất"),
            Pair("11", "Xem nhiều nhất tháng"),
            Pair("12", "Xem nhiều nhất tuần"),
            Pair("13", "Xem nhiều nhất hôm nay"),
            Pair("20", "Theo dõi nhiều nhất"),
            Pair("25", "Bình luận nhiều nhất"),
            Pair("30", "Số chapter nhiều nhất"),
        ),
    )

    override fun getFilterList(): FilterList {
        launchIO { fetchAdvancedGenres() }
        launchIO { fetchGenres() }
        return FilterList(
            Filter.Header("Filter cho hộp tìm kiếm"),
            StatusFilter(intl["STATUS"], getStatusList()),
            if (genreList.isEmpty()) {
                Filter.Header(intl["GENRES_RESET"])
            } else {
                GenreFilter(intl["GENRE"], genreList)
            },
            Filter.Separator(),
            Filter.Header("Tìm truyện nâng cao\n(Không sử dụng cùng với hộp tìm kiếm)"),
            if (advancedGenresList.isEmpty()) {
                Filter.Header(intl["GENRES_RESET"])
            } else {
                AdvancedGenreFilter(intl["GENRE"], advancedGenresList)
            },
            AdvancedStatusFilter(intl["STATUS"], getAdvancedStatusList()),
            ChaptersNumFilter(),
            GenderFilter(),
            OrderFilter(),
        )
    }
}
