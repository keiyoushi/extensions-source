package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class Nekopost : KeiSource() {

    private val projectDataEndpoint get() = "$baseUrl/api/project/detail2"

    /**
     * The site splits its files across two hosts by project id. Either host answers 200 for the
     * other's paths, but returns a placeholder or a 10-byte "Hi there!" instead of the file, so
     * picking the wrong one fails silently.
     */
    private fun fileHost(projectId: Int) = if (projectId > FILE_HOST_THRESHOLD) FS_FILE_HOST else WWW_FILE_HOST

    private val apiHeaders get() = headersBuilder()
        .set("Accept", "*/*")
        .set("Content-Type", "application/json")
        .build()

    override suspend fun getPopularManga(page: Int) = parseProjectList(
        getProject(
            "list/popular",
            UpdatesRequest("mc", PagingInfo(1, POPULAR_PAGE_SIZE)),
        ),
        null,
        false,
    )

    override suspend fun getLatestUpdates(page: Int) = parseLatestUpdates(
        getProject(
            "latest",
            UpdatesRequest("m", PagingInfo(page, LATEST_PAGE_SIZE)),
        ),
    )

    private fun parseLatestUpdates(response: Response): MangasPage {
        val chapterList = response.parseAs<RawLatestChapterList>()

        if (chapterList.listChapter.isNullOrEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val mangaList =
            chapterList.listChapter.map {
                SManga.create().apply {
                    url = it.pid.toString()
                    title = it.projectName
                    status = getStatus(it.status.toInt())
                    thumbnail_url =
                        buildCoverUrl(it.pid, it.coverVersion)
                    initialized = false
                }
            }

        return MangasPage(
            mangaList,
            mangaList.size == LATEST_PAGE_SIZE,
        )
    }

    private suspend fun getProject(endpoint: String, body: UpdatesRequest) = client.post(
        "$baseUrl/api/project/$endpoint",
        apiHeaders,
        body.toJsonRequestBody(),
    )

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val emptyMangas = MangasPage(emptyList(), false)

        val segments = url.pathSegments
        if (segments.size < 2) return emptyMangas
        val type = segments[0]
        val id = segments[1].toIntOrNull() ?: return emptyMangas

        return when (type) {
            "manga" -> {
                val body = ProjectRequestBody(id).toJsonRequestBody()

                val response = client.post(projectDataEndpoint, apiHeaders, body)
                val projectInfo = response.parseAs<RawProjectInfo>()
                if (projectInfo.info == null) return emptyMangas

                MangasPage(
                    listOf(mangaFromProjectInfo(projectInfo)),
                    false,
                )
            }

            "editor" -> {
                val response = client.get("$baseUrl/api/editor/project/$id", apiHeaders)
                parseEditorProjectList(response)
            }

            else -> emptyMangas
        }
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val body =
            SearchRequest(
                keyword = query.trim(),
                status = 0,
                paging = PagingInfo(page, SEARCH_PAGE_SIZE),
            )

        val response = client.post(
            "$baseUrl/api/project/search",
            apiHeaders,
            body.toJsonRequestBody(),
        )
        return parseProjectList(response, setOf("m"), true)
    }

    private fun mangaFromProjectInfo(info: RawProjectInfo): SManga = SManga.create().apply {
        val p = info.info!!.project
        url = p.projectId.toString()
        title = p.projectName
        artist = p.artistName
        author = p.authorName
        description = p.info
        status = getStatus(p.status)
        thumbnail_url = buildCoverUrl(p.projectId)
        genre =
            info.info.category
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
                            project.pid,
                            project.coverVersion,
                        )
                    initialized = false
                }
            }

        return MangasPage(mangaList, false)
    }

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
                                it.pid,
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

    private fun buildCoverUrl(projectId: Int, coverVersion: Int? = null): String {
        val base = "${fileHost(projectId)}/collectManga/$projectId/${projectId}_cover.jpg"
        return if (coverVersion != null) "$base?ver=$coverVersion" else base
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val body = ProjectRequestBody(manga.url.toInt()).toJsonRequestBody()
        val projectInfo = client.post(projectDataEndpoint, apiHeaders, body).parseAs<RawProjectInfo>()
        return SMangaUpdate(
            manga = mangaFromProjectInfo(projectInfo),
            chapters = chapterListParse(projectInfo),
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    private fun chapterListParse(projectInfo: RawProjectInfo): List<SChapter> {
        val info = projectInfo.info!!

        if (getStatus(info.project.status) == SManga.LICENSED) {
            throw Exception("Licensed")
        }

        val projectId = info.project.projectId

        return info.chapter.orEmpty().map {
            SChapter.create().apply {
                url = "$projectId/${it.chapterId}/${projectId}_${it.chapterId}.json"
                name = it.chapterName
                chapter_number = it.chapterNo.toFloat()
                date_upload = Instant.tryParse(it.publishDate.value)
                scanlator = it.providerName
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url.substringBefore("/")}/${chapter.chapter_number.toString().removeSuffix(".0")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split('/')
        val request = ChapterInfoRequest(segments[0].toInt(), segments[1].toInt())

        val response = client.post("$baseUrl/handler/cinfo", apiHeaders, request.toJsonRequestBody())

        // Not JSON: the body is base64 of a CryptoJS payload, so it has to be read as text and
        // decrypted before it can be parsed.
        val decrypted = CryptoAES.decrypt(response.body.string(), CIPHER_PASSPHRASE)
            .ifEmpty { throw Exception("Failed to decrypt chapter data") }

        val info = decrypted.parseAs<RawChapterInfo>()
        val base = "${fileHost(info.projectId.toInt())}/collectManga/${info.projectId}/${info.chapterId}"

        // The site's own reader sorts by pageNo rather than trusting the returned order.
        return info.pageItem
            .sortedBy { it.pageNo.toInt() }
            .mapIndexed { index, item ->
                Page(index, imageUrl = "$base/${item.pageName ?: item.fileName}")
            }
    }

    private fun getStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        3 -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val POPULAR_PAGE_SIZE = 15
        private const val LATEST_PAGE_SIZE = 15
        private const val SEARCH_PAGE_SIZE = 100

        /** Passphrase the site's client bundle uses to decrypt the `handler/cinfo` response. */
        private const val CIPHER_PASSPHRASE = "AeyTest"

        private const val WWW_FILE_HOST = "https://www.osemocphoto.com"
        private const val FS_FILE_HOST = "https://fs.osemocphoto.com"

        /** Project ids above this are served from [FS_FILE_HOST], the rest from [WWW_FILE_HOST]. */
        private const val FILE_HOST_THRESHOLD = 17500
    }
}
