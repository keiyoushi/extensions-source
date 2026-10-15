package eu.kanade.tachiyomi.extension.zh.boylove

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator

// Uses MACCMS http://www.maccms.la/
// 支持站点，不要添加屏蔽广告选项，何况广告本来就不多
@Source
abstract class BoyLove : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(UnscramblerInterceptor())
        .rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage("$baseUrl/home/api/getpage/tp/1-topestmh-${page - 1}")

    private suspend fun parseMangaPage(url: String): MangasPage {
        val listPage = client.get(url).parseAs<ResultDto<ListPageDto<MangaDto>>>().result
        val mangas = listPage.list.map { it.toSManga() }
        return MangasPage(mangas, !listPage.lastPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/home/Api/getDailyUpdate.html?widx=4&page=${page - 1}&limit=10")
            .parseAs<ResultDto<List<MangaDto>>>().result.map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 10)
    }

    private fun textSearchUrl(page: Int, query: String) = "$baseUrl/home/api/searchk?keyword=$query&type=1&pageNo=$page"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isNotBlank()) {
        parseMangaPage(textSearchUrl(page, query))
    } else {
        parseMangaPage("$baseUrl/home/api/cate/tp/${parseFilters(page, filters)}")
    }

    // for WebView
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/home/book/index/id/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val id = manga.url.toInt()
            client.get(textSearchUrl(1, manga.title))
                .parseAs<ResultDto<ListPageDto<MangaDto>>>().result.list.find { it.id == id }!!.toSManga()
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$baseUrl/home/api/chapter_list/tp/${manga.url}")
                .parseAs<ResultDto<ListPageDto<ChapterDto>>>().result.list.map { it.toSChapter() }.reversed()
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = chapter.url
        val index = chapterUrl.indexOf(':') // old URL format
        if (index == -1) return fetchPageList(chapterUrl)
        return chapterUrl.substring(index + 1).ifEmpty {
            return emptyList()
        }.split(',').mapIndexed { i, url ->
            Page(i, imageUrl = url.toImageUrl())
        }
    }

    private suspend fun fetchPageList(chapterUrl: String): List<Page> {
        val doc = client.get(baseUrl + chapterUrl).asJsoup()
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
        return urlList.mapIndexed { index, imageUrl ->
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

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/home/book/cate.html").asJsoup()
        .select("div[data-str=tag] > a.button")
        .map { it.ownText() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            Filter.Header("分类筛选（搜索文本时无效）"),
            StatusFilter(),
            TypeFilter(),
            RegionFilter(),
            data?.parseAs<List<String>>()?.let { GenreFilter(it.toTypedArray()) },
            Filter.Header("若要观看VIP漫画，请先在Webview中登录网站，并确认您的账户已达到Lv3"),
            VipFilter(),
            // SortFilter(), // useless
        ),
    )

    private fun Document.getPartsCount(): Int? = selectFirst("script:containsData(firstMergeImg):containsData(imageData)")?.data()?.run {
        substringBefore("var scrollTop")
            .substringAfterLast("var randomClass = ")
            .substringBefore(';')
            .trim()
            .substringAfterLast(" ")
            .toIntOrNull()
    }
}
