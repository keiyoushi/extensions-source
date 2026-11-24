package eu.kanade.tachiyomi.extension.zh.manwame

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Manwame : KeiSource() {

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        val host = baseUrl.toHttpUrl().host
        rateLimit(15, 1.minutes, 1500.milliseconds) { it.host == host && it.encodedPath == "/search" }
        rateLimit(2) { it.host == host }
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(OrderFilter()))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(OrderFilter(1)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val order = filters.firstInstance<OrderFilter>().value
        if (query.isBlank() && order.startsWith(RANKING_PREFIX)) {
            val mangas = client.get("$baseUrl/$order", Fetch.DOCUMENT).asJsoup()
                .select(".rank-line .manga-line")
                .map { it.toSManga() }
                // The site's boards can list a title twice
                .distinctBy { it.url }
            return MangasPage(mangas, false)
        }
        val url = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("format", "json")
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            "$baseUrl/category".toHttpUrl().newBuilder().apply {
                filters.filterIsInstance<PathFilter>()
                    .filter { it.value.isNotEmpty() }
                    .forEach { addPathSegment(it.key).addPathSegment(it.value) }
                addPathSegment("page").addPathSegment(page.toString())
                addQueryParameter("format", "json")
            }.build()
        }
        val listing = client.get(url, headers.newBuilder().set("X-Requested-With", "XMLHttpRequest").build(), Fetch.XHR).parseAs<ListingDto>()
        return MangasPage(listing.rows.map { it.toSManga() }, listing.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "book") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/book/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga().apply { this.url = manga.url }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val profile = document.selectFirst(".comic-profile")!!
        title = profile.selectFirst("h1")!!.text()
        thumbnail_url = profile.selectFirst(".profile-cover img")?.absUrl("src")
        author = profile.info("作者")?.takeIf { it != "未知" }
        genre = (listOfNotNull(profile.info("地区")) + document.select(".tag-strip a").map { it.text() })
            .joinToString().ifEmpty { null }
        status = when (profile.info("状态")) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            "已下架" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".intro-text"))
            .build()
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        val updated = DATE_FORMAT.tryParseDateTime(document.selectFirst(".comic-profile")!!.info("更新"), ZONE)
        return document.select("[data-chapter-list] a").asReversed().mapIndexed { index, element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
                if (index == 0) date_upload = updated
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).body.string()
        // A JS string literal with \u002b and \/ escapes
        val params = Base64.decode("\"${PARAMS_REGEX.find(html)!!.groupValues[1]}\"".parseAs<String>(), Base64.DEFAULT)
        val data = params.copyOfRange(16, params.size)
            .aesDecrypt(aesCbc(PARAMS_KEY, params.copyOfRange(0, 16)))
            .decodeToString()
            .parseAs<ChapterDataDto>()
        return data.images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/category", Fetch.DOCUMENT).asJsoup()
        .select(".filter-line:has(> span:containsOwn(题材)) a[href*=/theme/]")
        .map { it.text() to it.absUrl("href").toHttpUrl().pathSegments.last() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(Filter.Header("文字搜索时忽略以下筛选，排行榜忽略其他筛选"))
            add(AreaFilter())
            data?.parseAs<List<Pair<String, String>>>()?.let { add(ThemeFilter(it)) }
            add(StateFilter())
            add(OrderFilter())
        },
    )

    private fun Element.info(label: String) = select("p").firstOrNull { it.ownText().startsWith("$label：") }
        ?.text()?.substringAfter("：")?.ifEmpty { null }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    companion object {
        private val PARAMS_REGEX = Regex("""params = '([^']+)'""")
        private val PARAMS_KEY = "5V&RoR%Jf@pJPydF".encodeToByteArray()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Shanghai")
    }
}
