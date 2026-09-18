package eu.kanade.tachiyomi.extension.zh.mh160

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 漫画160 runs the "qingtiancms" (qTcms) reader: each chapter page embeds a base64 string of
 * image paths separated by `$qingtiandy$`, and the reader JS picks an image host based on the
 * chapter id.
 */
@Source
abstract class Mh160 : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", USER_AGENT)

    // ------------------------------------------------------------------ listings

    // Full catalogue, 12 titles per page: /kanmanhua/all/, /kanmanhua/all/2.html, ...
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get(listUrl("all", page), headers).asJsoup())

    // Single page of recent updates.
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/kanmanhua/zaixian_recent.html", headers).asJsoup()
        return MangasPage(parseMangaList(document).mangas, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/statics/searchelxt1e1.aspx".toHttpUrl().newBuilder()
                .addQueryParameter("key", query.trim())
                .addQueryParameter("page", page.toString())
                .build()
                .toString()
        } else {
            // The site cannot combine region and genre, so region wins.
            val region = filters.firstInstanceOrNull<RegionFilter>()?.selected
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
            listUrl(region ?: genre ?: "all", page)
        }
        return parseMangaList(client.get(url, headers).asJsoup())
    }

    private fun listUrl(slug: String, page: Int): String = if (page <= 1) "$baseUrl/kanmanhua/$slug/" else "$baseUrl/kanmanhua/$slug/$page.html"

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("ul.mh-search-list > li").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst(".NewPages a:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst(".mh-works-title h4 a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.attr("title").ifBlank { link.text() }
            thumbnail_url = element.selectFirst(".mh-nlook-w img")?.absUrl("src")
            description = element.selectFirst(".mh-works-decs")?.text()
        }
    }

    // ------------------------------------------------------- details + chapters

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val match = MANGA_URL_REGEX.find(url.encodedPath) ?: return null
        val mangaUrl = "/kanmanhua/${match.groupValues[1]}/"
        val document = client.get(baseUrl + mangaUrl, headers).asJsoup()
        return parseDetails(document).apply { this.url = mangaUrl }
    }

    // Details and chapters live on the same page, so fetch it once and return both.
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, headers).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".mh-date-info-name h4 a")?.text()
            ?: document.title().substringBefore("漫画,")
        thumbnail_url = document.selectFirst(".mh-date-bgpic img")?.absUrl("src")
        author = document.selectFirst(".works-info-tc .one em")?.text()
        artist = author
        description = document.selectFirst("#workint")?.text()
        status = when (document.selectFirst("p.works-info-tc span:contains(状态) em")?.text()?.trim()) {
            "连载中" -> SManga.ONGOING
            "已完结", "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Listed newest first on the page, which is the order the app expects.
    private fun parseChapters(document: Document): List<SChapter> = document.select("ul[id^=mh-chapter-list-ol] li a").map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst("p")?.text() ?: link.text()
        }
    }

    // --------------------------------------------------------------------- pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = client.get(baseUrl + chapter.url, headers).use { it.body.string() }
        val encoded = MURL_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("找不到图片列表，页面结构可能已更改")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        if (decoded.contains("+http://") || decoded.contains("+https://")) {
            throw Exception("该章节已下架")
        }
        val chapterId = PID_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        // Mirrors f_qTcms_Pic_curUrl_realpic() in the site's show.js.
        val host = if (chapterId > 542724) IMAGE_HOSTS[(chapterId % IMAGE_HOSTS.size).toInt()] else LEGACY_IMAGE_HOST

        return decoded.split(PATH_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapIndexed { index, path ->
                val imageUrl = when {
                    path.startsWith("http") -> path
                    path.startsWith("/") -> host + encodePath(path)
                    else -> "$baseUrl/statics/pic/?p=" + URLEncoder.encode(path, "UTF-8")
                }
                Page(index, imageUrl = imageUrl)
            }
    }

    /** Percent-encodes each path segment (the paths contain Chinese characters). */
    private fun encodePath(path: String): String = path.split("/").joinToString("/") { segment ->
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

    // ------------------------------------------------------------------- filters

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("筛选仅在搜索框为空时生效；地区优先于题材"),
        RegionFilter(),
        GenreFilter(),
    )

    private open class UrlSelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        val selected: String? get() = options[state].second
    }

    private class RegionFilter :
        UrlSelectFilter(
            "地区",
            listOf(
                "不限" to null,
                "日韩" to "zaixian_rhmh",
                "内地" to "zaixian_dlmh",
                "港台" to "zaixian_gtmh",
            ),
        )

    private class GenreFilter :
        UrlSelectFilter(
            "题材",
            listOf(
                "全部" to null,
                "热血" to "rexue",
                "格斗" to "gedou",
                "科幻" to "kehuan",
                "竞技" to "jingji",
                "搞笑" to "gaoxiao",
                "推理" to "tuili",
                "恐怖" to "kongbu",
                "耽美" to "danmei",
                "少女" to "shaonv",
                "恋爱" to "lianai",
                "生活" to "shenghuo",
                "战争" to "zhanzheng",
                "故事" to "gushi",
                "冒险" to "maoxian",
                "魔幻" to "mohuan",
                "玄幻" to "xuanhuan",
                "校园" to "xiaoyuan",
                "悬疑" to "xuanyi",
                "萌系" to "mengxi",
                "穿越" to "chuanyue",
                "后宫" to "hougong",
                "都市" to "dushi",
                "武侠" to "wuxia",
                "历史" to "lishi",
                "同人" to "tongren",
            ),
        )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val PATH_SEPARATOR = "\$qingtiandy\$"
        private const val LEGACY_IMAGE_HOST = "https://mhpic6.tgmhfc.uk"
        private val IMAGE_HOSTS = listOf(
            "https://mhpic5er.tgmhfc.uk",
            "https://mhpic789-5.tgmhfc.uk",
            "https://mhpic7fr.tgmhfc.uk",
            "https://mhpicwt.tgmhfc.uk",
            "https://mhpicwx.tgmhfc.uk",
        )
        private val MANGA_URL_REGEX = Regex("""^/kanmanhua/([A-Za-z0-9]+)/?""")
        private val MURL_REGEX = Regex("""qTcms_S_m_murl_e\s*=\s*"([^"]+)"""")
        private val PID_REGEX = Regex("""qTcms_S_p_id\s*=\s*"(\d+)"""")
    }
}
