package eu.kanade.tachiyomi.extension.en.loadingartist

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class LoadingArtist : HttpSource() {
    override val name = "Loading Artist"

    override val baseUrl = "https://loadingartist.com"

    override val lang = "en"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    @Serializable
    private data class Comic(
        val url: String,
        val title: String,
        val date: String = "",
        val section: String,
    )

    // Popular Section (list of comic archives by year)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(
            MangasPage(
                listOf(
                    SManga.create().apply {
                        title = "Loading Artist"
                        setUrlWithoutDomain("/archives")
                        thumbnail_url = "$baseUrl/img/bg/logo-text_dark.png"
                        artist = "Loading Artist"
                        author = artist
                        status = SManga.ONGOING
                    },
                ),
                false,
            ),
        )
    }

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")
    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        throw Exception("Search not available for this source")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(GET("$baseUrl/search.json", headers))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response)
            }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comics = json.parseToJsonElement(response.body.string()).jsonObject.map {
            json.decodeFromJsonElement<Comic>(it.value)
        }
        val validTypes = listOf("comic", "game", "art")
        return comics.filter { validTypes.any { type -> it.section == type } }.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.url)
                name = it.title
                date_upload = try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(it.date)?.time ?: 0
                } catch (_: ParseException) {
                    0
                }
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val imageUrl = response.asJsoup().selectFirst("div.main-image-container img")!!
            .attr("abs:src")
        return listOf(Page(0, response.request.url.toString(), imageUrl))
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")
    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")
}
