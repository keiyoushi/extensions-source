package eu.kanade.tachiyomi.extension.vi.nettruyens

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup

@Source
abstract class NetTruyenS : WPComics() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(
        "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment(page.toString()) }
            .addQueryParameter("sort", "views")
            .build(),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(("$baseUrl/danh-sach-truyen" + if (page > 1) "/$page" else "").toHttpUrl())

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li a"

    override val mangaUrlDirectory = "manga"

    private suspend fun parseMangaList(url: HttpUrl): MangasPage = parseMangaPage(client.get(url, Fetch.DOCUMENT), popularMangaSelector(), ::popularMangaFromElement)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .apply { if (page > 1) addPathSegment(page.toString()) }
                .addQueryParameter("keyword", query)
                .build()
            return parseMangaList(url)
        }

        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment(page.toString()) }

        val genreFilter = filters.firstInstanceOrNull<GenreGroupFilter>()
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        genreFilter?.state?.forEach { genre ->
            when (genre.state) {
                Filter.TriState.STATE_INCLUDE -> included.add(genre.id)
                Filter.TriState.STATE_EXCLUDE -> excluded.add(genre.id)
                else -> {}
            }
        }
        url.addQueryParameter("genres", included.joinToString(","))
        url.addQueryParameter("notGenres", excluded.joinToString(","))

        filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let {
            url.addQueryParameter("status", it)
        }
        filters.firstInstanceOrNull<GenderFilter>()?.let {
            url.addQueryParameter("sex", it.toValue())
        }
        filters.firstInstanceOrNull<MinChapterFilter>()?.let {
            url.addQueryParameter("chapter_count", it.toValue())
        }
        filters.firstInstanceOrNull<SortFilter>()?.toUriPart()?.let {
            url.addQueryParameter("sort", it)
        }

        return parseMangaList(url.build())
    }

    override suspend fun parsePageList(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        val images = document.select(pageListSelector).ifEmpty {
            val chapterId = CHAPTER_ID_REGEX.find(document.html())?.groupValues?.get(1)
                ?: return emptyList()

            val html = client.post(
                "$baseUrl/ajax/image/list/chap/$chapterId?cache=0",
                headersBuilder().set("X-Requested-With", "XMLHttpRequest").build(),
                Fetch.XHR.from(chapterUrl),
                FormBody.Builder().build(),
            ).parseAs<AjaxImageListDto>().html
            Jsoup.parseBodyFragment(html, baseUrl).select(pageListSelector)
        }

        return images.mapNotNull(::imageOrNull)
            .filterNot { AD_IMAGE_PATH in it }
            .distinct()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override val supportsFilterFetching = false

    override fun getStatusList(): List<Pair<String?, String>> = listOf(
        Pair(null, intl["STATUS_ALL"]),
        Pair("completed", "Hoàn thành"),
        Pair("on-going", "Đang tiến hành"),
        Pair("on-hold", "Tạm ngưng"),
        Pair("canceled", "Đã hủy"),
    )

    override fun getSortList(): List<Pair<String?, String>> = listOf(
        Pair("latest-updated", "Mới cập nhật"),
        Pair("views", "Xem nhiều nhất"),
        Pair("views_month", "Xem nhiều nhất tháng"),
        Pair("views_week", "Xem nhiều nhất tuần"),
        Pair("views_day", "Xem nhiều nhất ngày"),
        Pair("score", "Đánh giá cao"),
        Pair("az", "Từ A-Z"),
        Pair("za", "Từ Z-A"),
        Pair("chapters", "Số chương nhiều nhất"),
        Pair("new", "Mới nhất"),
        Pair("old", "Cũ nhất"),
    )

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Bộ lọc không dùng được khi tìm kiếm bằng từ khóa"),
        GenreGroupFilter(getGenreList()),
        StatusFilter(intl["STATUS"], getStatusList()),
        GenderFilter(),
        MinChapterFilter(),
        SortFilter(intl["SORT"], getSortList()),
    )

    companion object {
        private val CHAPTER_ID_REGEX = Regex("""CHAPTER_ID\s*=\s*(\d+)""")

        // The site's ad banner, shown before and after the pages of every chapter
        private const val AD_IMAGE_PATH = "/uploads/tmp/"
    }
}

@Serializable
private class AjaxImageListDto(
    val html: String,
)
