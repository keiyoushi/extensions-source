package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class NhatTruyen : WPComics() {

    override val popularPath = "truyen-tranh-hot"

    override val useChapterListApi = true

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div.shortened").flatMap { it.children() }
            .joinToString("\n\n") { it.wholeText().trim() }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addPathSegment(it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    is OrderByFilter -> filter.toUriPart()?.let { addQueryParameter("sort", it) }
                    else -> {}
                }
            }

            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), searchMangaSelector(), ::searchMangaFromElement)
    }

    private class OrderByFilter :
        UriPartFilter(
            "Sắp xếp theo",
            listOf(
                Pair("0", "Ngày cập nhật"),
                Pair("15", "Truyện mới"),
                Pair("10", "Top all"),
                Pair("11", "Top tháng"),
                Pair("12", "Top tuần"),
                Pair("13", "Top ngày"),
                Pair("20", "Top theo dõi"),
                Pair("25", "Bình luận"),
                Pair("30", "Số chapter"),
            ),
        )

    override fun getFilterList(genres: List<Pair<String?, String>>): FilterList = FilterList(
        buildList {
            add(StatusFilter(intl["STATUS"], getStatusList()))
            add(OrderByFilter())
            if (genres.isNotEmpty()) {
                add(GenreFilter(intl["GENRE"], genres))
            }
        },
    )
}
