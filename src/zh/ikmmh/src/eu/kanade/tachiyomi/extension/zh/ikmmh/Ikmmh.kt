package eu.kanade.tachiyomi.extension.zh.ikmmh

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 爱看漫 (ymcdnyfqdapp.ikmmh.com)
 *
 * 站点特征：
 * - 仅移动 UA 可访问（桌面 UA 返回 404）；Mihon 默认 UA 即移动端，仅默认 UA 非移动时回退硬编码移动 UA
 * - WAF 对非浏览器形态请求敏感：需锁定 HTTP/1.1、显式 Accept/Accept-Language、去除 Origin，
 *   否则可能触发封禁出口 IP（connection closed 后浏览器也无法访问）
 * - 热门/分类共用 /booklists/{area}/{tag}/{status}/{page}.html，站点按 1 起始分页
 * - 最新 /update/{page}.html：分页大小不固定（整周更新合排，页间 370/181/0 条），
 *   站点第 3 页起为空，沿用 app 约定 7 页上限防呆
 * - 搜索 /search?searchkey=...（无分页，恒 hasNextPage=false）
 * - 详情 og: meta；作者字段含 "\," 转义（如 "Son Yua\,RAKO"），还原为 ","
 * - 章节 /api/comic/zyz/chapters?zpid={bookId}&orderby=desc（desc 由站点原生支持，返回新→旧）
 * - 正文 POST /api/comic/read/pics 分批读取（limit=10，offset 递增，超界返回空 pic 数组）
 *
 * name / lang / id / baseUrl 由 build.gradle.kts 的 keiyoushi 块注入。
 */
@Source
abstract class Ikmmh : KeiSource() {

    // 站点对无 Cookie 的请求会在每次响应中轮换下发新 PHPSESSID（实测），
    // 固定回传同一会话可避免"WAF 会话不连贯"特征；缺失或异常时静默跳过，不影响可用性
    @Volatile
    private var sessionCookie: String? = null

    // 站点桌面端返回 404，桌面 UA 会触发站点 WAF 封禁出口 IP，必须使用移动 UA。
    // Mihon 默认 UA 即移动端，仅当默认 UA 非移动时才替换为硬编码移动 UA（保留用户在应用设置里的自定义）
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .apply {
            // headersBuilder 已预置默认 UA（Mihon 设置 → Default user agent）
            val defaultUserAgent = build()["User-Agent"]
            if (defaultUserAgent == null || !defaultUserAgent.contains("Mobile")) {
                set("User-Agent", MOBILE_UA)
            }
        }
        .set("Accept-Language", "zh-CN,zh;q=0.9")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .removeAll("Origin")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        .protocols(listOf(Protocol.HTTP_1_1))
        .rateLimit(permits = 2, period = 500.milliseconds) { it.host == baseUrl.toHttpUrl().host }
        .addInterceptor(SessionInterceptor({ sessionCookie }, { sessionCookie = it }))

    // 所有入口（热门/最新/搜索/详情/读图）先确保已取得 PHPSESSID，失败不阻塞主流程
    protected suspend fun warmupSession() {
        if (sessionCookie == null) {
            runCatching {
                client.get("$baseUrl/").asJsoup()
            }
        }
    }

    // ---- 热门（与分类浏览共用 booklists） ----

    override suspend fun getPopularManga(page: Int): MangasPage {
        warmupSession()
        val document = client.get("$baseUrl/booklists/9/全部/3/$page.html").asJsoup()
        return MangasPage(parseListItemMangas(document), document.hasNextPage())
    }

    // ---- 最新 ----

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        warmupSession()
        val document = client.get("$baseUrl/update/$page.html").asJsoup()
        return MangasPage(parseListItemMangas(document), page < LATEST_MAX_PAGES)
    }

    // ---- 搜索与分类浏览 ----

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        warmupSession()
        if (query.isNotBlank()) {
            // 站点搜索接口无分页
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("searchkey", query)
                .build()
            val document = client.get(url).asJsoup()
            return MangasPage(parseListItemMangas(document), hasNextPage = false)
        }

        var area = "9"
        var tag = "全部"
        var status = "3"
        filters.forEach { filter ->
            when (filter) {
                is AreaFilter -> area = filter.selected().first
                is TagFilter -> tag = filter.selected().first
                is StatusFilter -> status = filter.toString()
                else -> {}
            }
        }

        val document = client.get("$baseUrl/booklists/$area/$tag/$status/$page.html").asJsoup()
        return MangasPage(parseListItemMangas(document), document.hasNextPage())
    }

    // ---- 详情与章节（不同端点，按 flags 并发获取） ----

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                warmupSession()
                client.get(getMangaUrl(manga)).asJsoup().parseDetails(manga)
            } else {
                manga
            }
        }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun Document.parseDetails(manga: SManga): SManga = manga.apply {
        title = selectFirst("meta[property=og:title]")?.attr("content") ?: title
        thumbnail_url = selectFirst("meta[property=og:image]")?.attr("content")
        description = selectFirst("meta[property=og:description]")?.attr("content")
        genre = selectFirst("meta[property=og:cartoon:category]")?.attr("content")
        // 作者字段形如 "Son Yua\,RAKO"，站点用 "\," 转义逗号
        val authorMeta = selectFirst("meta[property=og:cartoon:author]")?.attr("content")?.replace("\\,", ",")
        author = authorMeta
        artist = authorMeta
        status = when (selectFirst("meta[property=og:cartoon:status]")?.attr("content")?.trim()) {
            "完结" -> SManga.COMPLETED
            "连载" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        // manga.url 形如 /book/71097/
        val bookId = manga.url.trim().trimEnd('/').substringAfterLast('/')
        val chapters = client.get(
            "$baseUrl/api/comic/zyz/chapters?ph=1&tempid=3&zpid=$bookId&page=0&line=48&orderby=desc",
        ).parseAs<ChapterListDto> {
            it.replace("\\,", ",")
        }

        return chapters.length.mapIndexed { index, dto ->
            SChapter.create().apply {
                url = dto.url
                name = dto.name
                // orderby=desc 返回新→旧，编号按站点原顺序（旧→新）反向计算
                chapter_number = (chapters.length.size - index).toFloat()
                date_upload = DATE_FORMAT.tryParseDate(dto.stime)
            }
        }
    }

    // ---- URL 搜索（在搜索框粘贴站点链接） ----

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "book") return null

        return client.get(url).asJsoup().parseDetails(SManga.create()).apply {
            this.url = url.encodedPath
        }
    }

    // ---- 正文 ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // chapter.url 形如 /chapter/71097/2332470.html
        val parts = chapter.url.trim('/').split('/')
        require(parts.size == 3 && parts[0] == "chapter") {
            "Unexpected chapter URL: ${chapter.url}"
        }
        val aid = parts[1]
        val cid = parts[2].substringBefore('.')

        warmupSession()
        val pages = mutableListOf<Page>()
        var offset = 0
        while (pages.size < MAX_PAGES) {
            val body = FormBody.Builder()
                .add("id", cid)
                .add("aid", aid)
                .add("offset", offset.toString())
                .add("limit", "10")
                .build()

            val data = client.post("$baseUrl/api/comic/read/pics", body).parseAs<PicsDto>().data
            val batch = data.pic
            if (batch.isEmpty()) break

            batch.forEach { pic ->
                pages.add(Page(index = pages.size, imageUrl = pic.pic))
            }
            offset += batch.size
            if (data.total > 0 && offset >= data.total) break
        }
        return pages
    }

    // ---- 筛选器 ----

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    private companion object {
        const val LATEST_MAX_PAGES = 7
        const val MAX_PAGES = 1000
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH)
    }
}

/** 列表卡片：li.item.comic-item > a[href]（书链）、p.title（标题）、img.img（封面） */
private fun Ikmmh.parseListItemMangas(document: Document): List<SManga> = document
    .select("li.item.comic-item")
    .mapNotNull { item ->
        val link = item.selectFirst("a[href]") ?: return@mapNotNull null
        SManga.create().apply {
            url = link.attr("href")
            title = item.selectFirst("p.title")?.text()
                ?: link.attr("title").substringBefore(',')
            thumbnail_url = item.selectFirst("img.img")?.absUrl("src")
        }
    }

/** 分类浏览每页条数（实测 48，末页 33 条、空页 0 条） */
private const val PAGE_SIZE = 48

/** 分类浏览按 48 条/页整页判定下一页（末页 33 条、空页 0 条实测收敛） */
private fun Document.hasNextPage(): Boolean = select("li.item.comic-item").size == PAGE_SIZE

/**
 * 自管 PHPSESSID：请求前回传已缓存的会话 Cookie，响应中捕获站点新下发的 PHPSESSID。
 * 站点（PHP 后端 + WAF）依赖会话连贯性；不带 Cookie 时每次响应都轮换新 ID，易被判定为异常客户端。
 */
private class SessionInterceptor(
    private val getSessionCookie: () -> String?,
    private val setSessionCookie: (String) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val cookie = getSessionCookie()
        val request = cookie?.let {
            chain.request().newBuilder().header("Cookie", it).build()
        } ?: chain.request()

        val response = chain.proceed(request)
        response.headers("Set-Cookie")
            .firstOrNull { it.startsWith("PHPSESSID=") }
            ?.let { setSessionCookie(it.substringBefore(';')) }
        return response
    }
}
