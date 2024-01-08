package eu.kanade.tachiyomi.extension.en.warforrayuba

import android.os.Build
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.en.warforrayuba.dto.PageDto
import eu.kanade.tachiyomi.extension.en.warforrayuba.dto.RoundDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class WarForRayuba : HttpSource() {

    override val name = "War For Rayuba"

    override val baseUrl = "https://xrabohrok.github.io/WarMap/#/"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .build()

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    private val cubariHeaders = Headers.Builder().apply {
        add(
            "User-Agent",
            "(Android ${Build.VERSION.RELEASE}; " +
                "${Build.MANUFACTURER} ${Build.MODEL}) " +
                "Tachiyomi/${AppInfo.getVersionName()} " +
                Build.ID,
        )
    }.build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:86.0) Gecko/20100101 Firefox/86.0 ")
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int) = GET("https://github.com/xrabohrok/WarMap/tree/main/tools", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#repo-content-pjax-container .Details div[role=row] div[role=rowheader] a[href*='.json']").map { element ->
            SManga.create().apply {
                val githubRawUrl = "https://raw.githubusercontent.com/xrabohrok/WarMap/" + element.attr("abs:href").replace(".*(?=main)".toRegex(), "")
                val githubData: RoundDto = json.decodeFromString(
                    client.newCall(GET(githubRawUrl, headers)).execute().body.string(),
                )

                title = githubData.title
                thumbnail_url = githubData.cover
                url = githubRawUrl
            }
        }

        return MangasPage(mangas, false)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl, headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val githubData: RoundDto = json.decodeFromString(response.body.string())

        thumbnail_url = githubData.cover
        status = SManga.UNKNOWN
        author = githubData.author
        artist = githubData.artist
        title = githubData.title
        description = githubData.description
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseJson: RoundDto = json.decodeFromString(response.body.string())

        val chapterList: MutableList<SChapter> = ArrayList()
        responseJson.chapters.forEach { (number, chapter) ->
            chapterList.add(
                SChapter.create().apply {
                    url = "https://cubari.moe" + chapter.groups.primary
                    chapter_number = number.toFloat()
                    name = number.toString() + " " + chapter.title
                    date_upload = chapter.last_updated
                },
            )
        }

        return chapterList.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, cubariHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterData: List<PageDto> = json.decodeFromString(response.body.string())

        val pageList = chapterData.mapIndexed { index, page ->
            Page(index, page.src.slice(0..page.src.lastIndexOf(".")), page.src)
        }

        return pageList
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.just(MangasPage(emptyList(), false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not Used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not Used")
}
