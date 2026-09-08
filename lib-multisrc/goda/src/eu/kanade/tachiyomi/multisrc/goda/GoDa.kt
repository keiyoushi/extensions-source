package eu.kanade.tachiyomi.multisrc.goda

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities

abstract class GoDa : KeiSource() {

    open val enableGenres = true

    // Popular + latest
    open fun popularMangaUrl(page: Int) = "$baseUrl/hots/page/$page"

    override suspend fun getPopularManga(page: Int) = parsePopularMangas(client.get(popularMangaUrl(page)))

    open fun latestUpdatesUrl(page: Int) = "$baseUrl/newss/page/$page"

    override suspend fun getLatestUpdates(page: Int) = parseLatestMangas(client.get(latestUpdatesUrl(page)))

    open fun parsePopularMangas(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".container > .cardlist .pb-2 a").map { element ->
            SManga.create().apply {
                val imgSrc = element.selectFirst("img")!!.attr("src")
                url = getKey(element.attr("href"))
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = if ("url=" in imgSrc) imgSrc.toHttpUrl().queryParameter("url")!! else imgSrc
            }
        }
        val nextPage = if (lang == "zh") "下一頁" else "NEXT"
        val hasNextPage = document.selectFirst("a[aria-label=$nextPage] button") != null
        return MangasPage(mangas, hasNextPage)
    }

    open fun parseLatestMangas(response: Response) = parsePopularMangas(response)

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/s".toHttpUrl().newBuilder()
                .addPathSegment(query)
                .addEncodedQueryParameter("page", "$page")
                .build().toString()
        } else {
            var filterUrl = popularMangaUrl(page)
            for (filter in filters) {
                if (filter is UriPartFilter) filterUrl = baseUrl + filter.toUriPart() + "/page/$page"
            }
            filterUrl
        }
        return parseSearchManga(client.get(url))
    }

    open fun parseSearchManga(response: Response) = parsePopularMangas(response)

    // Details + Chapters
    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.memo.getStringOrNull("id")

        return if (fetchChapters && mangaId != null) {
            coroutineScope {
                val deferredChapters = async { fetchChapterList(mangaId) }
                val deferredManga = async { if (fetchDetails) getMangaDetails(manga) else manga }

                SMangaUpdate(deferredManga.await(), deferredChapters.await())
            }
        } else {
            val updatedManga = if (mangaId == null || fetchDetails) getMangaDetails(manga) else manga

            val updatedChapters = if (fetchChapters) {
                val mangaId = updatedManga.memo.getString("id")
                fetchChapterList(mangaId)
            } else {
                chapters
            }
            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    open suspend fun getMangaDetails(manga: SManga) = parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup())

    open fun parseMangaDetails(document: Document) = SManga.create().apply {
        val mangaId = getMangaId(document)
        val document = document.selectFirst("main")!!
        val titleElement = document.selectFirst("h1")!!
        val elements = titleElement.parent()!!.parent()!!.children()
        check(elements[4].tagName() == "p")

        title = titleElement.ownText()
        status = when (titleElement.child(0).text()) {
            "連載中", "Ongoing" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            "停止更新" -> SManga.CANCELLED
            "休刊" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        author = Entities.unescape(elements[1].children().drop(1).joinToString { it.text().removeSuffix(" ,") })
        genre = buildList {
            elements[2].children().drop(1).mapTo(this) { it.text().removeSuffix(" ,") }
            elements[3].children().mapTo(this) { it.text().removePrefix("#") }
        }.joinToString()
        description = (elements[4].text() + "\n\nID: $mangaId").trim()
        thumbnail_url = document.selectFirst("img.object-cover")!!.attr("src")

        memo = buildJsonObject {
            put("id", mangaId)
        }
    }

    open suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val response = client.get("$baseUrl/manga/get?mid=$mangaId&mode=all")
        val document = response.asJsoup()

        return document.select(".chapteritem").asReversed().map { element ->
            val anchor = element.selectFirst("a")!!
            SChapter.create().apply {
                url = getKey(anchor.attr("href")) + "#$mangaId/" + anchor.attr("data-cs")
                name = anchor.attr("data-ct")
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/" + chapter.url.substringBeforeLast('#')

    // Pages
    private fun pageListUrl(chapter: SChapter): String {
        val id = chapter.url.substringAfterLast('#', "")
        val mangaId = id.substringBefore('/', "")
        val chapterId = id.substringAfter('/', "")
        if (mangaId.isEmpty() || chapterId.isEmpty()) error(if (lang == "zh") "请刷新漫画" else "Refresh manga")
        return pageListUrl(mangaId, chapterId)
    }

    open fun pageListUrl(mangaId: String, chapterId: String) = "$baseUrl/chapter/getcontent?m=$mangaId&c=$chapterId"

    override suspend fun getPageList(chapter: SChapter) = parsePageList(client.get(pageListUrl(chapter)))

    open fun parsePageList(response: Response) = response.asJsoup().select("#chapcontent > div > img").mapIndexed { index, element ->
        Page(index, imageUrl = element.attr("data-src").ifEmpty { element.attr("src") })
    }

    // Filters
    override val supportsFilterFetching get() = enableGenres

    open fun parseGenres(document: Document): Map<String, String> {
        val box = document.selectFirst("h2")?.parent()?.parent() ?: return emptyMap()
        val items = box.select("a")
        return items.associate { it.text().removePrefix("#") to it.attr("href") }
    }

    open val genresUrl = popularMangaUrl(1)

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get(genresUrl)
        return parseGenres(response.asJsoup()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            val genres = data?.parseAs<Map<String, String>>() ?: emptyMap()

            if (enableGenres && !genres.isEmpty()) {
                add(Filter.Header(if (lang == "zh") "分类（搜索文本时无效）" else "Filters are ignored when using text search."))
                add(UriPartFilter(if (lang == "zh") "分类" else "Genre", genres.toList()))
            }
        },
    )

    class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // Utils
    open fun getKey(link: String): String = link.substringAfter("/manga/").removeSuffix("/")

    open fun getMangaId(doc: Element) = doc.selectFirst("#mangachapters")!!.attr("data-mid")
}
