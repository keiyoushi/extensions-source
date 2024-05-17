package eu.kanade.tachiyomi.extension.zh.baozimhorg

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// Uses WPManga + GeneratePress/Blocksy Child
class BaozimhOrg : HttpSource(), ConfigurableSource {

    override val name = "包子漫画导航"

    override val lang = "zh"

    override val supportsLatest = true

    override val baseUrl: String

    private val baseHttpUrl: HttpUrl

    private val enableGenres: Boolean

    init {
        val mirrors = MIRRORS
        val mirrorIndex = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
            .getString(MIRROR_PREF, "0")!!.toInt().coerceAtMost(mirrors.size - 1)
        baseUrl = "https://" + mirrors[mirrorIndex]
        baseHttpUrl = baseUrl.toHttpUrl()
        enableGenres = mirrorIndex == 0
    }

    override val client = network.client.newBuilder()
        .addInterceptor(UrlInterceptor)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hots/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup().also(::parseGenres)
        val mangas = document.select(".cardlist .pb-2 a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = element.selectFirst("img")?.imgSrc
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, document.selectFirst("a[aria-label=下一頁] button") != null)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/newss/page/$page/", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/s".toHttpUrl().newBuilder()
                .addPathSegment(query)
                .addQueryParameter("page", "$page")
                .build()
            return GET(url, headers)
        }
        for (filter in filters) {
            if (filter is UriPartFilter) return GET("$baseUrl${filter.toUriPart()}/page/$page/", headers)
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst(Evaluator.Tag("h1"))!!.ownText()
        description = document.selectFirst("p.text-medium.line-clamp-4")?.text()
        thumbnail_url = document.selectFirst("img.object-cover.rounded-lg")?.imgSrc
        author = document.selectFirst("div.text-small.py-1.pb-2 a:nth-child(3)")
            ?.text()?.replace(",", "")?.trim()

        val genreList = document.select("div.py-1:nth-child(4) a")
            .map { it.ownText() }
            .toMutableList()

        if ("连载中" in genreList) {
            genreList.remove("连载中")
            status = SManga.ONGOING
        } else if ("已完结" in genreList) {
            genreList.remove("已完结")
            status = SManga.COMPLETED
        }
        genre = genreList.joinToString()
    }

    override fun chapterListRequest(manga: SManga) = super.chapterListRequest(
        manga.apply {
            url = url.replace("manga", "chapterlist")
        },
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mid = document.selectFirst("#allchapters")!!.attr("data-mid")

        val chapterURL = "$baseUrl/manga/get".toHttpUrl().newBuilder()
            .addQueryParameter("mid", mid)
            .addQueryParameter("mode", "all")
            .build()

        val chapterResponse = client.newCall(GET(chapterURL, headers)).execute()

        val chapters = chapterResponse.asJsoup().select("#allchapterlist .chapteritem")
            .map { element ->
                SChapter.create().apply {
                    val anchor = element.selectFirst("a")!!
                    name = anchor.attr("data-ct")
                    chapter_number = element.attr("data-index").toFloat()
                    setUrlWithoutDomain(anchor.absUrl("href"))
                }
            }

        return chapters.sortedBy { it.chapter_number }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val container = document.selectFirst("#chapterContent")!!
        val host = container.attr("data-host")
        val ms = container.attr("data-ms")
        val cs = container.attr("data-cs")
        val url = "$host/chapter/getcontent".toHttpUrl().newBuilder()
            .addQueryParameter("m", ms)
            .addQueryParameter("c", cs)
            .build()

        val chapterResponse = client.newCall(GET(url, headers)).execute()
        if (!chapterResponse.isSuccessful) {
            return emptyList()
        }

        return chapterResponse.asJsoup().select("#chapcontent noscript img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgSrc)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private var genres: Array<Pair<String, String>> = emptyArray()

    private fun parseGenres(document: Document) {
        if (!enableGenres || genres.isNotEmpty()) return
        val items = document.select("div h2:contains(漫畫類型) + div a")

        genres = buildList(items.size + 1) {
            add(Pair("全部", "/allmanga"))
            items.mapTo(this) { element ->
                Pair(element.text(), element.attr("href"))
            }
        }.toTypedArray()
    }

    override fun getFilterList(): FilterList =
        if (!enableGenres) {
            FilterList()
        } else if (genres.isEmpty()) {
            FilterList(listOf(Filter.Header("点击“重置”刷新分类")))
        } else {
            val list = listOf(
                Filter.Header("分类（搜索文本时无效）"),
                UriPartFilter("分类", genres),
            )
            FilterList(list)
        }

    class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            val mirrors = MIRRORS
            key = MIRROR_PREF
            title = "镜像网址"
            summary = "%s\n重启生效，暂未适配GoDa漫画的分类筛选功能"
            entries = mirrors
            entryValues = Array(mirrors.size) { it.toString() }
            setDefaultValue("0")
        }.let(screen::addPreference)
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"
        private val MIRRORS get() = arrayOf("baozimh.org", "cn.godamanga.com")

        val Element.imgSrc: String get() = attr("data-src").ifEmpty { attr("src") }
    }
}
