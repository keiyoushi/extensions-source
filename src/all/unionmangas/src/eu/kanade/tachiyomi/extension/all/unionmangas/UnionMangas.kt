package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class UnionMangas(private val langOption: LanguageOption) : HttpSource() {
    override val lang = langOption.lang

    override val name: String = "Union Mangas"

    override val baseUrl: String = "https://unionmangasbr.org"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    val langApiInfix = when (lang) {
        "it" -> langOption.infix
        else -> "v3/po"
    }

    override val client = network.client.newBuilder()
        .rateLimit(5, 2, TimeUnit.SECONDS)
        .build()

    private fun apiHeaders(url: String): Headers {
        val date = apiDateFormat.format(Date())
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

    private fun authorization(vararg payloads: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = payloads.joinToString("").toByteArray()
        val digest = md.digest(bytes)
        return digest
            .fold("") { str, byte -> str + "%02x".format(byte) }
            .padStart(32, '0')
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var currentPage = 0
        do {
            val chaptersDto = fetchChapterListPageable(manga, currentPage)
            chapters += chaptersDto.toSChapter(langOption)
            currentPage++
        } while (chaptersDto.hasNextPage())
        return Observable.just(chapters.reversed())
    }

    private fun fetchChapterListPageable(manga: SManga, page: Int): ChapterPageDto {
        val maxResult = 16
        val url = "$apiUrl/api/$langApiInfix/GetChapterListFilter/${manga.slug()}/$maxResult/$page/all/ASC"
        return client.newCall(GET(url, apiHeaders(url))).execute()
            .parseAs<ChapterPageDto>()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val nextData = response.parseNextData<LatestUpdateProps>()
        val dto = nextData.data.latestUpdateDto
        val mangas = dto.mangas.map { mangaParse(it, nextData.query) }

        return MangasPage(
            mangas = mangas,
            hasNextPage = dto.hasNextPage(),
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/${langOption.infix}/latest-releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val nextData = response.parseNextData<MangaDetailsProps>()
        val dto = nextData.data.mangaDetailsDto
        return SManga.create().apply {
            title = dto.title
            genre = dto.genres
            thumbnail_url = dto.thumbnailUrl
            url = mangaUrlParse(dto.slug, nextData.query.type)
            status = dto.status
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val chaptersDto = decryptChapters(response)
        return chaptersDto.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun decryptChapters(response: Response): ChaptersDto {
        val document = response.asJsoup()
        val password = findChapterPassword(document)
        val pageListData = document.parseNextData<ChaptersProps>().data.pageListData
        val decodedData = CryptoAES.decrypt(pageListData, password)
        return ChaptersDto(
            data = json.decodeFromString<ChaptersDto>(decodedData).data,
            delimiter = langOption.pageDelimiter,
        )
    }

    private fun findChapterPassword(document: Document): String {
        val regxPasswordUrl = """\/pages\/%5Btype%5D\/%5Bidmanga%5D\/%5Biddetail%5D-.+\.js""".toRegex()
        val regxFindPassword = """AES\.decrypt\(\w+,"(?<password>[^"]+)"\)""".toRegex(RegexOption.MULTILINE)
        val jsDecryptUrl = document.select("script")
            .map { it.absUrl("src") }
            .first { regxPasswordUrl.find(it) != null }
        val jsDecrypt = client.newCall(GET(jsDecryptUrl, headers)).execute().asJsoup().html()
        return regxFindPassword.find(jsDecrypt)?.groups?.get("password")!!.value.trim()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseNextData<PopularMangaProps>()
        val mangas = dto.data.mangas.map { it.details }.map { mangaParse(it, dto.query) }
        return MangasPage(
            mangas = mangas,
            hasNextPage = false,
        )
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/${langOption.infix}")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val maxResult = 6
        val url = "$apiUrl/api/$langApiInfix/searchforms/$maxResult/".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .addPathSegment("${page - 1}")
            .build()
        return GET(url, apiHeaders(url.toString()))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX)) {
            val url = "$baseUrl/${langOption.infix}/${query.substringAfter(SEARCH_PREFIX)}"
            return client.newCall(GET(url, headers))
                .asObservableSuccess().map { response ->
                    val mangas = try { listOf(mangaDetailsParse(response)) } catch (_: Exception) { emptyList() }
                    MangasPage(mangas, false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasDto = response.parseAs<MangaListDto>().apply {
            currentPage = response.request.url.pathSegments.last()
        }

        return MangasPage(
            mangas = mangasDto.toSManga(langOption.infix),
            hasNextPage = mangasDto.hasNextPage(),
        )
    }

    private inline fun <reified T> Response.parseNextData() = asJsoup().parseNextData<T>()

    private inline fun <reified T> Document.parseNextData(): NextData<T> {
        val jsonContent = selectFirst("script#__NEXT_DATA__")!!.html()
        return json.decodeFromString<NextData<T>>(jsonContent)
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun String.removeProtocol() = trim().replace("https://", "")

    private fun SManga.slug() = this.url.split("/").last()

    private fun String.toUrlWithoutDomain() = trim().replace(apiUrl, "")

    private fun mangaParse(dto: MangaDto, query: QueryDto): SManga {
        return SManga.create().apply {
            title = dto.title
            thumbnail_url = dto.thumbnailUrl
            status = dto.status
            url = mangaUrlParse(dto.slug, query.type)
            genre = dto.genres
        }
    }

    private fun mangaUrlParse(slug: String, pathSegment: String) = "/$pathSegment/$slug"

    companion object {
        const val SEARCH_PREFIX = "slug:"
        val apiUrl = "https://api.unionmanga.xyz"
        val apiSeed = "8e0550790c94d6abc71d738959a88d209690dc86"
        val domain = "yaoi-chan.xyz"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val apiDateFormat = SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
    }
}
