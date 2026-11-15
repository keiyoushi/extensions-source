package eu.kanade.tachiyomi.extension.zh.acgn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Acgn : KeiSource() {

    override val supportsLatest = false

    override val supportsRelatedMangas = true

    override val supportsFilterFetching = true

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) {
        it.host == baseHost && !it.encodedPath.startsWith("/logo/")
    }

    private var cseConfig: CseConfig? = null

    private var nextSearchStart: Int? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".tabs-panel li").map { element ->
            val link = element.selectFirst("div > a")!!
            SManga.create().apply {
                url = link.absUrl("href").toHttpUrl().pageId("manhua-")!!
                title = link.attrOrNull("title")!!
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return searchSite(page, query)

        val path = filters.firstInstanceOrNull<CategoryFilter>()?.path
            ?: filters.firstInstance<LetterFilter>().path
            ?: return getPopularManga(page)
        val listUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(path)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(listUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#display .content_block").map { element ->
            val link = element.selectFirst(".list_r h2 a")!!
            SManga.create().apply {
                url = link.absUrl("href").toHttpUrl().pageId("manhua-")!!
                title = link.attrOrNull("title")!!
                thumbnail_url = element.selectFirst("img.thumb")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".pager a[rel=next]") != null)
    }

    // The site's search box is a Google Programmable Search element
    private suspend fun searchSite(page: Int, query: String): MangasPage {
        val searchPage = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("searchcse")
            .addQueryParameter("q", query)
            .build()
        val config = cseConfig.takeIf { page > 1 } ?: fetchCseConfig(searchPage).also { cseConfig = it }
        var start = if (page == 1) 0 else nextSearchStart ?: return MangasPage(emptyList(), false)
        while (true) {
            val url = "https://cse.google.com/cse/element/v1".toHttpUrl().newBuilder()
                .addQueryParameter("rsz", "filtered_cse")
                .addQueryParameter("num", "20")
                .addQueryParameter("hl", "zh-TW")
                .addQueryParameter("source", "gcsc")
                .apply { if (start > 0) addQueryParameter("start", start.toString()) }
                .addQueryParameter("cselibv", config.libVersion)
                .addQueryParameter("cx", CSE_CX)
                .addQueryParameter("q", query)
                .addQueryParameter("safe", "off")
                .addQueryParameter("cse_tok", config.token)
                .addQueryParameter("sort", "")
                .addQueryParameter("exp", config.exp)
                .addQueryParameter("callback", CSE_CALLBACK)
                .addQueryParameter("rurl", searchPage.toString())
                .build()
            val result = client.get(url, Fetch.SCRIPT.from(searchPage)).parseAs<CseResultDto> {
                it.substringAfter("$CSE_CALLBACK(").substringBeforeLast(")")
            }
            if (result.error != null) throw Exception("Google search refused the request, search again")
            val mangas = result.results.mapNotNull { it.toSManga(baseHost) }.distinctBy { it.url }
            val next = result.cursor!!.pages.map { it.start.toInt() }.firstOrNull { it > start }
            // Pages holding only chapter hits would end the app's paging
            if (mangas.isNotEmpty() || next == null) {
                nextSearchStart = next
                return MangasPage(mangas, next != null)
            }
            start = next
        }
    }

    private suspend fun fetchCseConfig(searchPage: HttpUrl): CseConfig {
        val url = "https://cse.google.com/cse.js".toHttpUrl().newBuilder()
            .addQueryParameter("cx", CSE_CX)
            .build()
        val script = client.get(url, Fetch.SCRIPT.from(searchPage)).body.string()
        return CseConfig(
            token = CSE_TOKEN_REGEX.find(script)!!.groupValues[1],
            libVersion = CSE_LIB_VERSION_REGEX.find(script)!!.groupValues[1],
            exp = CSE_EXP_REGEX.find(script)!!.groupValues[1].parseAs<List<String>>().joinToString(","),
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manhua-${manga.url}.htm"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/view-${chapter.url}.htm"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost) return null
        val slug = url.pageId("manhua-")
            ?: url.pageId("view-")?.let { id ->
                client.get(getChapterUrl(SChapter.create().apply { this.url = id }), Fetch.DOCUMENT).asJsoup()
                    .selectFirst("#breadcrumb a[href*=/manhua-]")!!.absUrl("href").toHttpUrl().pageId("manhua-")!!
            }
            ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = document.toSManga(manga.url)
        // 最新收錄 dates the chapter it links
        val latest = document.field("最新收錄")
        val latestId = latest?.selectFirst("a")?.absUrl("href")?.toHttpUrl()?.pageId("view-")
        val latestDate = latest?.let { DATE_REGEX.find(it.text())?.value }
        val chapterList = document.select("#comic_chapter li a")
            .map { link ->
                SChapter.create().apply {
                    url = link.absUrl("href").toHttpUrl().pageId("view-")!!
                    name = link.text()
                    if (url == latestId) date_upload = dateFormat.tryParseDate(latestDate, siteZone)
                }
            }
            // The site's list interleaves batches on long series; ids follow upload order
            .sortedByDescending { it.url.toLong() }
        return SMangaUpdate(details, chapterList)
    }

    private fun Document.field(label: String) = select(".load01_r li.mss").firstOrNull { it.ownText().startsWith(label) }?.selectFirst("span")

    private fun Document.toSManga(slug: String): SManga {
        val title = selectFirst(".list_navbox h3 a[href*=/manhua-]")?.text()
            ?: throw Exception("This series was removed from the site")
        return SManga.create().apply {
            url = slug
            this.title = title
            author = field("漫畫作者")?.select("a")?.joinToString { it.text() }?.takeIf { it.isNotEmpty() }
            status = when (field("漫畫狀態")?.text()) {
                "連載中" -> SManga.ONGOING
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = listOfNotNull(field("漫畫類型")?.textOrNull())
                .plus(field("漫畫分類")?.select("a")?.map { it.text() }.orEmpty())
                .plus(field("漫畫標籤")?.text()?.split(",").orEmpty().map { it.trim() })
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()
                .takeIf { it.isNotEmpty() }
            // Synopses open with "　　<title>漫畫 ，"
            description = selectFirst(".gameshows dd:last-of-type")?.text()
                ?.trimStart('　')
                ?.removePrefix("${title}漫畫")
                ?.trimStart(' ', '，')
                ?.takeIf { it.isNotEmpty() }
            thumbnail_url = selectFirst(".gameshows img")?.absUrl("src")
        }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select(".danji li a[href*=/manhua-]").map { link ->
            SManga.create().apply {
                url = link.absUrl("href").toHttpUrl().pageId("manhua-")!!
                title = link.attrOrNull("title")!!
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("#pic_list .pic[_src]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("_src"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("#hd a[href^=/cate-]")
        .map { CategoryDto(it.attr("title").removeSuffix("漫畫"), it.absUrl("href").toHttpUrl().pathSegments.last()) }
        .distinctBy { it.path }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<CategoryDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                Filter.Header("搜尋文字時忽略篩選；分類優先於字母"),
                categories.takeIf { it.isNotEmpty() }?.let(::CategoryFilter),
                LetterFilter(),
            ),
        )
    }
}

/** The slug or id in `<prefix><id>.htm`, null for other pages. */
fun HttpUrl.pageId(prefix: String) = pathSegments.last()
    .takeIf { it.startsWith(prefix) && it.endsWith(".htm") }
    ?.removePrefix(prefix)
    ?.removeSuffix(".htm")

private const val CSE_CX = "008124722090293425135:k7bx4juliog"
private const val CSE_CALLBACK = "google.search.cse.api1"
private val CSE_TOKEN_REGEX = """"cse_token":\s*"([^"]+)"""".toRegex()
private val CSE_LIB_VERSION_REGEX = """"cselibVersion":\s*"([^"]+)"""".toRegex()
private val CSE_EXP_REGEX = """"exp":\s*(\[[^\]]*])""".toRegex()
private val DATE_REGEX = """\d{4}-\d{2}-\d{2}""".toRegex()
private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val siteZone = ZoneId.of("Asia/Taipei")
