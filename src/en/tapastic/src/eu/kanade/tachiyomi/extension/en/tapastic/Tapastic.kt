package eu.kanade.tachiyomi.extension.en.tapastic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable

class Tapastic : HttpSource() {

    // Originally Tapastic
    override val id = 3825434541981130345

    override val name = "Tapas"

    override val lang = "en"

    override val baseUrl = "https://tapas.io"

    private val apiUrl = "https://story-api.${baseUrl.substringAfterLast("/")}"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "https://m.tapas.io")
        .set("User-Agent", USER_AGENT)

    // ============================== Popular ===================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/cosmos/api/v1/landing/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("category_type", "COMIC")
            .addQueryParameter("subtab_id", "17")
            .addQueryParameter("size", "25")
            .addQueryParameter("page", (page - 1).toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<DataWrapper<WrapperContent>>()
        val mangas = dto.items.map(MangaDto::toSManga)
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================== Latest ====================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/cosmos/api/v1/landing/genre".toHttpUrl().newBuilder()
            .addQueryParameter("category_type", "COMIC")
            .addQueryParameter("sort_option", "NEWEST_EPISODE")
            .addQueryParameter("subtab_id", "17")
            .addQueryParameter("pageSize", "25")
            .addQueryParameter("page", (page - 1).toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("pageNumber", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("t", "COMICS")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".search-item-wrap").map { element ->
            SManga.create().apply {
                title = element.select(".item__thumb img").firstOrNull()?.attr("alt") ?: element.select(".title-section .title a").text()
                thumbnail_url = element.select(".item__thumb img, .thumb-wrap img").attr("src")
                description = element.selectFirst(".desc.force.mbm")?.text()
                url = "/series/" + element.selectFirst(".item__thumb a, .title-section .title a")!!.attr("data-series-id")
            }
        }

        return MangasPage(mangas, hasNextPage = document.selectFirst("a[class*=paging__button--next]") != null)
    }

    // ============================== Details ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}/info"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val fields = listOf(
            "series_title",
            "genres",
            "img_url",
        )
        val url = "$baseUrl${manga.url}/event-properties".toHttpUrl().newBuilder()
            .addQueryParameter("fields", fields.joinToString(","))
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<DataWrapper<DetailsWrapper>>().data.toSManga()

    // ============================== Chapters ==================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val url = "$baseUrl${manga.url}/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("page", (page++).toString())
                .addQueryParameter("sort", "NEWEST")
                .addQueryParameter("since", System.currentTimeMillis().toString())
                .addQueryParameter("large", "true")
                .addQueryParameter("last_access", "0")
                .addQueryParameter("", "") // make a same request from web
                .build()
            val response = client.newCall(GET(url, headers)).execute()
            val dto = response.parseAs<DataWrapper<ChapterListDto>>()
            chapters += dto.data.episodes.map(ChapterDto::toSChapter)
        } while (dto.data.hasNextPage())

        return Observable.just(chapters)
    }

    override fun chapterListRequest(manga: SManga): Request =
        throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    // ============================== Pages =====================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("img.content__img").mapIndexed { i, img ->
            Page(i, "", img.let { if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src") })
        }
        return pages.takeIf { it.isNotEmpty() } ?: throw IOException("Chapter locked")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===================================

    override fun getFilterList() = FilterList()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:105.0) Gecko/20100101 Firefox/105.0"
    }
}
