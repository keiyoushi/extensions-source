package eu.kanade.tachiyomi.extension.en.milftoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
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

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("views", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("latest", page)

    private suspend fun getMangaList(orderBy: String, page: Int): MangasPage {
        val url = "$baseUrl/manga_list/all".toHttpUrl().newBuilder()
            .addQueryParameter("orderby", orderBy)
            .addQueryParameter("page", page.toString())
            .build()
        val html = client.get(url, Fetch.DOCUMENT).asJsoup().html()
        return parseList(html)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getLatestUpdates(page)
        val url = "$baseUrl/search_result".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        val html = client.get(url, Fetch.DOCUMENT).asJsoup().html()
        return parseList(html)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "comics") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/comics/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val route = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().html().parseRouteData<DetailWrapper>()
        val comic = route.auxiliary
        val details = SManga.create().apply {
            url = manga.url
            title = comic.title
            author = comic.author
            description = MarkdownDescription.Builder()
                .description(comic.synopsisHtml?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
                .build()
            genre = (comic.genres + comic.tags).map { it.name }.distinct().joinToString().ifEmpty { null }
            status = when (comic.status) {
                "ongoing" -> SManga.ONGOING
                "end" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = comic.coverPath?.let { cdnUrl + it }
        }
        // Keeps the bare comic url for this chapter so library entries saved before chapter urls still match
        val landingSlug = route.data.chapter?.slug
        val chapterList = comic.chapters.map { chapter ->
            SChapter.create().apply {
                name = listOfNotNull(chapter.name, chapter.nameExtend).filter { it.isNotBlank() }.joinToString(" ")
                url = if (chapter.slug == landingSlug) manga.url else "${manga.url}/${chapter.slug}"
                date_upload = Instant.tryParse(chapter.publishedAt)
            }
        }.asReversed()
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup().html().parseRouteData<PageWrapper>().data
        return data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.src)
        }
    }
}
