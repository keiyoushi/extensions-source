package eu.kanade.tachiyomi.extension.en.milftoon

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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class Milftoon : KeiSource() {
    private val cdnUrl = "https://fdc1.azhentai.com/"

    private inline fun <reified T> String.parseRouteData(): T {
        val start = indexOf("window.__INITIAL_ROUTE_DATA__=")
            .takeIf { it >= 0 }
            ?.let { indexOf('{', it) }
            ?.takeIf { it >= 0 }
            ?: throw Exception("Route data not found")
        var depth = 0
        var inString = false
        var escaped = false
        var end = -1
        for (i in start until length) {
            val c = this[i]
            when {
                inString && escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                inString && c == '"' -> inString = false
                !inString && c == '"' -> inString = true
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }
        if (end < 0) throw Exception("Route data not found")
        return substring(start, end).parseAs<T>()
    }

    private fun parseList(html: String): MangasPage {
        val data = html.parseRouteData<RouteWrapper>().data
        val mangas = data.items.map { item ->
            SManga.create().apply {
                title = item.title
                author = item.author
                setUrlWithoutDomain("/comics/${item.slug}")
                thumbnail_url = item.coverPath?.let { cdnUrl + it }
            }
        }
        val hasNext = data.page * data.pageSize < data.total
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val html = client.get("$baseUrl/ranking/hottest?page=$page").asJsoup().html()
        return parseList(html)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val html = client.get("$baseUrl/manga_list/all?page=$page").asJsoup().html()
        return parseList(html)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getLatestUpdates(page)
        val url = "$baseUrl/search_result".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        val html = client.get(url).asJsoup().html()
        return parseList(html)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        val data = document.html().parseRouteData<DetailWrapper>().data
        val details = SManga.create().apply {
            url = manga.url
            title = data.comic.title
            author = manga.author
            description = document.selectFirst("meta[name=description]")?.attr("content")
            thumbnail_url = data.comic.coverPath?.let { cdnUrl + it }
            initialized = true
        }
        val chapterList = data.chapter?.let { chapter ->
            listOf(
                SChapter.create().apply {
                    name = chapter.name.ifBlank { "Chapter" }
                    setUrlWithoutDomain(manga.url)
                    date_upload = Instant.tryParse(chapter.publishedAt)
                },
            )
        }.orEmpty()
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(baseUrl + chapter.url).asJsoup().html().parseRouteData<DetailWrapper>().data
        return data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.src)
        }
    }
}
