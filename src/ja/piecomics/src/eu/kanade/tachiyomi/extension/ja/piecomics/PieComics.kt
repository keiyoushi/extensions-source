package eu.kanade.tachiyomi.extension.ja.piecomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class PieComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(SpeedBinbInterceptor())
    }

    override val supportsLatest get() = false

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "", "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty()
        val order = filters.firstInstanceOrNull<OrderFilter>()?.value.orEmpty()
        if (query.isBlank()) return getMangaList(page, status, order)

        // The site's search (admin-ajax.php) answers 403, and its manga list is only two pages long
        val mangas = buildList {
            var listPage = 1
            do {
                val result = getMangaList(listPage++, status, order)
                addAll(result.mangas)
            } while (result.hasNextPage)
        }
        val terms = query.normalized()
        return MangasPage(mangas.filter { terms in it.title.normalized() || terms in it.author.orEmpty().normalized() }, false)
    }

    private suspend fun getMangaList(page: Int, status: String, order: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("comicart/manga/")
            .apply { if (page > 1) addPathSegments("page/$page/") }
            .apply { if (status.isNotEmpty()) addQueryParameter("sort", status) }
            .apply { if (order.isNotEmpty()) addQueryParameter("order", order) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("section.p-categoryBlock_item").map { it.toSManga() }
        // The pager's links can carry the tracking parameters of the page cache's first visitor: only their presence counts
        val hasNextPage = document.selectFirst(".wp-pagenavi .current + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga() = SManga.create().apply {
        url = seriesPath(selectFirst(".p-categoryBox_image a")!!.absUrl("href").toHttpUrl())!!
        title = selectFirst(".p-categoryBox_title")!!.text()
        author = selectFirst(".p-categoryBox_author")?.textOrNull()
        thumbnail_url = selectFirst(".p-categoryBox_image img")?.absUrl("src")
        // Series pages have no status: only the list's labels give it
        val labels = select(".p-categoryBox_labelList a").map { it.text() }
        status = when {
            "完結" in labels -> SManga.COMPLETED
            "連載中" in labels -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val seriesUrl = when (url.host) {
            baseUrl.toHttpUrl().host -> url
            // An episode's page links back to its series on the site
            in YONDEMILL_HOSTS -> {
                if (url.pathSegments.firstOrNull() != "contents") return null
                val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                client.get("$YONDEMILL_URL/contents/$id", Fetch.DOCUMENT).asJsoup()
                    .selectFirst("a[href^=$baseUrl/series/]")?.absUrl("href")?.toHttpUrl()
            }
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = seriesPath(seriesUrl) ?: return null }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun seriesPath(url: HttpUrl): String? {
        if (url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return "/series/$slug/"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toMangaDetails(manga), parseChapters(document))
    }

    private fun Document.toMangaDetails(manga: SManga) = SManga.create().apply {
        url = manga.url
        title = selectFirst("h1.p-work_title")!!.text()
        author = selectFirst(".p-work_author")?.textOrNull()
        thumbnail_url = selectFirst(".p-work_image img")?.absUrl("src")
        // Tags repeat the title and the author's name
        genre = select(".p-work_tags li").map { it.text().removePrefix("#").trim() }
            .filter { it.isNotEmpty() && it != title && it != author }
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".p-work_desc"))
            .field("編集部コメント", selectFirst(".p-work_commentBody")?.textOrNull())
            .build()
        status = manga.status
        initialized = true
    }

    // Other entries are sold on Amazon, or are posts of illustrated prose on the site
    private fun parseChapters(document: Document): List<SChapter> = document.select("ol.p-series_list a.p-series_link")
        .mapNotNull { link ->
            val url = link.absUrl("href").toHttpUrl()
            if (url.host !in YONDEMILL_HOSTS || url.pathSegments.firstOrNull() != "contents") return@mapNotNull null
            SChapter.create().apply {
                this.url = "/contents/${url.pathSegments[1]}"
                name = link.selectFirst(".p-series_itemTitle")!!.text().removePrefix("【無料】").trim()
                date_upload = DATE_FORMAT.tryParseDate(link.selectFirst("time")?.text()?.removeSuffix("更新"), TOKYO)
            }
        }
        // Some series list their first episodes twice
        .distinctBy { it.url }

    override fun getChapterUrl(chapter: SChapter): String = "$YONDEMILL_URL${chapter.url}?view=1"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // The episode's page sends readers to the SpeedBinb reader with a new session token each time
        val redirect = client.get(getChapterUrl(chapter), Fetch.DOCUMENT, CacheControl.FORCE_NETWORK).body.string()
        val readerUrl = READER_URL_REGEX.find(redirect)!!.groupValues[1]
        val content = client.get(readerUrl, Fetch.DOCUMENT.from(getChapterUrl(chapter)), CacheControl.FORCE_NETWORK).asJsoup()
            .selectFirst("#content")!!
        return client.fetchPages(content.absUrl("data-ptbinb").toHttpUrl(), content.attr("data-ptbinb-cid"))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
        OrderFilter(),
    )

    private fun String.normalized() = replace(WHITESPACE_REGEX, "").lowercase()

    companion object {
        private const val YONDEMILL_URL = "https://www.yondemill.jp"
        private val YONDEMILL_HOSTS = setOf("www.yondemill.jp", "yondemill.jp")
        private val READER_URL_REGEX = Regex("""location\.href\s*=\s*'([^']+)'""")
        private val WHITESPACE_REGEX = Regex("""[\s\u3000]+""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val TOKYO = ZoneId.of("Asia/Tokyo")
    }
}
