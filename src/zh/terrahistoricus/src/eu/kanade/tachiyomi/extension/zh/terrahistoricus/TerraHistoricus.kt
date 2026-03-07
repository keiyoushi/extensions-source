package eu.kanade.tachiyomi.extension.zh.terrahistoricus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class TerraHistoricus : HttpSource() {
    override val name = "泰拉记事社"
    override val lang = "zh"
    override val baseUrl = "https://comic.hypergryph.com"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val topicKeys = listOf("terra-historicus", "talos-ii-historicus")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/comic?topicKey=${topicKeys[0]}", headers)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val requests = topicKeys.map { client.newCall(GET("$baseUrl/api/comic?topicKey=$it", headers)) }
        return Observable.from(requests)
            .flatMap { it.asObservableSuccess() }
            .toList()
            .map { responses ->
                val mangas = responses.flatMap { it.parseAs<List<THComic>>().map { comic -> comic.toSManga() } }
                MangasPage(mangas, false)
            }
    }

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/recentUpdate?topicKey=${topicKeys[0]}", headers)

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val requests = topicKeys.map { client.newCall(GET("$baseUrl/api/recentUpdate?topicKey=$it", headers)) }
        return Observable.from(requests)
            .flatMap { it.asObservableSuccess() }
            .toList()
            .map { responses ->
                val mangas = responses.flatMap { it.parseAs<List<THRecentUpdate>>().map { update -> update.toSManga() } }
                MangasPage(mangas, false)
            }
    }

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(page).map { mangasPage ->
        val mangas = mangasPage.mangas.filter { it.title.contains(query) }
        MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // navigate webview to webpage
    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url.replace("/api/comic/", "/terra-historicus/"), headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(chapterListRequest(manga)).asObservableSuccess()
        .map { response -> mangaDetailsParse(response).apply { initialized = true } }

    override fun mangaDetailsParse(response: Response) = response.parseAs<THComic>().toSManga()

    override fun chapterListParse(response: Response) = response.parseAs<THComic>().toSChapterList()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservableSuccess().map { response ->
        (0 until response.parseAs<THEpisode>().pageInfos!!.size).map {
            Page(it, "$baseUrl${chapter.url}/page?pageNum=${it + 1}")
        }
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = response.parseAs<THPage>().url

    private inline fun <reified T> Response.parseAs() = json.decodeFromString<THResult<T>>(this.body.string()).data
}
