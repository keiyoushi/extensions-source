package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.extension.th.nekopost.model.LatestRequest
import eu.kanade.tachiyomi.extension.th.nekopost.model.PagingInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.PopularRequest
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawChapterInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSearchSummaryList
import eu.kanade.tachiyomi.extension.th.nekopost.model.SearchRequest
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Nekopost : HttpSource() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override val baseUrl = "https://www.nekopost.net"
    override val lang = "th"
    override val name = "Nekopost"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val projectDataEndpoint = "https://api.osemocphoto.com/frontAPI/getProjectInfo"
    private val fileHost = "https://www.osemocphoto.com"

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("th")) }

    private val commonHeaders by lazy { headersBuilder().build() }

    private val apiHeaders by lazy {
        headersBuilder().set("Accept", "*/*").set("Content-Type", "application/json").build()
    }

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private fun getStatus(status: String) =
        when (status) {
            "1" -> SManga.ONGOING
            "2" -> SManga.COMPLETED
            "3" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }

    override fun latestUpdatesRequest(page: Int) =
        projectRequest(
            "latest",
            LatestRequest(0, PagingInfo(page, PAGE_SIZE)),
        )

    override fun popularMangaRequest(page: Int) =
        projectRequest(
            "list/popular",
            PopularRequest("mc", PagingInfo(page, PAGE_SIZE)),
        )

    private inline fun <reified T> projectRequest(endpoint: String, body: T): Request {
        return POST(
            "$baseUrl/api/project/$endpoint",
            apiHeaders,
            json.encodeToString(body).toRequestBody(),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseProjectList(response, filterTypes = setOf("m", "c"), hasNextPage = true)

    override fun popularMangaParse(response: Response): MangasPage =
        parseProjectList(response, filterTypes = setOf("m", "c"), hasNextPage = true)

    override fun searchMangaParse(response: Response): MangasPage =
        parseProjectList(response, filterTypes = setOf("m"), hasNextPage = false)

    private fun parseProjectList(
        response: Response,
        filterTypes: Set<String>,
        hasNextPage: Boolean,
    ): MangasPage {
        val projectList: RawProjectSearchSummaryList = json.decodeFromString(response.body.string())

        if (projectList.listProject.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            projectList
                .listProject
                .asSequence()
                .filter { it.projectType in filterTypes }
                .map { project ->
                    SManga.create().apply {
                        url = project.pid.toString()
                        title = project.projectName
                        status = project.status
                        thumbnail_url =
                            buildCoverUrl(project.pid.toString(), project.coverVersion)
                        initialized = false
                    }
                }
                .toList()

        return MangasPage(mangaList, hasNextPage)
    }

    private fun buildCoverUrl(projectId: String, coverVersion: Int? = null): String {
        val base = "$fileHost/collectManga/$projectId/${projectId}_cover.jpg"
        return if (coverVersion != null) "$base?ver=$coverVersion" else base
    }

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$projectDataEndpoint/${manga.url}", commonHeaders)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val projectInfo: RawProjectInfo = json.decodeFromString(response.body.string())

        return SManga.create().apply {
            projectInfo.projectInfo.let { info ->
                url = info.projectId
                title = info.projectName
                artist = info.artistName
                author = info.authorName
                description = info.info
                status = getStatus(info.status)
                thumbnail_url = buildCoverUrl(info.projectId)
                initialized = true
            }
            genre =
                projectInfo
                    .projectCategoryUsed
                    ?.joinToString(", ") { it.categoryName }
                    .orEmpty()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val headers =
            Headers.headersOf(
                "accept",
                "*/*",
                "content-type",
                "text/plain;charset=UTF-8",
                "origin",
                baseUrl,
            )
        return GET("$projectDataEndpoint/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val projectInfo: RawProjectInfo = json.decodeFromString(response.body.string())

        if (getStatus(projectInfo.projectInfo.status) == SManga.LICENSED) {
            throw Exception("Licensed - No chapter to show")
        }

        val projectId = projectInfo.projectInfo.projectId.toInt()

        return projectInfo.projectChapterList?.map { chapter ->
            SChapter.create().apply {
                url = "$projectId/${chapter.chapterId}/${projectId}_${chapter.chapterId}.json"
                name = chapter.chapterName
                date_upload = dateFormat.parse(chapter.createDate)?.time ?: 0L
                chapter_number = chapter.chapterNo.toFloat()
                scanlator = chapter.providerName
            }
        }
            ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter) =
        GET("$fileHost/collectManga/${chapter.url}", commonHeaders)

    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl/manga/${chapter.url.substringBefore("/")}/${chapter.chapter_number.toString().removeSuffix(".0")}"

    override fun pageListParse(response: Response): List<Page> {
        val chapterInfo: RawChapterInfo = json.decodeFromString(response.body.string())
        val basePath = "$fileHost/collectManga/${chapterInfo.projectId}/${chapterInfo.chapterId}"

        return chapterInfo.pageItem.map { page ->
            Page(
                index = page.pageNo,
                imageUrl = "$basePath/${page.pageName ?: page.fileName}",
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchData =
            SearchRequest(
                keyword = query,
                status = 0,
                paging = PagingInfo(pageNo = page, pageSize = 100),
            )
        return POST(
            "$baseUrl/api/project/search",
            apiHeaders,
            json.encodeToString(searchData).toRequestBody(),
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()

    companion object {
        private const val PAGE_SIZE = 30
    }
}
