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
    override val baseUrl = "https://terra-historicus.hypergryph.com"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/comic", headers)
    override fun popularMangaParse(response: Response) =
        MangasPage(response.parseAs<List<THComic>>().map { it.toSManga() }, false)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/recentUpdate", headers)
    override fun latestUpdatesParse(response: Response) =
        MangasPage(response.parseAs<List<THRecentUpdate>>().map { it.toSManga() }, false)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        fetchPopularManga(page).map { mangasPage ->
            val mangas = mangasPage.mangas.filter { it.title.contains(query) }
            MangasPage(mangas, false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not used.")
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Not used.")

    // navigate webview to webpage
    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url.removePrefix("/api"), headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { response -> mangaDetailsParse(response).apply { initialized = true } }

    override fun mangaDetailsParse(response: Response) = response.parseAs<THComic>().toSManga()

    override fun chapterListParse(response: Response) = response.parseAs<THComic>().toSChapterList()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess().map { response ->
            (0 until response.parseAs<THEpisode>().pageInfos!!.size).map {
                Page(it, "$baseUrl${chapter.url}/page?pageNum=${it + 1}")
            }
        }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response) = response.parseAs<THPage>().url

    private inline fun <reified T> Response.parseAs() =
        json.decodeFromString<THResult<T>>(this.body.string()).data
}
