package eu.kanade.tachiyomi.extension.all.unionmangas

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
            chapters += chaptersDto.data.map { chapter ->
                SChapter.create().apply {
                    name = chapter.name
                    date_upload = chapter.date.toDate()
                    url = "/${langOption.infix}${chapter.toChapterUrl(langOption.infix)}"
                }
            }
            currentPage++
        } while (chaptersDto.hasNextPage())
        return Observable.just(chapters)
    }

    private fun fetchChapterListPageable(manga: SManga, page: Int): Pageable1<List<ChapterDto>> {
        val maxResult = 16
        val url = "$newApiUrl/${langOption.infix}/GetChapterListFilter/${manga.slug()}/$maxResult/$page/all/ASC"
        return client.newCall(GET(url, headers)).execute()
            .parseAs<Pageable1<List<ChapterDto>>>()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(emptyList(), false)
//        val nextData = response.parseNextData<LatestUpdateProps>()
//        val dto = nextData.data.latestUpdateDto
//        val mangas = dto.mangas.map { mangaParse(it, "nextData.query") }
//
//        return MangasPage(
//            mangas = mangas,
//            hasNextPage = dto.hasNextPage(),
//        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/${langOption.infix}/latest-releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val nextData = response.parseAs<Pageable1<MangaDetailsProps>>()
        return mangaParse(nextData.data.mangaDetailsDto)
    }

    override fun pageListParse(response: Response): List<Page> {
        return emptyList()
//        val chaptersDto = decryptChapters(response)
//        return chaptersDto.images.mapIndexed { index, imageUrl ->
//            Page(index, imageUrl = imageUrl)
//        }
    }

//    private fun decryptChapters(response: Response): ChaptersDto {
//        val document = response.asJsoup()
//        val password = findChapterPassword(document)
//        val pageListData = document.parseNextData<ChaptersProps>().data.pageListData
//        val decodedData = CryptoAES.decrypt(pageListData, password)
//        return ChaptersDto(
//            data = json.decodeFromString<ChaptersDto>(decodedData).data,
//            delimiter = langOption.pageDelimiter,
//        )
//    }

//    private fun findChapterPassword(document: Document): String {
//        val regxPasswordUrl = """\/pages\/%5Btype%5D\/%5Bidmanga%5D\/%5Biddetail%5D-.+\.js""".toRegex()
//        val regxFindPassword = """AES\.decrypt\(\w+,"(?<password>[^"]+)"\)""".toRegex(RegexOption.MULTILINE)
//        val jsDecryptUrl = document.select("script")
//            .map { it.absUrl("src") }
//            .first { regxPasswordUrl.find(it) != null }
//        val jsDecrypt = client.newCall(GET(jsDecryptUrl, headers)).execute().asJsoup().html()
//        return regxFindPassword.find(jsDecrypt)?.groups?.get("password")!!.value.trim()
//    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<Pageable1<List<MangaDto>>>()
        val mangas = dto.data.map(::mangaParse)
        return MangasPage(
            mangas = mangas,
            hasNextPage = dto.hasNextPage(),
        )
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$newApiUrl/${langOption.infix}/HomeTopFllow/24/${page - 1}")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val maxResult = 6
        val url = "$apiUrl/api/${langOption.infix}/searchforms/$maxResult/".toHttpUrl().newBuilder()
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
        return latestUpdatesParse(response)
//        val mangasDto = response.parseAs<MangaListDto>().apply {
//            currentPage = response.request.url.pathSegments.last()
//        }
//
//        return MangasPage(
//            mangas = mangasDto.toSManga(langOption.infix),
//            hasNextPage = mangasDto.hasNextPage(),
//        )
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

    private fun mangaParse(dto: MangaDto): SManga {
        return SManga.create().apply {
            title = dto.title
            thumbnail_url = dto.thumbnailUrl
            status = dto.status
            url = "/${langOption.infix}/${dto.slug}"
            genre = dto.genres
            initialized = true
        }
    }

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    companion object {
        const val SEARCH_PREFIX = "slug:"
        val apiUrl = "https://api.unionmanga.xyz"
        val newApiUrl = "https://app.unionmanga.xyz/api"
        val apiSeed = "8e0550790c94d6abc71d738959a88d209690dc86"
        val domain = "yaoi-chan.xyz"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ENGLISH)
        val apiDateFormat = SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
    }
}
