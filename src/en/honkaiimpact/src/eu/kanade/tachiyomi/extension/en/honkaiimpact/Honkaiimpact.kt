package eu.kanade.tachiyomi.extension.en.honkaiimpact

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.concurrent.TimeUnit

class Honkaiimpact : HttpSource() {

    override val name = "Honkai Impact 3rd"
    override val baseUrl = "https://manga.honkaiimpact3.com"
    override val lang = "en"
    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/book", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("a[href*=book]").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/book", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters))
        .asObservableSuccess()
        .map { response ->
            val mangas = response.asJsoup()
                .select("a[href*=book]")
                .map { it.toSManga() }
                .filter { manga ->
                    query.isEmpty() || manga.title.contains(query.trim(), ignoreCase = true)
                }
            MangasPage(mangas, false)
        }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Manga details
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            thumbnail_url = document.select("img.cover").attr("abs:src")
            description = document.select("div.detail_info1").text()
            title = document.select("div.title").text()
        }
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "/get_chapter", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<Dto>>().map { it.toSChapter() }

    // Page list
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("img.lazy.comic_img").mapIndexed { i, el ->
        Page(i, imageUrl = el.attr("data-original"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun org.jsoup.nodes.Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(attr("abs:href"))
        title = select(".container-title").text()
        thumbnail_url = select(".container-cover img").attr("abs:src")
    }
}
