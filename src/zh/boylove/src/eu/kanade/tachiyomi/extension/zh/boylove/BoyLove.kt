package eu.kanade.tachiyomi.extension.zh.boylove

import android.app.Application
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

// Uses MACCMS http://www.maccms.la/
// 支持站点，不要添加屏蔽广告选项，何况广告本来就不多
class BoyLove : HttpSource(), ConfigurableSource {
    override val name = "香香腐宅"
    override val lang = "zh"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val baseUrl by lazy {
        val preferences =
            Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

        val mirrors = MIRRORS
        val index = preferences.getString(MIRROR_PREF, "0")!!.toInt().coerceIn(0, mirrors.size - 1)
        "https://" + mirrors[index]
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(UnscramblerInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/home/api/getpage/tp/1-topestmh-${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val listPage: ListPageDto<MangaDto> = response.parseAs()
        val mangas = listPage.list.map { it.toSManga() }
        return MangasPage(mangas, !listPage.lastPage)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/home/Api/getDailyUpdate.html?widx=4&page=${page - 1}&limit=10", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 10)
    }

    private fun textSearchRequest(page: Int, query: String): Request =
        GET("$baseUrl/home/api/searchk?keyword=$query&type=1&pageNo=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            textSearchRequest(page, query)
        } else {
            GET("$baseUrl/home/api/cate/tp/${parseFilters(page, filters)}", headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // for WebView
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl/home/book/index/id/${manga.url}")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(textSearchRequest(1, manga.title)).asObservableSuccess().map { response ->
            val id = manga.url.toInt()
            response.parseAs<ListPageDto<MangaDto>>().list.find { it.id == id }!!.toSManga()
        }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl/home/api/chapter_list/tp/${manga.url}-0-0-10", headers)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<ListPageDto<ChapterDto>>().list.map { it.toSChapter() }

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

    private fun fetchPageList(chapterUrl: String): Observable<List<Page>> =
        client.newCall(GET(baseUrl + chapterUrl, headers)).asObservableSuccess().map { response ->
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

    private fun Document.getPartsCount(): Int? {
        return selectFirst("script:containsData(do_mergeImg):containsData(context0 =)")?.data()?.run {
            substringBefore("canvas0.width")
                .substringAfterLast("var ")
                .substringBefore(';')
                .trim()
                .substringAfterLast(" ")
                .toIntOrNull()
        }
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream<ResultDto<T>>(body.byteStream()).result
    }

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
            genreFilter,
            // SortFilter(), // useless
        )
    }

    private fun fetchGenres() {
        isFetchingGenres = true
        thread {
            try {
                val request = client.newCall(GET("$baseUrl/home/book/cate.html", headers))
                val document = request.execute().asJsoup()
                genres = document.select("ul[data-str=tag] > li[class] > a")
                    .map { it.ownText() }.toTypedArray()
            } catch (e: Throwable) {
                isFetchingGenres = false
                Log.e("BoyLove", "failed to fetch genres", e)
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = "镜像网址"
            summary = "选择要使用的镜像网址，重启生效"
            val desc = MIRRORS_DESC
            entries = desc
            entryValues = Array(desc.size, Int::toString)
            setDefaultValue("0")
        }.let { screen.addPreference(it) }
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"

        // redirect URL: https://fuhouse.club/bl
        // link source URL: https://boylovepage.github.io/boylove_page
        private val MIRRORS get() = arrayOf("boylove1.mobi", "boylove3.cc", "boylove.cc", "boyloves.space", "boylove4.xyz", "boyloves.fun", "boylove.today", "fuzai.one", "xxfuzai.xyz", "fuzai.cc")
        private val MIRRORS_DESC get() = arrayOf("boylove1.mobi", "boylove3.cc", "boylove.cc（非大陆）", "boyloves.space", "boylove4.xyz", "boyloves.fun", "boylove.today", "fuzai.one", "xxfuzai.xyz", "fuzai.cc（非大陆）")
    }
}
