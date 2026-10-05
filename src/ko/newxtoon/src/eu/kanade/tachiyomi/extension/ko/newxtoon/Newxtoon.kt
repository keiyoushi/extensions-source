package eu.kanade.tachiyomi.extension.ko.newxtoon

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Newxtoon : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getComicList(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComicList(page, "latest")

    private suspend fun getComicList(page: Int, sort: String): MangasPage {
        val url = "$baseUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .build()
        return parseComicList(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
        } else {
            "$baseUrl/comics".toHttpUrl().newBuilder().apply {
                filters.filterIsInstance<UriPartFilter>().forEach { filter ->
                    filter.toUriPart()?.let { addQueryParameter(filter.param, it) }
                }
            }
        }
            .addQueryParameter("page", page.toString())
            .build()
        return parseComicList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.getOrNull(0) != "comics") return null
        val id = segments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return parseMangaDetails(manga, client.get(getMangaUrl(manga)).asJsoup())
    }

    private fun parseComicList(document: Document): MangasPage {
        val mangas = document.select("a.comic-link[href*=/comics/]:not([data-cover-ad])").map { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img.cover-image")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("nav[data-site-pagination] a[aria-label=다음 페이지]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) parseMangaDetails(manga, client.get(getMangaUrl(manga)).asJsoup()) else manga }
        val chapterList = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    private fun parseMangaDetails(manga: SManga, document: Document): SManga {
        val info = document.selectFirst("section[aria-labelledby=comic-title]")!!
        return SManga.create().apply {
            url = manga.url
            title = info.selectFirst("#comic-title")!!.text()
            thumbnail_url = info.selectFirst("img[alt$=표지]")?.absUrl("src")
            author = info.select("#comic-title + p a").joinToString { it.text() }.ifEmpty { null }
            description = info.selectFirst("[data-comic-description]")?.wholeText()?.trim()
            genre = info.select("a[href*=/comics?category=]").joinToString { it.text() }.ifEmpty { null }
            status = when {
                info.selectFirst("strong:containsOwn(완결)") != null -> SManga.COMPLETED
                info.selectFirst("strong:containsOwn(연재중)") != null -> SManga.ONGOING
                info.selectFirst("strong:containsOwn(휴재)") != null -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val url = "$baseUrl/comics/${manga.url}/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "latest")
                .addQueryParameter("page", page.toString())
                .build()
            val response = client.get(url, jsonHeaders).parseAs<ChapterListDto>()
            response.chapters.mapTo(chapters) { it.toSChapter(manga.url) }
            page++
        } while (response.hasMore)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("[data-reader-canvas] [data-reader-page] img[data-reader-image]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("검색어 입력 시 필터는 무시됩니다"),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        PlatformFilter(),
        StatusFilter(),
        WeekdayFilter(),
    )

    private val jsonHeaders: Headers
        get() = headersBuilder()
            .set("Accept", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
}
