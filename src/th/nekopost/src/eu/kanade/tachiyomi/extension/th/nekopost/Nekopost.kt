package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.extension.th.nekopost.model.PagingInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawChapterInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSearchSummaryList
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSummaryList
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
    private val json: Json by injectLazy()

    override val baseUrl = "https://www.nekopost.net"
    override val lang = "th"
    override val name = "Nekopost"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    private val latestMangaEndpoint = "https://api.osemocphoto.com/frontAPI/getLatestChapter/m"
    private val projectDataEndpoint = "https://api.osemocphoto.com/frontAPI/getProjectInfo"
    private val fileHost = "https://www.osemocphoto.com"

    private val existingProject = HashSet<String>()
    private var firstPageNulled = false

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("th"))
    }

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private fun getStatus(status: String) = when (status) {
        "1" -> SManga.ONGOING
        "2" -> SManga.COMPLETED
        "3" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()

    override fun mangaDetailsRequest(manga: SManga) = GET("$projectDataEndpoint/${manga.url}", headers)
    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val projectInfo: RawProjectInfo = json.decodeFromString(response.body.string())

        return SManga.create().apply {
            projectInfo.projectInfo.let {
                url = it.projectId
                title = it.projectName
                artist = it.artistName
                author = it.authorName
                description = it.info
                status = getStatus(it.status)
                thumbnail_url = "$fileHost/collectManga/${it.projectId}/${it.projectId}_cover.jpg"
                initialized = true
            }
            genre = projectInfo.projectCategoryUsed?.joinToString(", ") { it.categoryName }.orEmpty()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val headers = Headers.headersOf("accept", "*/*", "content-type", "text/plain;charset=UTF-8", "origin", baseUrl)
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
        } ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter) = GET("$fileHost/collectManga/${chapter.url}", headers)

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

    override fun popularMangaRequest(page: Int): Request {
        if (page <= 1) existingProject.clear()
        return GET("$latestMangaEndpoint/${if (firstPageNulled) page else page - 1}", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val projectList: RawProjectSummaryList = json.decodeFromString(response.body.string())

        projectList.listChapter ?: run {
            firstPageNulled = true
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val mangaList = projectList.listChapter
            .filterNot { it.projectId in existingProject }
            .map {
                existingProject.add(it.projectId)
                SManga.create().apply {
                    url = it.projectId
                    title = it.projectName
                    thumbnail_url = "$fileHost/collectManga/${it.projectId}/${it.projectId}_cover.jpg"
                    initialized = false
                }
            }

        return MangasPage(mangaList, hasNextPage = true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchHeaders = headersBuilder()
            .set("Accept", "*/*")
            .set("Content-Type", "application/json")
            .build()

        val searchData = SearchRequest(
            keyword = query,
            status = 0,
            paging = PagingInfo(pageNo = page, pageSize = 100),
        )

        return POST("$baseUrl/api/project/search", searchHeaders, Json.encodeToString(searchData).toRequestBody())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val projectList: RawProjectSearchSummaryList = json.decodeFromString(response.body.string())

        val mangaList = projectList.listProject
            .filter { it.projectType == "m" }
            .map {
                SManga.create().apply {
                    url = it.pid.toString()
                    title = it.projectName
                    status = it.status
                    thumbnail_url = "$fileHost/collectManga/${it.pid}/${it.pid}_cover.jpg?ver=${it.coverVersion}"
                }
            }

        return MangasPage(mangaList, false)
    }
}
