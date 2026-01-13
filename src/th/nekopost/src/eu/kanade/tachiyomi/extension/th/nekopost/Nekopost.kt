package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.extension.th.nekopost.model.LatestRequest
import eu.kanade.tachiyomi.extension.th.nekopost.model.PagingInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.PopularRequest
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawChapterInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawLatestChapterList
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSearchSummaryList
import eu.kanade.tachiyomi.extension.th.nekopost.model.SearchRequest
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Nekopost : HttpSource() {

    override val baseUrl = "https://www.nekopost.net"
    override val lang = "th"
    override val name = "Nekopost"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val projectDataEndpoint = "https://api.osemocphoto.com/frontAPI/getProjectInfo"
    private val fileHost = "https://www.osemocphoto.com"

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("th")) }

    private val apiHeaders by lazy {
        headersBuilder()
            .set("Accept", "*/*")
            .set("Content-Type", "application/json")
            .build()
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
            LatestRequest("m", PagingInfo(page, PAGE_SIZE)),
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
            Json.encodeToString(body).toRequestBody(),
        )
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val cleanQuery = query.trim()
        val projectIdRegex = Regex("""nekopost\.net/manga/(\d+)""")
        val matchResult = projectIdRegex.find(cleanQuery)

        return if (matchResult != null) {
            val projectId = matchResult.groupValues[1]
            client.newCall(GET("$projectDataEndpoint/$projectId", headers))
                .asObservableSuccess()
                .map { response ->
                    MangasPage(listOf(mangaDetailsParse(response)), false)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseLatestChapterList(response)

    override fun popularMangaParse(response: Response): MangasPage =
        parseProjectList(response, filterTypes = null, hasNextPage = true)

    override fun searchMangaParse(response: Response): MangasPage =
        parseProjectList(response, filterTypes = setOf("m"), hasNextPage = false)

    private fun parseLatestChapterList(response: Response): MangasPage {
        val chapterList = response.parseAs<RawLatestChapterList>()

        if (chapterList.listChapter.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            chapterList
                .listChapter
                .asSequence()
                .map { chapter ->
                    SManga.create().apply {
                        url = chapter.pid.toString()
                        title = chapter.projectName
                        status = getStatus(chapter.status)
                        thumbnail_url =
                            buildCoverUrl(
                                chapter.pid.toString(),
                                chapter.coverVersion,
                            )
                        initialized = false
                    }
                }
                .toList()

        return MangasPage(
            mangaList,
            mangaList.isNotEmpty() && mangaList.size >= PAGE_SIZE,
        )
    }

    private fun parseProjectList(
        response: Response,
        filterTypes: Set<String>?,
        hasNextPage: Boolean,
    ): MangasPage {
        val projectList = response.parseAs<RawProjectSearchSummaryList>()

        if (projectList.listProject.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            projectList
                .listProject
                .asSequence()
                .filter { filterTypes == null || it.projectType in filterTypes }
                .map { project ->
                    SManga.create().apply {
                        url = project.pid.toString()
                        title = project.projectName
                        status = project.status
                        thumbnail_url =
                            buildCoverUrl(
                                project.pid.toString(),
                                project.coverVersion,
                            )
                        initialized = false
                    }
                }
                .toList()

        return MangasPage(
            mangaList,
            hasNextPage && mangaList.isNotEmpty() && mangaList.size >= PAGE_SIZE,
        )
    }

    private fun buildCoverUrl(projectId: String, coverVersion: Int? = null): String {
        val base = "$fileHost/collectManga/$projectId/${projectId}_cover.jpg"
        return if (coverVersion != null) "$base?ver=$coverVersion" else base
    }

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$projectDataEndpoint/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val projectInfo = response.parseAs<RawProjectInfo>()

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

    override fun chapterListRequest(manga: SManga) =
        GET("$projectDataEndpoint/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val projectInfo = response.parseAs<RawProjectInfo>()

        if (getStatus(projectInfo.projectInfo.status) == SManga.LICENSED) {
            throw Exception("Licensed - No chapter to show")
        }

        val projectId = projectInfo.projectInfo.projectId.toInt()

        return projectInfo.projectChapterList?.map { chapter ->
            SChapter.create().apply {
                url =
                    "$projectId/${chapter.chapterId}/${projectId}_${chapter.chapterId}.json"
                name = chapter.chapterName
                date_upload = dateFormat.parse(chapter.createDate)?.time ?: 0L
                chapter_number = chapter.chapterNo.toFloat()
                scanlator = chapter.providerName
            }
        }
            ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter) =
        GET("$fileHost/collectManga/${chapter.url}", headers)

    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl/manga/${chapter.url.substringBefore("/")}/${chapter.chapter_number.toString().removeSuffix(".0")}"

    override fun pageListParse(response: Response): List<Page> {
        val chapterInfo = response.parseAs<RawChapterInfo>()
        val basePath =
            "$fileHost/collectManga/${chapterInfo.projectId}/${chapterInfo.chapterId}"

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
                keyword = query.trim(),
                status = 0,
                paging = PagingInfo(pageNo = page, pageSize = 100),
            )
        return POST(
            "$baseUrl/api/project/search",
            apiHeaders,
            Json.encodeToString(searchData).toRequestBody(),
        )
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()

    companion object {
        private const val PAGE_SIZE = 20
    }
}
