package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstance
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLEncoder

@Source
abstract class HComic : HttpSource() {
    override val supportsLatest = true

    private val imgUrl = "https://h-comic.link/api"

    // Popular (Random)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/random/__data.json")

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAsMangaList(0)
        return MangasPage(result.first.map { it.toSManga(imgUrl) }, true)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/__data.json?page=$page")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")!!.toInt()
        val result = response.parseAsMangaList(page)
        return MangasPage(result.first.map { it.toSManga(imgUrl) }, result.second)
    }

    // Search

    override fun getFilterList() = FilterList(RandomFilter(), TagGroup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tags = filters.firstInstance<TagGroup>().state.filter { it.state }.joinToString(",") { it.value }
        val url = "${baseUrl + filters[0]}/__data.json".toHttpUrl().newBuilder()
        url.addQueryParameter("tag", tags).addQueryParameter("q", query).addQueryParameter("page", page.toString())
        return GET(url.build())
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Manga Detail

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${URLEncoder.encode(manga.title, "UTF-8")}/1"

    override fun mangaDetailsRequest(manga: SManga) = GET("${getMangaUrl(manga)}/__data.json")

    override fun mangaDetailsParse(response: Response) = response.parseAsManga().toSManga(imgUrl)

    // Chapters

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comics/${URLEncoder.encode(chapter.name, "UTF-8")}/1"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val info = manga.url.split('|')
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = info.first()
                    name = manga.title
                    date_upload = info.last().substringBefore(':').toLong() * 1000L
                    scanlator = info.last().substringAfter(':')
                },
            ),
        )
    }

    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val info = chapter.url.split(':')
        return Observable.just(
            List(info.last().toInt()) {
                Page(it, imageUrl = "$imgUrl/${info.first()}/pages/${it + 1}")
            },
        )
    }

    override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
