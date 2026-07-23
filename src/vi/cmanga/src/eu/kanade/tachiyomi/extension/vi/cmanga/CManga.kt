package eu.kanade.tachiyomi.extension.vi.cmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class CManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", sourceFile)
            .addQueryParameter("type", "hot")
            .addQueryParameter("sort", defaultSort)
            .addQueryParameter("tag", "")
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaPage(client.get(url))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", sourceFile)
            .addQueryParameter("type", "update")
            .addQueryParameter("sort", defaultSort)
            .addQueryParameter("tag", "")
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaPage(client.get(url))
    }

    // ============================== Search ================================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async {
            client.get("$baseUrl/assets/json/album_tags_image.json")
                .parseAs<CMangaGenreResponse>()
                .list
                .mapNotNull { (value, genre) ->
                    val name = genre.name.trim()
                    if (name.isEmpty() || value.isEmpty()) null else GenreOption(name, value)
                }
        }

        val teams = async {
            client.get("$baseUrl/api/team_list")
                .parseAs<CMangaTeamResponse>()
                .data
                .mapNotNull { team ->
                    val name = runCatching { team.info.parseAs<CMangaTeamInfo>().name.trim() }
                        .getOrNull()
                        .orEmpty()
                    if (name.isEmpty()) null else FilterOption(name, team.id.toString())
                }
        }

        CMangaFilterData(genres.await(), teams.await()).toJsonElement()
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()
            ?.selectedValues()
            .orEmpty()
            .joinToString(",")

        val team = filters.firstInstanceOrNull<TeamFilter>()
            ?.toUriPart()
            ?: "0"
        val minChapter = filters.firstInstanceOrNull<MinChapterFilter>()
            ?.toUriPart()
            ?: "0"
        val sort = filters.firstInstanceOrNull<SortFilter>()
            ?.toUriPart()
            ?: defaultSort
        val status = filters.firstInstanceOrNull<StatusFilter>()
            ?.toUriPart()
            ?: "all"

        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", sourceFile)
            .addQueryParameter("type", "search")
            .addQueryParameter("sort", sort)
            .addQueryParameter("tag", genres)
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("status", status)
            .addQueryParameter("string", query)
            .addQueryParameter("team", team)
            .addQueryParameter("num_chapter", minChapter)
            .build()

        return parseMangaPage(client.get(url))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val payload = response.parseAs<CMangaAlbumListResponse>()
        val mangas = payload.data?.data
            .orEmpty()
            .mapNotNull(::toSManga)

        val total = payload.data?.total ?: 0
        val hasNextPage = hasNextPage(total, response.request.url.queryParameter("page"), response.request.url.queryParameter("limit"))
        return MangasPage(mangas, hasNextPage)
    }

    private fun toSManga(item: CMangaAlbumItem): SManga? {
        val info = parseAlbumInfo(item.info) ?: return null
        val title = info.name ?: return null
        val slug = info.url ?: return null
        val id = item.idAlbum ?: return null

        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain("/album/$slug-$id")
            thumbnail_url = resolveCoverUrl(info.avatar)
        }
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "album") return null

        val canonicalPath = when {
            url.pathSegments.any { it.startsWith("chapter-") } -> {
                val document = client.get(url).asJsoup()
                document.selectFirst("a[href^=/album/][href~=-[0-9]+$]")?.attr("href")
            }
            extractAlbumId(url.encodedPath) != null -> url.encodedPath
            else -> findAlbumBySlug(url.pathSegments.getOrNull(1))?.url
        } ?: return null

        val manga = SManga.create().apply { setUrlWithoutDomain(canonicalPath) }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private suspend fun findAlbumBySlug(slug: String?): SManga? {
        if (slug.isNullOrEmpty()) return null

        val url = "$baseUrl/api/home_album_list".toHttpUrl().newBuilder()
            .addQueryParameter("file", sourceFile)
            .addQueryParameter("type", "search")
            .addQueryParameter("sort", defaultSort)
            .addQueryParameter("tag", "")
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("page", "1")
            .addQueryParameter("status", "all")
            .addQueryParameter("string", slug.replace('-', ' '))
            .addQueryParameter("team", "0")
            .addQueryParameter("num_chapter", "0")
            .build()

        return client.get(url).parseAs<CMangaAlbumListResponse>()
            .data?.data
            .orEmpty()
            .mapNotNull(::toSManga)
            .firstOrNull { extractAlbumSlug(it.url) == slug }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get("$baseUrl${manga.url}")
        val document = response.asJsoup()
        val albumId = extractAlbumId(manga.url)
        val apiInfo: CMangaAlbumInfo? = if (albumId != null) fetchAlbumInfo(albumId) else null

        return SManga.create().apply {
            setUrlWithoutDomain(manga.url)
            title = document.selectFirst("div.book_other h1 .name, div.book_other h1 p.name")!!.text()
            thumbnail_url = document.selectFirst("div.book_avatar img[itemprop=image], div.book_avatar img")?.absUrl("src")
                ?: resolveCoverUrl(apiInfo?.avatar)
            author = apiInfo?.author?.joinToString().ifNullOrBlank { "Unknown" }
            status = parseStatus(apiInfo?.status)
            genre = apiInfo?.tags
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString()
            description = parseDescription(document, apiInfo?.detail)
        }
    }

    private suspend fun fetchAlbumInfo(albumId: String): CMangaAlbumInfo? {
        val url = "$baseUrl/api/get_data_by_id".toHttpUrl().newBuilder()
            .addQueryParameter("id", albumId)
            .addQueryParameter("table", "album")
            .addQueryParameter("data", "info,data")
            .build()

        val payload = client.get(url).parseAs<CMangaAlbumByIdResponse>()
        return parseAlbumInfo(payload.data?.info)
    }

    private fun parseDescription(document: Document, apiDetail: String?): String? {
        val html = document.selectFirst("#book_detail_text")?.html() ?: apiDetail
        if (html == null) return null

        val normalized = html
            .replace(brTagRegex, "\n")
            .replace("&nbsp;", " ")

        val plainText = Jsoup.parse(normalized).wholeText()
            .replace(xemThemRegex, "")
            .replace(anBotRegex, "")
            .replace(horizontalSpaceRegex, " ")
            .replace(multiNewlineRegex, "\n")
            .trim()

        return plainText.ifEmpty { null }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("doing", ignoreCase = true) -> SManga.ONGOING
        status.contains("done", ignoreCase = true) -> SManga.COMPLETED
        status.contains("đang", ignoreCase = true) -> SManga.ONGOING
        status.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun getChapterList(manga: SManga): List<SChapter> = coroutineScope {
        val albumId = extractAlbumId(manga.url) ?: throw Exception("Không tìm thấy mã truyện")
        val albumSlug = extractAlbumSlug(manga.url)
        val version = currentEpochSeconds()

        val seenChapterIds = mutableSetOf<String>()
        val chapters = mutableListOf<SChapter>()

        val firstPageItems = client.get(chapterListPageUrl(albumId, 1, albumSlug, version))
            .parseAs<CMangaChapterListResponse>().data.orEmpty()
        chapters += toSChapterList(firstPageItems, albumSlug, seenChapterIds)

        var nextPage = 2
        var hasMorePages = firstPageItems.size >= chapterPageSize

        while (hasMorePages) {
            val pageResults = (nextPage until nextPage + chapterFetchBatchSize)
                .map { page ->
                    async {
                        client.get(chapterListPageUrl(albumId, page, albumSlug, version))
                            .parseAs<CMangaChapterListResponse>().data.orEmpty()
                    }
                }
                .awaitAll()

            for (pageItems in pageResults) {
                val newItems = toSChapterList(pageItems, albumSlug, seenChapterIds)
                if (pageItems.isEmpty() || newItems.isEmpty()) {
                    hasMorePages = false
                    break
                }

                chapters += newItems
                if (pageItems.size < chapterPageSize) {
                    hasMorePages = false
                    break
                }
            }

            nextPage += chapterFetchBatchSize
        }

        chapters
    }

    private fun chapterListPageUrl(albumId: String, page: Int, slug: String?, version: String): HttpUrl = "$baseUrl/api/chapter_list".toHttpUrl().newBuilder()
        .addQueryParameter("album", albumId)
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", chapterPageSize.toString())
        .addQueryParameter("v", version)
        .apply {
            if (slug != null) {
                addQueryParameter("slug", slug)
            }
        }
        .build()

    private fun toSChapterList(
        chapterItems: List<CMangaChapterItem>,
        albumSlug: String?,
        seenChapterIds: MutableSet<String>,
    ): List<SChapter> {
        return chapterItems.mapNotNull { chapterItem ->
            val chapterInfo = parseChapterInfo(chapterItem.info) ?: return@mapNotNull null
            val chapterId = chapterInfo.id?.let { runCatching { it.string }.getOrNull() }
                ?: chapterItem.idChapter?.toString()
                ?: return@mapNotNull null
            if (!seenChapterIds.add(chapterId)) return@mapNotNull null

            val chapterNumber = chapterInfo.num?.let { runCatching { it.string }.getOrNull() }
                ?: return@mapNotNull null
            val chapterTitle = buildChapterTitle(chapterNumber, chapterInfo.name)
            val chapterName = if (isChapterLocked(chapterInfo)) "🔒 $chapterTitle" else chapterTitle
            val slug = albumSlug ?: "truyen"

            SChapter.create().apply {
                name = chapterName
                setUrlWithoutDomain("/album/$slug/chapter-$chapterNumber-$chapterId")
                date_upload = parseChapterDate(chapterInfo.lastUpdate)
            }
        }
    }

    private fun parseChapterDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDateTime.parse(date, dateFormat)
                .atZone(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun buildChapterTitle(number: String, chapterTitle: String?): String {
        if (chapterTitle.isNullOrBlank()) return "Chapter $number"
        if (isRedundantChapterTitle(number, chapterTitle)) return "Chapter $number"
        return "Chapter $number: $chapterTitle"
    }

    private fun isRedundantChapterTitle(number: String, chapterTitle: String): Boolean {
        val normalizedNumber = number.lowercase(Locale.ROOT)
        val normalizedTitle = chapterTitle.lowercase(Locale.ROOT).removeSuffix(":")
        if (
            normalizedTitle == normalizedNumber ||
            normalizedTitle == "chapter $normalizedNumber" ||
            normalizedTitle == "chap $normalizedNumber" ||
            normalizedTitle == "chương $normalizedNumber" ||
            normalizedTitle == "chuong $normalizedNumber"
        ) {
            return true
        }

        val compactNumber = normalizedNumber.replace(" ", "")
        val compactTitle = normalizedTitle.replace(" ", "")

        for (prefix in redundantChapterPrefixes) {
            if (!compactTitle.startsWith(prefix)) continue

            val rest = compactTitle.removePrefix(prefix)
            if (!rest.startsWith(compactNumber)) continue

            val suffix = rest.removePrefix(compactNumber)
            if (suffix.isEmpty() || !suffix.first().isDigit()) {
                return true
            }
        }

        return false
    }

    private fun isChapterLocked(chapterInfo: CMangaChapterInfo): Boolean {
        val lock = chapterInfo.lock
        val chapterLevel = chapterInfo.level?.let { runCatching { it.int }.getOrNull() } ?: 0
        val lockLevel = lock?.level?.let { runCatching { it.int }.getOrNull() } ?: 0
        val lockFee = lock?.fee?.let { runCatching { it.int }.getOrNull() } ?: 0
        val nowSeconds = System.currentTimeMillis() / 1000
        val lockEnd = lock?.end?.let { runCatching { it.long }.getOrNull() } ?: 0L
        val hasActiveLock = lock != null && lockEnd >= nowSeconds
        return hasActiveLock || chapterLevel != 0 || lockLevel != 0 || lockFee != 0
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = extractChapterId(chapter.url)
            ?: throw Exception("Không tìm thấy mã chương")
        val userSecurity = getUserSecurity()

        val url = "$baseUrl/api/chapter_image".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("v", "0")
            .addQueryParameter("time", (System.currentTimeMillis() / 1000).toString())
            .addQueryParameter("user_id", userSecurity.id ?: "0")
            .addQueryParameter("user_token", userSecurity.token ?: "")
            .build()

        val payload = client.get(url).parseAs<CMangaChapterImageResponse>()
        val imageData = payload.data ?: return emptyList()
        if (imageData.status != 1) {
            throw Exception(loginWebviewMessage)
        }

        return imageData.image.orEmpty().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Helpers ===============================

    private fun parseAlbumInfo(rawInfo: String?): CMangaAlbumInfo? {
        if (rawInfo == null) return null
        return runCatching { rawInfo.parseAs<CMangaAlbumInfo>() }.getOrNull()
    }

    private fun parseChapterInfo(rawInfo: String?): CMangaChapterInfo? {
        if (rawInfo == null) return null
        return runCatching { rawInfo.parseAs<CMangaChapterInfo>() }.getOrNull()
    }

    private fun resolveCoverUrl(avatar: String?): String? {
        if (avatar.isNullOrBlank()) return null
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) return avatar
        if (avatar.startsWith("/")) return "$baseUrl$avatar"
        return "$baseUrl/assets/tmp/album/$avatar"
    }

    private fun hasNextPage(total: Int, page: String?, limit: String?): Boolean {
        val currentPage = page?.toIntOrNull() ?: 1
        val currentPageSize = limit?.toIntOrNull() ?: pageSize
        return currentPage * currentPageSize < total
    }

    private fun extractAlbumId(url: String): String? = albumIdRegex.find(url)?.groupValues?.get(1)

    private fun extractAlbumSlug(url: String): String? = albumSlugRegex.find(url)?.groupValues?.get(1)

    private fun extractChapterId(urlPath: String): String? = chapterIdRegex.find(urlPath)?.groupValues?.get(1)

    private fun String?.ifNullOrBlank(defaultValue: () -> String): String {
        if (this.isNullOrBlank()) return defaultValue()
        return this
    }

    private class CMangaUserSecurityCredential(
        val id: String? = null,
        val token: String? = null,
    )

    private fun getUserSecurity(): CMangaUserSecurityCredential {
        val cookieValue = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == userSecurityCookie }
            ?.value
            ?: return CMangaUserSecurityCredential()

        val decoded = decodeCookieValue(cookieValue) ?: return CMangaUserSecurityCredential()
        val security = runCatching { decoded.parseAs<CMangaUserSecurity>() }.getOrNull()
            ?: return CMangaUserSecurityCredential()

        return CMangaUserSecurityCredential(
            id = security.id?.let { runCatching { it.string }.getOrNull() },
            token = security.token?.let { runCatching { it.string }.getOrNull() },
        )
    }

    private fun decodeCookieValue(value: String): String? {
        var decoded = value
        repeat(2) {
            val next = runCatching { URLDecoder.decode(decoded, StandardCharsets.UTF_8.name()) }.getOrNull()
                ?: return null
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    private fun currentEpochSeconds(): String = (System.currentTimeMillis() / 1000).toString()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<CMangaFilterData>()
        return getFilters(filterData?.genres.orEmpty(), filterData?.teams.orEmpty())
    }

    private val albumIdRegex = Regex("""-([0-9]+)(?:/ref/[0-9]+)?/?$""")
    private val albumSlugRegex = Regex("""/album/([^/]+?)-[0-9]+(?:/ref/[0-9]+)?/?$""")
    private val chapterIdRegex = Regex("""chapter-[^/]+-([0-9]+)""")
    private val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val horizontalSpaceRegex = Regex("[\\t\\x0B\\f\\r ]+")
    private val multiNewlineRegex = Regex("\\n{2,}")
    private val xemThemRegex = Regex("""\.\.\.\s*Xem thêm""", RegexOption.IGNORE_CASE)
    private val anBotRegex = Regex("""Ẩn bớt""", RegexOption.IGNORE_CASE)

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")

    private val redundantChapterPrefixes = listOf("chapter", "chap", "chương", "chuong")
    private val sourceFile = "image"
    private val defaultSort = "update"
    private val pageSize = 20
    private val chapterPageSize = 50
    private val chapterFetchBatchSize = 3
    private val loginWebviewMessage = "Vui lòng đăng nhập vào tài khoản phù hợp qua Webview để đọc chương này"
    private val userSecurityCookie = "user_security"
}
