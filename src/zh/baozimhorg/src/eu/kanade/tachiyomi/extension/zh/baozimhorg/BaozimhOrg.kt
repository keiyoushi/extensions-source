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
import java.text.SimpleDateFormat
import java.util.Locale

// Uses WPManga + GeneratePress/Blocksy Child
class BaozimhOrg : HttpSource(), ConfigurableSource {

    override val name get() = "包子漫画导航"
    override val lang get() = "zh"
    override val supportsLatest get() = true

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

    private fun getKey(link: String): String {
        val pathSegments = baseHttpUrl.resolve(link)!!.pathSegments
        val fromIndex = if (pathSegments[0] == "manga") 1 else 0
        val toIndex = if (pathSegments.last().isEmpty()) pathSegments.size - 1 else pathSegments.size
        val list = pathSegments.subList(fromIndex, toIndex).toMutableList()
        list[0] = list[0].split("-").take(2).joinToString("-")
        return list.joinToString("/")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hots/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup().also(::parseGenres)
        val mangas = document.select("article.wp-manga").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(Evaluator.Tag("h2"))!!.child(0)
                url = getKey(link.attr("href"))
                title = link.ownText()
                thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.imgSrc
            }
        }
        val hasNextPage = document.selectFirst(Evaluator.Class("next"))?.tagName() == "a" ||
            document.selectFirst(".gb-button[aria-label=Next page]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/newss/page/$page/", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
            return Request.Builder().url(url.build()).headers(headers).build()
        }
        for (filter in filters) {
            if (filter is UriPartFilter) return GET(baseUrl + filter.toUriPart() + "page/$page/", headers)
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = manga.url
        if (url[0] == '/') throw Exception(MIGRATE)
        return GET("$baseUrl/manga/$url/", headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst(Evaluator.Tag("h1"))!!.ownText()
        author = document.selectFirst(Evaluator.Class("author-content"))!!.children().joinToString { it.ownText() }
        description = document.selectFirst(".descrip_manga_info, .wp-block-stackable-text")!!.text()
        thumbnail_url = document.selectFirst("img.wp-post-image")!!.imgSrc

        val genreList = document.selectFirst(Evaluator.Class("genres-content"))!!
            .children().eachText().toMutableSet()
        if ("连载中" in genreList) {
            genreList.remove("连载中")
            status = SManga.ONGOING
        } else if ("已完结" in genreList) {
            genreList.remove("已完结")
            status = SManga.COMPLETED
        }
        genre = genreList.joinToString()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = manga.url
        if (url[0] == '/') throw Exception(MIGRATE)
        return GET("$baseUrl/chapterlist/$url/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.selectFirst(Evaluator.Class("version-chaps"))!!.children().map {
            SChapter.create().apply {
                url = getKey(it.attr("href"))
                name = it.ownText()
                date_upload = parseChapterDate(it.child(0).text())
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url
        if (url[0] == '/') throw Exception(MIGRATE)
        return GET("$baseUrl/manga/$url/", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        // Jsoup won't ignore duplicates inside <noscript> tag
        document.select(Evaluator.Tag("noscript")).remove()
        return document.select("img[decoding=async]").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgSrc)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private var genres: Array<Pair<String, String>> = emptyArray()

    private fun parseGenres(document: Document) {
        if (!enableGenres || genres.isNotEmpty()) return
        val box = document.selectFirst(Evaluator.Class("wp-block-navigation__container")) ?: return
        val items = box.children()
        genres = buildList(items.size + 1) {
            add(Pair("全部", "/allmanga/"))
            items.mapTo(this) {
                val link = it.child(0)
                Pair(link.text(), link.attr("href"))
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

        const val MIGRATE = "请将此漫画重新迁移到本图源"

        val Element.imgSrc: String get() = attr("data-src").ifEmpty { attr("src") }

        private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }

        fun parseChapterDate(text: String): Long = try {
            dateFormat.parse(text)!!.time
        } catch (_: Throwable) {
            0
        }
    }
}
