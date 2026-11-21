package eu.kanade.tachiyomi.extension.vi.nettruyenviet

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.ZoneId

@Source
abstract class NetTruyenViet : WPComics() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)

    override val dateZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    override val useChapterListApi = true

    override fun getSortList() = netTruyenSortList()

    override val mangaUrlDirectory = "truyen-tranh"

    override fun isSiteHost(host: String) = super.isSiteHost(host) || host.removePrefix("www.") in SITE_HOSTS

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(
        client.get("$baseUrl/$searchPath?sort=10&page=$page", Fetch.DOCUMENT),
        popularMangaSelector(),
        ::popularMangaFromElement,
    )

    // The root is now a landing page without the list
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(
        client.get("$baseUrl/truyen-tranh-moi?page=$page", Fetch.DOCUMENT),
        latestUpdatesSelector(),
        ::latestUpdatesFromElement,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val websitePath = filters.firstInstanceOrNull<WebsiteFilter>()?.toUriPart()
        val url = when {
            query.isNotBlank() -> "$baseUrl/$searchPath".toHttpUrl().newBuilder().addQueryParameter(queryParam, query)
            websitePath != null -> "$baseUrl$websitePath".toHttpUrl().newBuilder()
            else -> "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
                filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { addPathSegment(it) }
                filters.firstInstanceOrNull<SortFilter>()?.toUriPart()?.let { addQueryParameter("sort", it) }
                filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let { addQueryParameter("status", it) }
            }
        }.addQueryParameter("page", page.toString()).build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), searchMangaSelector(), ::searchMangaFromElement)
    }

    override val pageListSelector = "#chapter-content img, .reading-detail .page-chapter img, .chapter-content img, .page-chapter img"

    override suspend fun parsePageList(response: Response): List<Page> = response.asJsoup().select(pageListSelector)
        .mapNotNull(::imageOrNull)
        .filterNot { SITE_IMAGES_PATH in it }
        .distinct()
        .mapIndexed { i, url -> Page(i, imageUrl = url) }

    override fun getFilterList(genres: List<Pair<String?, String>>): FilterList = FilterList(
        buildList {
            add(Filter.Header("Lưu ý: Bộ lọc không dùng chung được với tìm kiếm"))
            add(Filter.Separator())
            if (genres.isNotEmpty()) {
                add(GenreFilter(intl["GENRE"], genres))
            }
            add(WebsiteFilter())
            add(SortFilter(intl["SORT"], getSortList()))
            add(StatusFilter(intl["STATUS"], getStatusList()))
        },
    )

    private class WebsiteFilter :
        UriPartFilter(
            "Website",
            listOf(
                Pair(null, "Tất cả"),
                Pair("/tag/truyenqq", "TruyenQQ"),
                Pair("/tag/blogtruyen", "BlogTruyen"),
                Pair("/tag/teamlanhlung", "TeamLanhLung"),
                Pair("/tag/tusachxinhxinh", "Tủ Sách Xinh Xinh"),
                Pair("/tag/truyengihot", "TruyenGiHot"),
                Pair("/tag/tutientruyen", "Tu Tiên Truyện"),
                Pair("/tag/ungtycomics", "UngtyComics"),
                Pair("/tag/vycomycs", "VyComycs - Tủ Sách Nhỏ"),
                Pair("/tag/baotangtruyen", "Bảo Tàng Truyện"),
                Pair("/tag/dualeotruyen", "Dưa Leo Truyện"),
                Pair("/tag/fastscan", "Fastscan"),
                Pair("/tag/cmanga", "CManga"),
                Pair("/tag/fuhu", "FuHu"),
                Pair("/tag/soaicacomic", "Soái Ca Comic"),
                Pair("/tag/vlogtruyen", "VlogTruyen"),
                Pair("/tag/toptruyen", "TopTruyện"),
            ),
        )

    companion object {
        // The site's logo or notice image, placed before or after the pages depending on the domain
        private const val SITE_IMAGES_PATH = "/assets/images/"

        // The site serves the same pages on these domains, so their links open with the chosen base URL
        private val SITE_HOSTS = setOf(
            "nettruyenviet10.com",
            "nettruyenviet11.com",
            "hinettruyen.com",
        )
    }
}
