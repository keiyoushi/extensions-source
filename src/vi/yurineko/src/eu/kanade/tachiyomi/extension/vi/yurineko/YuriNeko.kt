package eu.kanade.tachiyomi.extension.vi.yurineko

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

class YuriNeko : HttpSource() {
    override val name = "YuriNeko"
    override val lang = "vi"
    override val baseUrl = "https://yurinekoz.com"
    override val supportsLatest = true
    private val apiUrl = "https://api.${baseUrl.toHttpUrl().host}"
    private val cdnUrl = "https://cdn.${baseUrl.toHttpUrl().host}"
    private val webApiUrl = "$baseUrl/api/v1"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 20, 1, TimeUnit.MINUTES)
        .addInterceptor(ImageDecryptor::interceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("limit", POPULAR_LIMIT.toString())
            .addQueryParameter("sort", "views")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = false,
        )
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", LATEST_LIMIT.toString())
            .addQueryParameter("sort", "latest")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val payload = response.parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = payload.page < payload.lastPage,
        )
    }

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val appliedFilters = filters.ifEmpty { getFilterList() }
        val tag = appliedFilters.firstInstanceOrNull<TagFilter>()?.selected
        val groupId = appliedFilters.firstInstanceOrNull<GroupFilter>()?.selected
        val sort = appliedFilters.firstInstanceOrNull<SortFilter>()?.selected ?: "latest"

        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", SEARCH_LIMIT.toString())
            addQueryParameter("sort", sort)
            query.takeIf(String::isNotBlank)?.let { addQueryParameter("search", it) }
            tag?.let { addQueryParameter("tags", it) }
            groupId?.let { addQueryParameter("groupId", it) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = payload.page < payload.lastPage,
        )
    }

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================
    override fun mangaDetailsParse(response: Response): SManga {
        val mangaId = resolveMangaId(response)
        val details = client.newCall(GET("$apiUrl/mangas/$mangaId", headers)).execute().use { detailResponse ->
            detailResponse.parseAs<MangaDetailsDto>()
        }

        val authors = details.linkedAuthors.map(LinkedPersonDto::name).joinToString()
        val artists = details.linkedArtists.map(LinkedPersonDto::name).joinToString()
        val genres = details.tags.map(TagDto::name).joinToString()

        return SManga.create().apply {
            title = details.title
            author = authors.takeIf(String::isNotEmpty)
            artist = artists.takeIf(String::isNotEmpty)
            genre = genres.takeIf(String::isNotEmpty)
            status = parseStatus(details.status)
            description = details.description?.let(::htmlToText)
            thumbnail_url = cdnImageUrl(details.thumbnailUrl)
        }
    }

    private fun mangaFromDto(manga: MangaDto): SManga = SManga.create().apply {
        setUrlWithoutDomain("/manga/${manga.id}")
        title = manga.title
        thumbnail_url = cdnImageUrl(manga.thumbnailUrl)
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun htmlToText(html: String): String = Jsoup.parseBodyFragment(html).text()

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = resolveMangaId(response)

        return fetchAllChapters(mangaId).map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain("/manga/$mangaId/${chapter.id}")
                name = chapterName(chapter)
                date_upload = parseChapterDate(chapter.publishedAt ?: chapter.createdAt)
            }
        }
    }

    private fun fetchAllChapters(mangaId: String): List<ChapterDto> = fetchChaptersFromChapterApi(mangaId)

    private fun fetchChaptersFromChapterApi(mangaId: String): List<ChapterDto> {
        val chapters = linkedMapOf<String, ChapterDto>()
        var currentPage = 1
        var totalPages = 1

        while (currentPage <= totalPages) {
            val url = "$webApiUrl/chapters/$mangaId".toHttpUrl().newBuilder()
                .addQueryParameter("page", currentPage.toString())
                .addQueryParameter("limit", CHAPTER_LIST_LIMIT.toString())
                .addQueryParameter("sort", "desc")
                .build()
            val payload = client.newCall(GET(url, headers)).execute().use { response ->
                response.parseAs<ChapterListDto>()
            }

            payload.data.forEach { chapter ->
                chapters.putIfAbsent(chapter.id, chapter)
            }

            if (payload.data.isEmpty()) break
            totalPages = payload.pageCount.coerceAtLeast(1)
            currentPage++
        }

        return chapters.values.sortedByDescending(::chapterSortValue)
    }

    private fun chapterSortValue(chapter: ChapterDto): Double {
        chapter.order?.let { return it }
        return CHAPTER_NUMBER_REGEX.find(chapter.chapterNumber)?.value?.toDoubleOrNull()
            ?: Double.NEGATIVE_INFINITY
    }

    private fun chapterName(chapter: ChapterDto): String {
        val chapterNumber = chapter.chapterNumber
        val baseName = if (
            chapterNumber.startsWith("Chương", ignoreCase = true) ||
            chapterNumber.startsWith("Chapter", ignoreCase = true)
        ) {
            chapterNumber
        } else {
            "Chương $chapterNumber"
        }

        val chapterTitle = (chapter.title ?: chapter.name)
            ?.takeIf(String::isNotBlank)

        return chapterTitle?.let { "$baseName: $it" } ?: baseName
    }

    private fun parseChapterDate(dateText: String?): Long = dateText?.let {
        runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
    } ?: 0L

    // ============================== Pages =================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageUrls = parsePageUrls(document)

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun parsePageUrls(document: Document): List<String> = parsePageUrlsFromChapterData(document)
        .ifEmpty { parsePageUrlsFromNextImage(document) }

    private fun parsePageUrlsFromChapterData(document: Document): List<String> {
        val scriptText = document.select("script").joinToString("\n") { it.data() }

        return CHAPTER_PAGE_URL_REGEX.findAll(scriptText)
            .map { it.value }
            .mapNotNull(::normalizeChapterImageUrl)
            .distinct()
            .toList()
    }

    private fun parsePageUrlsFromNextImage(document: Document): List<String> = document.select("img[src], img[srcset]")
        .mapNotNull(::parsePageUrlFromImageElement)
        .distinct()
        .toList()

    private fun parsePageUrlFromImageElement(element: Element): String? {
        val directUrl = normalizeChapterImageUrl(element.absUrl("src"))
        if (directUrl != null) return directUrl

        val srcUrl = parseNextImageUrl(element.absUrl("src"))
        if (srcUrl != null) return srcUrl

        return element.attr("srcset")
            .split(',')
            .map(String::trim)
            .map { it.substringBefore(' ') }
            .firstNotNullOfOrNull(::parseNextImageUrl)
    }

    private fun parseNextImageUrl(rawUrl: String?): String? {
        val value = rawUrl?.takeIf(String::isNotBlank) ?: return null
        val httpUrl = value.toHttpUrlOrNull()
            ?: "$baseUrl$value".toHttpUrlOrNull()
            ?: return null
        return normalizeChapterImageUrl(httpUrl.queryParameter("url"))
            ?: normalizeChapterImageUrl(value)
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!
        val xIk = ImageDecryptor.extractKey(imageUrl)

        val imageHeaders = headersBuilder().apply {
            xIk?.let { add("x-ik", it) }
        }.build()

        return GET(imageUrl, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun cdnImageUrl(path: String?): String? {
        val value = path?.takeIf(String::isNotBlank) ?: return null
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "$cdnUrl/${value.removePrefix("/")}"
        }
    }

    private fun normalizeChapterImageUrl(value: String?): String? {
        val raw = value?.takeIf(String::isNotBlank) ?: return null
        val unescaped = raw
            .trimEnd('\\')
            .replace("\\u0026", "&")
            .replace("\\/", "/")
        val resolved = decodeApiImageUrl(unescaped) ?: unescaped

        return when {
            resolved.startsWith("http://") || resolved.startsWith("https://") -> {
                resolved.takeIf { CHAPTER_IMAGE_PATH_REGEX.containsMatchIn(it) }
            }
            resolved.startsWith("/chapters/") || resolved.startsWith("chapters/") -> {
                cdnImageUrl(resolved)
            }
            resolved.startsWith("/api/img") -> {
                "$baseUrl$resolved"
            }
            else -> null
        }
    }

    private fun decodeApiImageUrl(rawValue: String): String? {
        val value = rawValue.takeIf { it.contains("/api/img") } ?: return null
        val url = value.toHttpUrlOrNull()
            ?: "$baseUrl${value.takeIf { it.startsWith("/") } ?: "/$value"}".toHttpUrlOrNull()
            ?: return null
        val encoded = url.queryParameter("d")?.takeIf(String::isNotBlank) ?: return null
        val decoded = runCatching {
            val normalized = encoded.replace('-', '+').replace('_', '/')
            val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
            String(Base64.getDecoder().decode(padded))
        }.getOrNull() ?: return null

        return decoded.substringBefore('|')
            .takeIf { it.startsWith("http") || it.startsWith("/chapters/") || it.startsWith("chapters/") }
    }

    private fun resolveMangaId(response: Response): String = response.request.url.mangaIdOrNull()
        ?: extractMangaIdFromDocument(response.asJsoup())
        ?: throw IllegalArgumentException("Không tìm thấy manga id từ URL: ${response.request.url}")

    private fun extractMangaIdFromDocument(document: Document): String? = document.select("a[href*=/manga/]")
        .asSequence()
        .mapNotNull { MANGA_PATH_ID_REGEX.find(it.attr("href"))?.groupValues?.getOrNull(1) }
        .firstOrNull()

    private fun HttpUrl.mangaIdOrNull(): String? {
        val mangaIndex = pathSegments.indexOf("manga")
        val mangaId = if (mangaIndex != -1) pathSegments.getOrNull(mangaIndex + 1) else null

        return mangaId
            ?.takeIf(UUID_REGEX::matches)
            ?: pathSegments.firstOrNull(UUID_REGEX::matches)
    }

    // =============================== Related ================================

    // disable suggested mangas on Komikku due to heavy rate limit
    override val disableRelatedMangasBySearch = true
    override val supportsRelatedMangas = false

    companion object {
        private const val POPULAR_LIMIT = 10
        private const val LATEST_LIMIT = 16
        private const val SEARCH_LIMIT = 20
        private const val CHAPTER_LIST_LIMIT = 50

        private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val UUID_REGEX = Regex(UUID_PATTERN, RegexOption.IGNORE_CASE)
        private val MANGA_PATH_ID_REGEX = Regex("/manga/($UUID_PATTERN)", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val CHAPTER_PAGE_URL_REGEX = Regex("""(?:/api/img\?[^"'\s]+|/?chapters/[^"'\\\s]+)""")
        private val CHAPTER_IMAGE_PATH_REGEX = Regex("""(?:^|/)chapters/""")
    }
}
