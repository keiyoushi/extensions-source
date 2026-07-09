package eu.kanade.tachiyomi.extension.zh.boylove

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator
import rx.Observable
import kotlin.concurrent.thread

// Uses MACCMS http://www.maccms.la/
// 支持站点，不要添加屏蔽广告选项，何况广告本来就不多
@Source
abstract class BoyLove : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(UnscramblerInterceptor())
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/home/api/getpage/tp/1-topestmh-${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val listPage = response.parseAs<ResultDto<ListPageDto<MangaDto>>>().result
        val mangas = listPage.list.map { it.toSManga() }
        return MangasPage(mangas, !listPage.lastPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/home/Api/getDailyUpdate.html?widx=4&page=${page - 1}&limit=10", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResultDto<List<MangaDto>>>().result.map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 10)
    }

    private fun textSearchRequest(page: Int, query: String): Request = GET("$baseUrl/home/api/searchk?keyword=$query&type=1&pageNo=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        textSearchRequest(page, query)
    } else {
        GET("$baseUrl/home/api/cate/tp/${parseFilters(page, filters)}", headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // for WebView
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/home/book/index/id/${manga.url}", headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(textSearchRequest(1, manga.title)).asObservableSuccess().map { response ->
        val id = manga.url.toInt()
        response.parseAs<ResultDto<ListPageDto<MangaDto>>>().result.list.find { it.id == id }!!.toSManga()
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/home/api/chapter_list/tp/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ResultDto<ListPageDto<ChapterDto>>>().result.list.map { it.toSChapter() }.reversed()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = chapter.url
        val index = chapterUrl.indexOf(':') // old URL format
        if (index == -1) return fetchPageList(chapterUrl)
        return chapterUrl.substring(index + 1).ifEmpty {
            return Observable.just(emptyList())
        }.split(',').mapIndexed { i, url ->
            Page(i, imageUrl = url.toImageUrl())
        }.let { Observable.just(it) }
    }

    private fun fetchPageList(chapterUrl: String): Observable<List<Page>> = client.newCall(GET(baseUrl + chapterUrl, headers)).asObservableSuccess().map { response ->
        val doc = response.asJsoup()
        val root = doc.selectFirst(Evaluator.Tag("section"))!!
        val images = root.select(Evaluator.Class("reader-cartoon-image"))
        val urlList = if (images.isEmpty()) {
            root.select(Evaluator.Tag("img")).map { it.attr("src").trim().toImageUrl() }
                .filterNot { it.endsWith(".gif") }
        } else {
            images.map { it.child(0) }
                .filter { it.attr("src").endsWith("load.png") }
                .map { it.attr("data-original").trim().toImageUrl() }
        }
        val parts = doc.getPartsCount()
        urlList.mapIndexed { index, imageUrl ->
            val url = if (parts == null) {
                imageUrl
            } else {
                imageUrl.toHttpUrl().newBuilder()
                    .addQueryParameter(UnscramblerInterceptor.PARTS_COUNT_PARAM, parts.toString())
                    .build()
                    .toString()
            }
            Page(index, imageUrl = url)
        }
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private var genres: Array<String> = emptyArray()
    private var isFetchingGenres = false

    override fun getFilterList(): FilterList {
        val genreFilter = if (genres.isEmpty()) {
            if (!isFetchingGenres) fetchGenres()
            Filter.Header("点击“重置”尝试刷新标签列表")
        } else {
            GenreFilter(genres)
        }
        return FilterList(
            Filter.Header("分类筛选（搜索文本时无效）"),
            StatusFilter(),
            TypeFilter(),
            RegionFilter(),
            genreFilter,
            Filter.Header("若要观看VIP漫画，请先在Webview中登录网站，并确认您的账户已达到Lv3"),
            VipFilter(),
            // SortFilter(), // useless
        )
    }

    private fun fetchGenres() {
        isFetchingGenres = true
        thread {
            try {
                val request = client.newCall(GET("$baseUrl/home/book/cate.html", headers))
                val document = request.execute().asJsoup()
                genres = document.select("div[data-str=tag] > a.button")
                    .map { it.ownText() }.toTypedArray()
            } catch (e: Throwable) {
                isFetchingGenres = false
                Log.e("BoyLove", "failed to fetch genres", e)
            }
        }
    }

    private fun Document.getPartsCount(): Int? = selectFirst("script:containsData(firstMergeImg):containsData(imageData)")?.data()?.run {
        substringBefore("var scrollTop")
            .substringAfterLast("var randomClass = ")
            .substringBefore(';')
            .trim()
            .substringAfterLast(" ")
            .toIntOrNull()
    }
}
