package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.extension.th.nekopost.model.RawChapterInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectInfo
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSearchSummaryList
import eu.kanade.tachiyomi.extension.th.nekopost.model.RawProjectSummaryList
import eu.kanade.tachiyomi.extension.th.nekopost.model.SearchRequest
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
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
    override val baseUrl: String = "https://www.nekopost.net"

    private val latestMangaEndpoint: String = "https://api.osemocphoto.com/frontAPI/getLatestChapter/m"
    private val projectDataEndpoint: String = "https://api.osemocphoto.com/frontAPI/getProjectInfo"
    private val fileHost: String = "https://www.osemocphoto.com"

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", "$baseUrl/")
    }

    private val existingProject: HashSet<String> = HashSet()

    private var firstPageNulled: Boolean = false

    override val lang: String = "th"
    override val name: String = "Nekopost"

    override val supportsLatest: Boolean = false

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

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$projectDataEndpoint/${manga.url}", headers)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = response.body
        val projectInfo: RawProjectInfo = json.decodeFromString(responseBody.string())
        val manga = SManga.create()
        manga.apply {
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

            genre = if (projectInfo.projectCategoryUsed != null) {
                projectInfo.projectCategoryUsed.joinToString(", ") { it.categoryName }
            } else {
                ""
            }
        }
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val headers = Headers.headersOf("accept", "*/*", "content-type", "text/plain;charset=UTF-8", "origin", baseUrl)
        return GET("$projectDataEndpoint/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body.string()
        val projectInfo: RawProjectInfo = json.decodeFromString(responseBody)
        val manga = SManga.create()
        manga.status = getStatus(projectInfo.projectInfo.status)

        if (manga.status == SManga.LICENSED) {
            throw Exception("Licensed - No chapter to show")
        }

        return projectInfo.projectChapterList!!.map { chapter ->
            SChapter.create().apply {
                url = "${projectInfo.projectInfo.projectId.toInt()}/${chapter.chapterId}/${projectInfo.projectInfo.projectId.toInt()}_${chapter.chapterId}.json"
                name = chapter.chapterName
                date_upload = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale("th"),
                ).parse(chapter.createDate)?.time ?: 0L
                chapter_number = chapter.chapterNo.toFloat()
                scanlator = chapter.providerName
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$fileHost/collectManga/${chapter.url}", headers)
    }

    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl/manga/${chapter.url.substringBefore("/")}/${chapter.chapter_number.toString().removeSuffix(".0")}"

    override fun pageListParse(response: Response): List<Page> {
        val responseBody = response.body
        val chapterInfo: RawChapterInfo = json.decodeFromString(responseBody.string())

        return chapterInfo.pageItem.map { page ->
            val imgUrl: String = if (page.pageName != null) {
                "$fileHost/collectManga/${chapterInfo.projectId}/${chapterInfo.chapterId}/${page.pageName}"
            } else {
                "$fileHost/collectManga/${chapterInfo.projectId}/${chapterInfo.chapterId}/${page.fileName}"
            }
            Page(
                index = page.pageNo,
                imageUrl = imgUrl,
            )
        }
    }
    override fun popularMangaRequest(page: Int): Request {
        if (page <= 1) existingProject.clear()
        // API has a bug that sometime it returns null on first page
        return GET("$latestMangaEndpoint/${if (firstPageNulled) page else page - 1}", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val responseBody = response.body
        val projectList: RawProjectSummaryList = json.decodeFromString(responseBody.string())

        val mangaList: List<SManga> = if (projectList.listChapter != null) {
            projectList.listChapter.filter { !existingProject.contains(it.projectId) }.map {
                SManga.create().apply {
                    url = it.projectId
                    title = it.projectName
                    thumbnail_url = "$fileHost/collectManga/${it.projectId}/${it.projectId}_cover.jpg"
                    initialized = false
                    status = 0
                }
            }
        } else {
            firstPageNulled = true // API has a bug that sometime it returns null on first page
            return MangasPage(emptyList(), hasNextPage = false)
        }

        mangaList.forEach { existingProject.add(it.url) }

        return MangasPage(mangaList, hasNextPage = true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val headers = Headers.headersOf("accept", "*/*", "content-type", "text/plain;charset=UTF-8", "origin", baseUrl)
        val requestBody = Json.encodeToString(SearchRequest(query, page)).toRequestBody()
        return POST("$baseUrl/api/explore/search", headers, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val responseBody = response.body.string()
        val decrypted = CryptoAES.decrypt(responseBody, "AeyTest")

        val projectList: RawProjectSearchSummaryList = json.decodeFromString(decrypted)
        val mangaList: List<SManga> = projectList.listProject
            .filter { it.projectType == "m" }
            .map {
                SManga.create().apply {
                    url = it.projectId.toString()
                    title = it.projectName
                    status = it.status
                    thumbnail_url = "$fileHost/collectManga/${it.projectId}/${it.projectId}_cover.jpg?ver=${it.coverVersion}"
                }
            }

        return MangasPage(mangaList, false)
    }
}
