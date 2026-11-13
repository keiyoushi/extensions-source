package eu.kanade.tachiyomi.extension.zh.longjinman

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

@Source
abstract class Longjinman : KeiSource() {

    // Every page lists the site's backup domains in its footer
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("#friendlink a[href^=https://]")
        .map { it.absUrl("href") }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl("bookcata", "all", "hits", "all", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl("bookcata", "all", "time", "all", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        val status = filters.firstInstance<StatusFilter>().value
        val url = if (query.isNotBlank()) {
            listUrl("search", query.trim(), sort, status, page)
        } else {
            listUrl("bookcata", filters.firstInstance<CategoryFilter>().value, sort, status, page)
        }
        return getMangaList(url)
    }

    private fun listUrl(path: String, key: String, sort: String, status: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .addPathSegment(key)
        .addPathSegments("ob/$sort/st/$status/page/$page")
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a.module-poster-item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".module-poster-item-title")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("data-original")
            }
        }
        return MangasPage(mangas, document.selectFirst("#page a[title=下一页]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && !MIRROR_HOST.matches(url.host)) return null
        val segments = url.pathSegments
        if (segments.firstOrNull() != "manhua") return null
        val id = segments.getOrNull(1)?.removeSuffix(".html")?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/manhua/$id.html" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    private fun SManga.parseDetails(document: Document) {
        val info = document.selectFirst(".module-info-main")!!
        val items = info.select(".module-info-item")
        fun item(label: String) = items.firstOrNull { it.selectFirst(".module-info-item-title")?.text() == label }
            ?.selectFirst(".module-info-item-content")

        title = info.selectFirst(".module-info-heading h1")!!.text().removePrefix("《").removeSuffix("》")
        thumbnail_url = document.selectFirst(".module-info-poster img")?.absUrl("data-original")
        // 不知名作者 is the site's placeholder for an unknown author
        author = item("作者：")?.select("a")?.map { it.text() }?.filter { it != "不知名作者" }?.joinToString()?.ifEmpty { null }
        genre = info.select(".module-info-tag-link a").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst(".module-info-introduction-content"))
            .build()
        status = when (item("更新：")?.ownText()?.substringBefore(' ')) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        update_strategy = if (status == SManga.COMPLETED) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("a.module-play-list-link").asReversed().map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.absUrl("href"))
            // Chapter titles are HTML-escaped twice
            name = Parser.unescapeEntities(it.text(), false)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("#m_r_imgbox_0 img[data-src]").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("搜索关键词时分类无效"),
        CategoryFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val MIRROR_HOST = Regex("""(?:\d*longjm|long\d+)\.[a-z]+""")
    }
}
