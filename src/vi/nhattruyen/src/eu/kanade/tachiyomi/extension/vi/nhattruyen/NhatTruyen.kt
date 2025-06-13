package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class NhatTruyen : WPComics(
    "NhatTruyen",
    "https://nhattruyenqq.com",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val searchPath = "tim-truyen"

    override val popularPath = "truyen-tranh-hot"

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                is OrderByFilter -> filter.toUriPart()?.let { url.addQueryParameter("sort", it) }
                else -> {}
            }
        }

        url.apply {
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
        }

        return GET(url.toString(), headers)
    }
    private class OrderByFilter : UriPartFilter(
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
    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            StatusFilter(intl["STATUS"], getStatusList()),
            OrderByFilter(),
            if (genreList.isEmpty()) {
                Filter.Header(intl["GENRES_RESET"])
            } else {
                GenreFilter(intl["GENRE"], genreList)
            },
        )
    }
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val url = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
        if (checkChapterLists(url).isNotEmpty()) {
            val slug = manga.url.removePrefix("/truyen-tranh/")
            return client.newCall(GET("$baseUrl/Comic/Services/ComicService.asmx/ChapterList?slug=$slug", headers))
                .asObservableSuccess()
                .map { response -> chapterListParse(response) }
        }
        return super.fetchChapterList(manga)
    }

    private fun checkChapterLists(document: Document) = document.selectFirst("a.view-more.hidden")!!.text()

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseAs<ChapterDTO>()
        val slug = response.request.url.queryParameter("slug")!!
        val chapter = json.data.map {
            SChapter.create().apply {
                setUrlWithoutDomain("$baseUrl/truyen-tranh/$slug/${it.chapter_slug}")
                name = it.chapter_name
                date_upload = dateFormatChapter.tryParse(it.updated_at)
            }
        }
        return chapter
    }

    private val dateFormatChapter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
