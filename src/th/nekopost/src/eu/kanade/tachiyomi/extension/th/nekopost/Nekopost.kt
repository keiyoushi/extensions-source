package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.extension.th.nekopost.model.EditorProject
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

    private fun getStatus(status: String) = when (status) {
        "1" -> SManga.ONGOING
        "2" -> SManga.COMPLETED
        "3" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    override fun latestUpdatesRequest(page: Int) = projectRequest(
        "latest",
        LatestRequest("m", PagingInfo(page, LATEST_PAGE_SIZE)),
    )

    override fun popularMangaRequest(page: Int) = projectRequest(
        "list/popular",
        PopularRequest("mc", PagingInfo(1, POPULAR_PAGE_SIZE)),
    )

    private inline fun <reified T> projectRequest(endpoint: String, body: T): Request = POST(
        "$baseUrl/api/project/$endpoint",
        apiHeaders,
        Json.encodeToString(body).toRequestBody(),
    )

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val cleanQuery = query.trim()

        val projectMatch = Regex("""nekopost\.net/manga/(\d+)""").find(cleanQuery)

        val editorMatch = Regex("""nekopost\.net/editor/(\d+)""").find(cleanQuery)

        return when {
            projectMatch != null -> {
                val projectId = projectMatch.groupValues[1]
                client.newCall(GET("$projectDataEndpoint/$projectId", headers))
                    .asObservableSuccess()
                    .map { response ->
                        if (response.peekBody(1024)
                                .string()
                                .contains("\"projectInfo\":null")
                        ) {
                            MangasPage(emptyList(), false)
                        } else {
                            val projectInfo =
                                response.parseAs<RawProjectInfo>()
                            MangasPage(
                                listOf(
                                    mangaFromProjectInfo(
                                        projectInfo,
                                    ),
                                ),
                                false,
                            )
                        }
                    }
            }

            editorMatch != null -> {
                val editorId = editorMatch.groupValues[1]

                client.newCall(
                    GET(
                        "$baseUrl/api/editor/project/$editorId",
                        apiHeaders,
                    ),
                )
                    .asObservableSuccess()
                    .map { response -> parseEditorProjectList(response) }
            }

            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    private fun mangaFromProjectInfo(info: RawProjectInfo): SManga = SManga.create().apply {
        val p = info.projectInfo
        url = p.projectId
        title = p.projectName
        artist = p.artistName
        author = p.authorName
        description = p.info
        status = getStatus(p.status)
        thumbnail_url = buildCoverUrl(p.projectId)
        genre =
            info.projectCategoryUsed
                ?.joinToString(", ") { it.categoryName }
                .orEmpty()
        initialized = true
    }

    private fun parseEditorProjectList(response: Response): MangasPage {
        val list =
            response.parseAs<List<EditorProject>?>()
                ?: return MangasPage(emptyList(), false)

        val mangaList =
            list.filter { it.projectType == "m" }.map { project ->
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

        return MangasPage(mangaList, false)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val chapterList = response.parseAs<RawLatestChapterList>()

        if (chapterList.listChapter.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            chapterList.listChapter.map {
                SManga.create().apply {
                    url = it.pid.toString()
                    title = it.projectName
                    status = getStatus(it.status)
                    thumbnail_url =
                        buildCoverUrl(it.pid.toString(), it.coverVersion)
                    initialized = false
                }
            }

        return MangasPage(
            mangaList,
            mangaList.size == LATEST_PAGE_SIZE,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = parseProjectList(response, null, false)

    override fun searchMangaParse(response: Response): MangasPage = parseProjectList(response, setOf("m"), true)

    private fun parseProjectList(
        response: Response,
        filterTypes: Set<String>?,
        isPaginated: Boolean,
    ): MangasPage {
        val projectList = response.parseAs<RawProjectSearchSummaryList>()

        if (projectList.listProject.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            projectList.listProject
                .filter { filterTypes == null || it.projectType in filterTypes }
                .map {
                    SManga.create().apply {
                        url = it.pid.toString()
                        title = it.projectName
                        status = it.status
                        thumbnail_url =
                            buildCoverUrl(
                                it.pid.toString(),
                                it.coverVersion,
                            )
                        initialized = false
                    }
                }

        return MangasPage(
            mangaList,
            isPaginated && mangaList.size == SEARCH_PAGE_SIZE,
        )
    }

    private fun buildCoverUrl(projectId: String, coverVersion: Int? = null): String {
        val base = "$fileHost/collectManga/$projectId/${projectId}_cover.jpg"
        return if (coverVersion != null) "$base?ver=$coverVersion" else base
    }

    override fun mangaDetailsRequest(manga: SManga) = GET("$projectDataEndpoint/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = mangaFromProjectInfo(response.parseAs())

    override fun chapterListRequest(manga: SManga) = GET("$projectDataEndpoint/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val projectInfo = response.parseAs<RawProjectInfo>()

        if (getStatus(projectInfo.projectInfo.status) == SManga.LICENSED) {
            throw Exception("Licensed")
        }

        val projectId = projectInfo.projectInfo.projectId.toInt()

        return projectInfo.projectChapterList.orEmpty().map {
            SChapter.create().apply {
                url = "$projectId/${it.chapterId}/${projectId}_${it.chapterId}.json"
                name = it.chapterName
                chapter_number = it.chapterNo.toFloat()
                date_upload = dateFormat.parse(it.createDate)?.time ?: 0L
                scanlator = it.providerName
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = GET("$fileHost/collectManga/${chapter.url}", headers)

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url.substringBefore("/")}/${chapter.chapter_number.toString().removeSuffix(".0")}"

    override fun pageListParse(response: Response): List<Page> {
        val info = response.parseAs<RawChapterInfo>()
        val base = "$fileHost/collectManga/${info.projectId}/${info.chapterId}"

        return info.pageItem.map {
            Page(
                index = it.pageNo,
                imageUrl = "$base/${it.pageName ?: it.fileName}",
            )
        }
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val body =
            SearchRequest(
                keyword = query.trim(),
                status = 0,
                paging = PagingInfo(page, SEARCH_PAGE_SIZE),
            )

        return POST(
            "$baseUrl/api/project/search",
            apiHeaders,
            Json.encodeToString(body).toRequestBody(),
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()

    companion object {
        private const val POPULAR_PAGE_SIZE = 15
        private const val LATEST_PAGE_SIZE = 15
        private const val SEARCH_PAGE_SIZE = 100
    }
}
