package eu.kanade.tachiyomi.extension.en.explosm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Explosm : HttpSource() {

    override val name = "Cyanide & Happiness"

    override val baseUrl = "https://explosm.net"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val archivePage = "$baseUrl/comics"

    private fun getArchiveAllYears(response: Response): JsonObject {
        val jsonPath = response.asJsoup()
            .select("head > script").last()?.attr("src")
            ?.replace("static", "data")
            ?.replaceAfterLast("/", "comics.json")
            ?: throw Exception("Error at last() in getArchiveAllYears")
        val json = client.newCall(GET(baseUrl + jsonPath, headers)).execute().body.string()
        return Json.decodeFromString<JsonObject>(json)["pageProps"]
            ?.jsonObject?.get("comicArchiveData")
            ?.jsonObject
            ?: throw Exception("Error while returning getArchiveAllYears")
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return (GET(archivePage, headers))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val eachYearAsAManga = getArchiveAllYears(response)
            .map { year ->
                SManga.create().apply {
                    initialized = true
                    title = "C&H " + year.key // year
                    url = year.key // need key here
                    thumbnail_url = "https://vhx.imgix.net/vitalyuncensored/assets/13ea3806-5ebf-4987-bcf1-82af2b689f77/S2E4_Still1.jpg"
                    author = "Explosm.net"
                }
            }
            .reversed()

        return MangasPage(eachYearAsAManga, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    // for webview
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/comics#${manga.url}-01")
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // Chapters

    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun JsonElement?.getContent(key: String): String {
        return this?.jsonObject?.get(key)?.jsonPrimitive?.content ?: throw Exception("Error getting chapter content from $key")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var chapterCount = 0F
        return client.newCall(GET(archivePage, headers))
            .asObservableSuccess()
            .map { response ->
                getArchiveAllYears(response)[manga.url]?.jsonObject
                    ?.map { month ->
                        month.value.jsonArray.map { comic ->
                            chapterCount++
                            SChapter.create().apply {
                                name = comic.getContent("slug")
                                // we get the url for page.imageurl here
                                url = if (comic.getContent("file_static") != "null") {
                                    comic.getContent("file_static")
                                } else {
                                    "https://files.explosm.net/comics/${comic.getContent("file")}"
                                }
                                date_upload = date.parse(comic.getContent("publish_at"))?.time ?: 0L
                                scanlator = comic.getContent("author_name")
                                chapter_number = chapterCount // so no "missing chapters" warning in app
                            }
                        }
                    }
                    ?.flatten()
                    ?.reversed()
                    ?: throw Exception("Error with main jsonObject")
            }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, "", chapter.url)))
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
