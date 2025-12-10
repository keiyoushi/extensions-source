package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
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
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                val otherName = info.select("h2.other-name").text()
                description = info.select("div.detail-content div.shortened").flatMap { it.children() }.joinToString("\n\n") { it.wholeText().trim() } +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }

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

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/") // slug
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("Comic/Services/ComicService.asmx/ChapterList")
            .addQueryParameter("slug", slug)
            .build()
        return GET(url, headers)
    }

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
