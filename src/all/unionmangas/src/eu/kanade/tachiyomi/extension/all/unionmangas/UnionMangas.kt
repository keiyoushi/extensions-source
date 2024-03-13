package eu.kanade.tachiyomi.extension.all.unionmangas

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class UnionMangas(
    override val lang: String,
    private val siteLang: String,
) : ParsedHttpSource() {

    override val name: String = "Union Mangas"

    override val baseUrl: String = "https://unionmangas.xyz"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(LangGroupFilter(getLangFilter()))

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var currentPage = 0
        do {
            val chaptersDto = fetchChapterListPageable(manga, currentPage)
            chapters += chaptersDto?.toModel() ?: emptyList()
            currentPage++
        } while (currentPage <= (chaptersDto?.totalPage ?: 0))
        return Observable.from(listOf(chapters))
    }

    private fun fetchChapterListPageable(manga: SManga, page: Int): ChapterPageDto? {
        return try {
            val date = SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }
                .format(Date())

            val url = "$apiUrl/api/v3/po/GetChapterListFilter/${manga.slug()}/16/$page/all/ASC"
            val path = url.toUrlWithoutDomain()

            val headers = headersBuilder()
                .add("_hash", buildHashRequest(apiSeed + domain + date))
                .add("_tranId", buildHashRequest(apiSeed + domain + date + path))
                .add("_date", date)
                .add("_domain", domain)
                .add("_path", path)
                .add("Origin", baseUrl)
                .add("Host", apiUrl.removeProtocol())
                .add("Referer", "$baseUrl/")
                .build()

            return client
                .newCall(GET(url, headers))
                .execute().body.toChapterDto()
        } catch (e: Exception) {
            Log.e("::fetchChapter", e.toString())
            null
        }
    }

    private fun buildHashRequest(payload: String): String = payload.MD5()

    private fun String.MD5(): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = trim().toByteArray()
        val digest = md.digest(bytes)
        return digest
            .fold("") { str, byte -> str + "%02x".format(byte) }
            .padStart(32, '0')
    }

    override fun chapterListSelector(): String = ""

    override fun imageUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toLatestUpdatesModel(),
            hasNextPage = dto.hasNextPageToLatestUpdates(),
        )
    }

    override fun latestUpdatesNextPageSelector() = "#next-prev a:nth-child(2):not(.line-through)"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/$siteLang/latest-releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "main > div > table > tbody > tr"

    override fun mangaDetailsParse(response: Response) =
        response.htmlDocumentToDto().toMangaDetailsModel()

    override fun pageListParse(document: Document): List<Page> {
        TODO("Not yet implemented")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toPopularMangaModel(),
            hasNextPage = dto.hasNextPageToPopularMangas(),
        )
    }

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$siteLang")

    override fun popularMangaSelector() = "main > div.pt-1 > a"

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }

    private fun getLangFilter() = listOf(
        CheckboxFilterOption("it", "Italian"),
        CheckboxFilterOption("pt_br", "Portuguese (Brazil)"),

    ).filterNot { it.value == siteLang }

    private fun Response.htmlDocumentToDto(): UnionMangasDto {
        val jsonContent = asJsoup().selectFirst("script#__NEXT_DATA__")!!.html()
        return json.decodeFromString<UnionMangasDto>(jsonContent)
    }

    private fun ResponseBody.toChapterDto() =
        json.decodeFromString<ChapterPageDto>(string())

    private fun String.removeProtocol() = trim().replace("https://", "")

    private fun SManga.slug() = this.url.split("/").last()

    private fun String.toUrlWithoutDomain() = trim().replace(apiUrl, "")

    companion object {
        val apiUrl = "https://api.unionmanga.xyz"
        val apiSeed = "8e0550790c94d6abc71d738959a88d209690dc86"
        val domain = "yaoi-chan.xyz"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    }
}

class CheckboxFilterOption(val value: String, name: String, default: Boolean = false) : Filter.CheckBox(name, default)

abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : Filter.Group<CheckboxFilterOption>(name, options) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }
}

class LangGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Languages", options)
