package eu.kanade.tachiyomi.extension.pt.argoscomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException

class ArgosComics : HttpSource() {

    override val name: String = "Argos Comics"

    override val baseUrl: String = "https://aniargos.com"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    override val versionId: Int = 2

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 2)
        .build()

    private val popularToken: String by lazy {
        getActionNextToken(getScriptUrlFrom("$baseUrl/projetos", "script[src*=projetos]"))
    }

    private val latestToken: String by lazy {
        getActionNextToken(getScriptUrlFrom(baseUrl, "script[src*=page-]"))
    }

    private val searchToken: String by lazy {
        val document = client.newCall(GET("$baseUrl/projetos")).execute().asJsoup()
        val urls = document.select("script[async]:not([src*=app]):not([src*=webpack])")
            .map { it.absUrl("src") }
            .sortedDescending()

        urls.forEach {
            try {
                return@lazy getActionNextToken(it)
            } catch (_: Exception) { /* ignored */ }
        }

        throw IOException("Não foi possível encontrar token para pesquisar")
    }

    private var detailsToken: String? = null
    private fun getDetailsToken(pageUrl: String): String = detailsToken.takeIf { !it.isNullOrBlank() }
        ?: getActionNextToken(getScriptUrlFrom(pageUrl, "script[src*=projectId]")).also { detailsToken = it }

    private var chaptersToken: String? = null
    private fun getChaptersToken(pageUrl: String): String = chaptersToken.takeIf { !it.isNullOrBlank() }
        ?: getActionNextToken(getScriptUrlFrom(pageUrl, "script[src*=projectId]"), CHAPTERS_TOKEN_REGEX).also { chaptersToken = it }

    private var pagesToken: String? = null
    private fun getPagesToken(pageUrl: String): String = pagesToken.takeIf { !it.isNullOrBlank() }
        ?: getActionNextToken(getScriptUrlFrom(pageUrl, "script[src*=capitulo]")).also { pagesToken = it }

    // ======================== Popular =============================

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val popularHeaders = headers.newBuilder()
            .set("Next-Action", popularToken)
            .build()

        val payload = listOf(page)
            .toJsonString()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        return POST(url.toString(), popularHeaders, payload)
    }

    override fun popularMangaParse(response: Response): MangasPage = getJsonBody(response).parseAs<MangasListDto>().toMangasPage()

    // ======================== Latest =============================

    override fun latestUpdatesRequest(page: Int): Request {
        val latestHeaders = headers.newBuilder()
            .set("Next-Action", latestToken)
            .build()

        val payload = emptyList<String>()
            .toJsonString()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        return POST(baseUrl, latestHeaders, payload)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = getJsonBody(response).parseAs<List<MangaDto>>()
        return MangasPage(dto.map(MangaDto::toSManga), false)
    }

    // ======================== Search =============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val payload = listOf(query)
            .toJsonString()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        val searchHeaders = headers.newBuilder()
            .set("Next-Action", searchToken)
            .build()

        return POST(url.toString(), searchHeaders, payload)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ======================== Details =============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val payload = getMangaUrl(manga).toHttpUrl().pathSegments
            .toJsonString()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        val detailsHeaders = headers.newBuilder()
            .set("Next-Action", getDetailsToken(getMangaUrl(manga)))
            .build()

        return POST(getMangaUrl(manga), detailsHeaders, payload)
    }

    override fun mangaDetailsParse(response: Response) = getJsonBody(response).parseAs<MangaDetailsDto>().toSManga()

    // ======================== Chapters =============================

    override fun chapterListRequest(manga: SManga): Request {
        val chaptersHeaders = headers.newBuilder()
            .set("Next-Action", getChaptersToken(getMangaUrl(manga)))
            .build()

        return mangaDetailsRequest(manga).newBuilder()
            .headers(chaptersHeaders)
            .build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pathSegment = response.request.url.toString().substringAfter(baseUrl)
        return getJsonBody(response).parseAs<VolumeChapterDto>().toChapterList(pathSegment)
    }

    // ======================== Pages =============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = getChapterUrl(chapter).toHttpUrl().pathSegments
        val payload = buildList {
            add(segments.first())
            add(segments.last())
        }.toJsonString().toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        val pagesHeaders = headers.newBuilder()
            .set("Next-Action", getPagesToken(getChapterUrl(chapter)))
            .build()

        return POST(getChapterUrl(chapter), pagesHeaders, payload)
    }

    override fun pageListParse(response: Response): List<Page> = getJsonBody(response).parseAs<PagesDto>().toPageList()

    override fun imageUrlParse(response: Response) = ""

    // ======================== Utils =============================

    private fun getActionNextToken(url: String?, regex: Regex = NEXT_ACTION_REGEX): String {
        url ?: throw IOException("Url não encontrada")
        val script = client.newCall(GET(url, headers))
            .execute().body.string()
        return regex.find(script)?.groupValues?.last(String::isNotBlank)
            ?: throw IOException("Não foi possível encontrar token")
    }

    private fun getJsonBody(response: Response): String = MANGA_DATA_REGEX.find(response.body.string())?.groupValues?.last(String::isNotBlank)
        ?: throw IOException("Não foi possível encontrar a lista de mangás")

    private fun getScriptUrlFrom(url: String, cssSelector: String): String? {
        val document = client.newCall(GET(url)).execute().asJsoup()
        return document.selectFirst(cssSelector)?.absUrl("src")
    }

    companion object {
        private val TEXT_PLAIN_MEDIA_TYPE = "text/plain;charset=UTF-8".toMediaTypeOrNull()

        //  Use isolated regex for chapters, as the  manga details token is in the same script as the chapters token.
        private val CHAPTERS_TOKEN_REGEX = """createServerReference[^"]*"([^"]+)"[^)]*getAllChapters""".toRegex()
        private val NEXT_ACTION_REGEX =
            """createServerReference[^"]*"([^"]+)"[^)]*(?:getAllWithoutFilters|getLastUpdates|search|getOne|getPages)""".toRegex()

        /**
         * The purpose is to find the beginning of the valid JSON data, ignoring any
         * preceding code or text. The pattern is defined by the expected first key-field
         * of the JSON for each response type:
         *
         * - 'id': Starts the list of latest projects or search results (e.g., [{"id"...).
         * - 'projects', 'title', 'groupName', 'pages': Starts other responses (e.g., {"projects"...).
         */
        private val MANGA_DATA_REGEX = buildList {
            add("""\[\{"id".+""".toRegex())
            add("""\{"(?:projects|title|groupName|pages)".+""".toRegex())
        }.joinToString("|").toRegex()
    }
}
