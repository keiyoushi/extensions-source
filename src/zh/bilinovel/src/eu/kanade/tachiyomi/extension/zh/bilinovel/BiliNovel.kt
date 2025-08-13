package eu.kanade.tachiyomi.extension.zh.bilinovel

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor

class BiliNovel : HttpSource(), ConfigurableSource {

    override val baseUrl = "https://www.bilinovel.com"

    override val lang = "zh"

    override val name = "哔哩轻小说"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = super.client.newBuilder().addInterceptor(TextInterceptor())
        .addNetworkInterceptor(NovelInterceptor()).build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "zh")
        .add("Accept", "*/*")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Customize

    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groups[1]!!.value
    private fun String.toHalfWidthDigits(): String {
        return this.map { if (it in '０'..'９') it - 65248 else it }.joinToString("")
    }

    companion object {
        const val PAGE_SIZE = 50
        val DATE_REGEX = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
        val MANGA_ID_REGEX = Regex("/novel/(\\d+)\\.html")
        val CHAPTER_ID_REGEX = Regex("/novel/\\d+/(\\d+)(?:_\\d+)?\\.html")
        val PAGE_SIZE_REGEX = Regex("（\\d+/(\\d+)）")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)

        fun addSuffixToUrl(url: String): String {
            val index = url.lastIndexOf(".")
            if (index != -1) {
                val newUrl = StringBuilder(url.substring(0, index))
                newUrl.append("_2")
                newUrl.append(url.substring(index))
                return newUrl.toString()
            }
            return url
        }
    }

    private fun getChapterUrlByContext(i: Int, els: Elements) = when (i) {
        0 -> "${els[1].attr("href")}#prev"
        else -> "${els[i - 1].attr("href")}#next"
    }

    private fun handleContent(content: Element, chapterId: Int): String {
        // 1. 计算种子
        val seed = chapterId * 135 + 236

        // 2. 获取所有子节点（包括文本节点等）
        val childNodes = content.children().filterNot {
            it.tagName() == "img"
        }.toMutableList()

        // 3. 过滤出有效的<p>元素节点
        val paragraphs = childNodes.filter {
            it.tagName() == "p" && it.text().trim().isNotBlank()
        }.toMutableList()

        // 5. 创建排列数组
        val n = paragraphs.size
        val permutation = mutableListOf<Int>().apply {
            // 前20个保持原顺序
            addAll(0 until minOf(20, n))
            // 处理超过20的部分
            if (n > 20) {
                val after20 = (20 until n).toMutableList()
                var num = seed.toLong()
                for (i in after20.size - 1 downTo 1) {
                    num = (num * 9302L + 49397L) % 233280L
                    val j = floor((num / 233280.0) * (i + 1)).toInt()
                    after20[j] = after20[i].also { after20[i] = after20[j] }
                }
                addAll(after20)
            }
        }

        // 6. 创建重排序后的段落数组
        val shuffled = arrayOfNulls<Element>(n).apply {
            for (i in 0 until n) {
                this[permutation[i]] = paragraphs[i].also {
                    it.removeAttr("class")
                    it.text("\u00A0\u00A0\u00A0\u00A0" + it.text())
                }
            }
        }.map { it!! } // 转换为非空列表

        // 7. 替换原始节点中的<p>元素
        // var paraIndex = 0
        // for (i in 0..paragraphs.size) {
        //     paragraphs[i] = shuffled[paraIndex++]
        // }

        // 8. 清空并重新添加处理后的节点
        content.html("")
        content.appendChildren(shuffled)

        // 9. 返回最终HTML
        return content.html()
    }

    // Popular Page

    override fun popularMangaRequest(page: Int): Request {
        val suffix = preferences.getString(PREF_POPULAR_MANGA_DISPLAY, "/top/weekvisit/%d.html")!!
        return GET(baseUrl + String.format(suffix, page), headers)
    }

    override fun popularMangaParse(response: Response) = response.asJsoup().let {
        val mangas = it.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                val img = it.selectFirst("img")!!
                thumbnail_url = img.absUrl("data-src")
                title = img.attr("alt")
            }
        }
        MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/top/lastupdate/$page.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Page

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search").addPathSegment("${query}_$page.html")
        } else {
            url.addPathSegment("top").addPathSegment(filters[1].toString())
                .addPathSegment("$page.html")
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("detail")) {
            return MangasPage(listOf(mangaDetailsParse(response)), false)
        }
        return popularMangaParse(response)
    }

    // Manga Detail Page

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val doc = response.asJsoup()
        val meta = doc.select(".book-meta")[1].text().split("|")
        val backupname = doc.selectFirst(".bkname-body")?.let { "\n\n別名：${it.text()}" } ?: ""
        url = doc.location()
        title = doc.selectFirst(".book-title")!!.text()
        thumbnail_url = doc.selectFirst(".book-cover")!!.attr("src")
        description = doc.selectFirst("#bookSummary > content")?.wholeText() + backupname
        author = doc.selectFirst(".authorname")?.text()
        status = when (meta.getOrNull(1)) {
            "连载" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (doc.select(".tag-small").map(Element::text) + meta.getOrElse(2) { "" }).joinToString()
        initialized = true
    }

    // Catalog Page

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/novel/${manga.id}/catalog", headers)

    override fun chapterListParse(response: Response) = response.asJsoup().let {
        val info = it.selectFirst(".chapter-sub-title")!!.text()
        val date = DATE_FORMAT.tryParse(DATE_REGEX.find(info)?.value)
        it.select(".catalog-volume").flatMap { v ->
            val chapterBar = v.selectFirst(".chapter-bar")!!.text().toHalfWidthDigits()
            val chapters = v.select(".chapter-li-a")
            chapters.mapIndexed { i, e ->
                val url = e.absUrl("href").takeUnless("javascript:cid(1)"::equals)?.let(::addSuffixToUrl)
                SChapter.create().apply {
                    name = e.text().toHalfWidthDigits()
                    date_upload = date
                    scanlator = chapterBar
                    setUrlWithoutDomain(url ?: getChapterUrlByContext(i, chapters))
                }
            }
        }.reversed()
    }

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().let {
        val size = PAGE_SIZE_REGEX.find(it.selectFirst("#atitle")!!.text())!!.groups[1]!!.value
        val prefix = it.location().substringBeforeLast("_")
        List(size.toInt()) { i ->
            Page(i, prefix + "${if (i > 0) "_${i + 1}" else ""}.html")
        }
    }

    // Image

    override fun imageUrlParse(response: Response) = response.asJsoup().let {
        val content = it.selectFirst("#acontent")!!
        val chapterId = CHAPTER_ID_REGEX.find(it.location())!!.groups[1]!!.value
        TextInterceptorHelper.createUrl("", handleContent(content, chapterId.toInt()))
    }
}
