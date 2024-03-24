package eu.kanade.tachiyomi.extension.all.unionmangas

import android.util.Log
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
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
    private val langOption: LanguageOption,
) : ParsedHttpSource() {
    override val lang = langOption.lang

    override val name: String = "Union Mangas"

    override val baseUrl: String = "https://unionmangas.xyz"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private fun apiHeaders(url: String): Headers {
        val date = SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date())
        val path = url.toUrlWithoutDomain()

        return headersBuilder()
            .add("_hash", authorization(apiSeed, domain, date))
            .add("_tranId", authorization(apiSeed, domain, date, path))
            .add("_date", date)
            .add("_domain", domain)
            .add("_path", path)
            .add("Origin", baseUrl)
            .add("Host", apiUrl.removeProtocol())
            .add("Referer", "$baseUrl/")
            .build()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var currentPage = 0
        do {
            val chaptersDto = fetchChapterListPageable(manga, currentPage)
            chapters += chaptersDto.toModel(langOption)
            currentPage++
        } while (chaptersDto.hasNextPage())
        return Observable.from(listOf(chapters.reversed()))
    }

    private fun fetchChapterListPageable(manga: SManga, page: Int): ChapterPageDto {
        return try {
            val maxResult = 16
            val pathSearch = when (langOption.lang) {
                "it" -> langOption.infix
                else -> "v3/po"
            }
            val url = "$apiUrl/api/$pathSearch/GetChapterListFilter/${manga.slug()}/$maxResult/$page/all/ASC"
            return client
                .newCall(GET(url, apiHeaders(url)))
                .execute()
                .toChapterPageDto()
        } catch (e: Exception) {
            Log.e("::fetchChapter", e.toString())
            ChapterPageDto()
        }
    }
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListSelector() = ""

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toLatestUpdatesModel(),
            hasNextPage = dto.hasNextPageToLatestUpdates(),
        )
    }

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = "#next-prev a:nth-child(2):not(.line-through)"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/${langOption.infix}/latest-releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "main > div > table > tbody > tr"

    override fun mangaDetailsParse(response: Response) =
        response.htmlDocumentToDto().toMangaDetailsModel()

    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val pageListData = document.htmlDocumentToDto().props.pageProps.pageListData
        val decodedData = CryptoAES.decrypt(pageListData!!, "ABC@123#245")
        val pagesData = json.decodeFromString<PageDataDto>(decodedData)
        return pagesData.data.getImages(langOption.pageDelimiter).mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toPopularMangaModel(),
            hasNextPage = dto.hasNextPageToPopularMangas(),
        )
    }

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/${langOption.infix}")

    override fun popularMangaSelector() = "main > div.pt-1 > a"

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val maxResult = 6
        val pathSearch = when (langOption.lang) {
            "it" -> langOption.infix
            else -> "v3/po"
        }
        val url = "$apiUrl/api/$pathSearch/searchforms/$maxResult/".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .addPathSegment("${page - 1}")
            .build()
        return GET(url, apiHeaders(url.toString()))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasDto = response.toMangaPageDto()
        return MangasPage(
            mangas = mangasDto.toModels(langOption.infix),
            hasNextPage = mangasDto.hasNextPage(),
        )
    }

    override fun searchMangaSelector() = ""

    private fun Response.htmlDocumentToDto(): UnionMangasDto {
        val jsonContent = asJsoup().selectFirst("script#__NEXT_DATA__")!!.html()
        return json.decodeFromString<UnionMangasDto>(jsonContent)
    }

    private fun Document.htmlDocumentToDto(): UnionMangasDto {
        val jsonContent = selectFirst("script#__NEXT_DATA__")!!.html()
        return json.decodeFromString<UnionMangasDto>(jsonContent)
    }

    private fun Response.toMangaPageDto() = json.decodeFromString<PageableMangaListDto>(body.string())

    private fun Response.toChapterPageDto() = json.decodeFromString<ChapterPageDto>(body.string())

    private fun String.removeProtocol() = trim().replace("https://", "")

    private fun SManga.slug() = this.url.split("/").last()

    private fun String.toUrlWithoutDomain() = trim().replace(apiUrl, "")

    private fun authorization(vararg payloads: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = payloads.joinToString("").toByteArray()
        val digest = md.digest(bytes)
        return digest
            .fold("") { str, byte -> str + "%02x".format(byte) }
            .padStart(32, '0')
    }

    companion object {
        val apiUrl = "https://api.unionmanga.xyz"
        val apiSeed = "8e0550790c94d6abc71d738959a88d209690dc86"
        val domain = "yaoi-chan.xyz"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}

class LanguageOption(val lang: String, val infix: String = lang, val chpPrefix: String, val pageDelimiter: String)

val languages = listOf(
    LanguageOption("it", "italy", "leer", ","),
    LanguageOption("pt-BR", "manga-br", "cap", "#"),
)
