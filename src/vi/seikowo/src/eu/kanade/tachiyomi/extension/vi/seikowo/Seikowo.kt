package eu.kanade.tachiyomi.extension.vi.seikowo

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class Seikowo : HttpSource() {
    override val name = "Seikowo"
    override val lang = "vi"
    override val baseUrl = "https://seikowo-app.blogspot.com"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val baseHttpUrl = baseUrl.toHttpUrl()

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private val isoDateMillisFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private var cachedCatalogueEntries: List<CatalogueEntry> = emptyList()
    private var cachedCatalogueEntriesAt = 0L

    private val contentTypeJson = "application/json; charset=utf-8".toMediaType()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val requestedPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        if (requestedPage > 1) {
            response.close()
            return MangasPage(emptyList(), false)
        }

        val scriptData = response.asJsoup()
            .selectFirst("script:containsData(window.__POPULAR_POST__)")
            ?.data()
            .orEmpty()

        val mangas = popularDataRegex.find(scriptData)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .let { popularItemRegex.findAll(it) }
            .mapNotNull { match ->
                val title = decodeHtmlEntities(match.groupValues[1])
                val url = match.groupValues[2]
                val thumbnail = match.groupValues[3].ifBlank { null }
                val relativeUrl = toRelativeUrl(url) ?: return@mapNotNull null

                SManga.create().apply {
                    this.url = relativeUrl
                    this.title = title
                    thumbnail_url = thumbnail
                }
            }
            .take(10)
            .toList()

        return MangasPage(mangas, false)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val startIndex = ((page - 1) * 30) + 1
        val url = feedUrlBuilder()
            .addQueryParameter("max-results", "30")
            .addQueryParameter("start-index", startIndex.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val requestUrl = response.request.url
        val feed = response.parseAs<FeedResponseDto>().feed
        val rawEntries = feed.entry.orEmpty()
        val mangas = rawEntries.mapNotNull(::toCatalogueEntry).map { it.toSManga() }

        val startIndex = requestUrl.queryParameter("start-index")?.toIntOrNull() ?: 1
        val total = feed.totalResults?.value?.toIntOrNull()
        val hasNextPage = if (total != null) {
            startIndex - 1 + rawEntries.size < total
        } else {
            rawEntries.size >= 30
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        SortByFilter(),
        GenreFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue
        val sort = filters.firstInstanceOrNull<SortByFilter>()?.selectedValue ?: "updated"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue

        val url = feedUrlBuilder()
            .addQueryParameter("max-results", "1")
            .addQueryParameter("start-index", "1")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("keyword", query)
            .addQueryParameter("sort", sort)
            .apply {
                status?.let { addQueryParameter("status", it) }
                genre?.let { addQueryParameter("genre", it) }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url
        val page = requestUrl.queryParameter("page")?.toIntOrNull() ?: 1
        val query = requestUrl.queryParameter("keyword").orEmpty()
        val status = requestUrl.queryParameter("status")
        val sort = requestUrl.queryParameter("sort") ?: "updated"
        val genre = requestUrl.queryParameter("genre")

        response.close()

        val filtered = getCatalogueEntries()
            .asSequence()
            .filter { entry ->
                query.isBlank() || entry.title.contains(query, ignoreCase = true)
            }
            .filter { entry ->
                status == null || entry.statusTerm?.equals(status, ignoreCase = true) == true
            }
            .filter { entry ->
                genre == null || entry.genres.any { it.equals(genre, ignoreCase = true) }
            }
            .let { entries ->
                when (sort) {
                    "published" -> entries.sortedByDescending { it.publishedAt }
                    "title" -> entries.sortedBy { it.title.lowercase(Locale.ROOT) }
                    "popular" -> entries.sortedWith(
                        compareByDescending<CatalogueEntry> { it.commentsCount }
                            .thenByDescending { it.updatedAt },
                    )

                    else -> entries.sortedByDescending { it.updatedAt }
                }
            }
            .toList()

        val fromIndex = (page - 1) * 30
        if (fromIndex >= filtered.size) {
            return MangasPage(emptyList(), false)
        }

        val toIndex = minOf(filtered.size, fromIndex + 30)
        val mangas = filtered.subList(fromIndex, toIndex).map { it.toSManga() }

        return MangasPage(mangas, toIndex < filtered.size)
    }

    @Synchronized
    private fun getCatalogueEntries(): List<CatalogueEntry> {
        val now = System.currentTimeMillis()
        if (cachedCatalogueEntries.isNotEmpty() && now - cachedCatalogueEntriesAt < 10 * 60 * 1000L) {
            return cachedCatalogueEntries
        }

        val feedBatchSize = 20
        val entries = mutableListOf<CatalogueEntry>()
        var startIndex = 1

        while (true) {
            val url = feedUrlBuilder()
                .addQueryParameter("max-results", feedBatchSize.toString())
                .addQueryParameter("start-index", startIndex.toString())
                .build()

            val feed = client.newCall(GET(url, headers)).execute().parseAs<FeedResponseDto>().feed
            val batch = feed.entry.orEmpty()

            entries += batch.mapNotNull(::toCatalogueEntry)

            if (batch.size < feedBatchSize) break

            startIndex += feedBatchSize
            if (startIndex > 5_001) break
        }

        cachedCatalogueEntries = entries
        cachedCatalogueEntriesAt = now

        return entries
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val postId = postIdRegex.find(body)?.groupValues?.getOrNull(1)
            ?: postIdFallbackRegex.find(body)?.groupValues?.getOrNull(1)
            ?: throw Exception("Cannot find post ID")

        val entry = fetchFeedEntry(postId)
        val metadata = parseMetadata(entry.content?.value)
            ?: throw Exception("Cannot find metadata")

        val title = metadata.title

        return SManga.create().apply {
            this.title = decodeHtmlEntities(title)
            author = metadata.author?.let(::decodeHtmlEntities)
            artist = metadata.artist?.let(::decodeHtmlEntities)
            description = metadata.description?.let(::decodeHtmlEntities)
            thumbnail_url = metadata.coverImage
            status = SManga.UNKNOWN
            genre = metadata.tags?.joinToString(", ")?.let(::decodeHtmlEntities)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val postId = postIdRegex.find(body)?.groupValues?.getOrNull(1)
            ?: postIdFallbackRegex.find(body)?.groupValues?.getOrNull(1)
            ?: throw Exception("Cannot find post ID")

        val entry = fetchFeedEntry(postId)
        val metadata = parseMetadata(entry.content?.value)
            ?: throw Exception("Cannot find metadata")

        val seriesId = metadata.seriesId
        val sourcePath = response.request.url.encodedPath

        return metadata.chapters
            .orEmpty()
            .mapNotNull { chapter ->
                val chapterNumber = chapter.number ?: chapter.chapterNum
                chapterNumber?.let {
                    ChapterItem(
                        number = it,
                        title = chapter.title ?: chapter.chapterTitle,
                        updatedAt = chapter.updatedAt ?: chapter.createdAt,
                    )
                }
            }
            .groupBy { formatChapterNumber(it.number) }
            .values
            .mapNotNull { duplicates ->
                duplicates.maxByOrNull { parseDate(it.updatedAt) }
            }
            .sortedByDescending { it.number }
            .map { item ->
                val chapterNumberText = formatChapterNumber(item.number)
                val chapterTitle = item.title?.let(::decodeHtmlEntities)
                SChapter.create().apply {
                    name = if (chapterTitle.isNullOrBlank()) {
                        "Chương $chapterNumberText"
                    } else {
                        "Chương $chapterNumberText - $chapterTitle"
                    }.removeSuffix(" - None")
                    chapter_number = item.number.toFloat()
                    date_upload = parseDate(item.updatedAt)
                    url = chapterReaderUrl(sourcePath, seriesId, chapterNumberText)
                }
            }
    }

    private fun fetchFeedEntry(postId: String): FeedEntryDto {
        val url = "$baseUrl/feeds/posts/default/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        return client.newCall(GET(url, headers)).execute().parseAs<FeedEntryResponseDto>().entry
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val requestUrl = response.request.url
        val chapterNumber = requestUrl.queryParameter("ch") ?: throw Exception("Missing chapter number")
        val seriesId = requestUrl.queryParameter("sid") ?: throw Exception("Missing series ID")

        response.close()

        val chapters = fetchWorkerPosts(seriesId)
            .flatMap { post -> parseDecryptedChapters(post.content) }

        val targetChapter = chapters
            .asSequence()
            .filter { chapter ->
                val number = chapter.number ?: chapter.chapterNum
                number != null && isChapterNumberMatch(number, chapterNumber)
            }
            .maxByOrNull { chapter -> chapter.images?.size ?: 0 }
            ?: throw Exception("Cannot find chapter data")

        val imageUrls = targetChapter.images
            .orEmpty()
            .mapNotNull { image -> image.id ?: image.dataUrl }
            .map(::toHighResolutionImageUrl)
            .ifEmpty { throw Exception("Cannot find chapter images") }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseDecryptedChapters(content: String?): List<NodeChapterDto> {
        if (content.isNullOrBlank()) return emptyList()

        val rawPayload = securePayloadRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(htmlTagRegex, "")
            ?.replace(whitespaceRegex, "")
            ?: return emptyList()

        val decrypted = runCatching { ImageDecryptor.decryptPayload(rawPayload) }
            .getOrNull()
            ?: return emptyList()

        return runCatching { decrypted.parseAs<List<NodeChapterDto>>() }
            .getOrNull()
            ?: runCatching { decrypted.parseAs<NodeChapterContainerDto>().chapters.orEmpty() }.getOrDefault(emptyList())
    }

    private fun fetchWorkerPosts(seriesId: String): List<WorkerPostDto> {
        val listPayload = WorkerListRequestDto(
            action = "list",
            labels = listOf("Data_Node", "Parent_$seriesId"),
            maxResults = 50,
            fetchFields = "items(id,content)",
            blogId = WORKER_BLOG_ID,
        )

        val listRequest = POST(
            WORKER_API_URL,
            jsonHeaders(),
            listPayload.toJsonString().toRequestBody(contentTypeJson),
        )

        val items = client.newCall(listRequest).execute().parseAs<WorkerListResponseDto>().items.orEmpty()

        if (items.none { it.content.isNullOrBlank() }) {
            return items
        }

        return items.mapNotNull { post ->
            if (!post.content.isNullOrBlank()) {
                post
            } else {
                val id = post.id ?: return@mapNotNull null
                fetchWorkerPost(id)
            }
        }
    }

    private fun fetchWorkerPost(postId: String): WorkerPostDto? {
        val getPayload = WorkerGetRequestDto(
            action = "get",
            id = postId,
            fetchFields = "id,content",
            blogId = WORKER_BLOG_ID,
        )

        val request = POST(
            WORKER_API_URL,
            jsonHeaders(),
            getPayload.toJsonString().toRequestBody(contentTypeJson),
        )

        return runCatching {
            client.newCall(request).execute().parseAs<WorkerPostDto>()
        }.getOrNull()
    }

    // ============================== Helpers ===============================

    private fun toCatalogueEntry(entry: FeedEntryDto): CatalogueEntry? {
        val metadata = parseMetadata(entry.content?.value) ?: return null
        val title = metadata.title

        val absoluteUrl = entry.link
            .orEmpty()
            .firstOrNull { it.rel == "alternate" }
            ?.href
            ?: return null

        val relativeUrl = toRelativeUrl(absoluteUrl) ?: return null

        val statusTerm = statusTerm(entry, metadata)
        val genres = genreTerms(entry, metadata)

        return CatalogueEntry(
            title = decodeHtmlEntities(title),
            url = relativeUrl,
            thumbnailUrl = metadata.coverImage ?: entry.thumbnail?.url,
            updatedAt = parseDate(entry.updated?.value),
            publishedAt = parseDate(entry.published?.value),
            commentsCount = entry.commentsCount?.value?.toIntOrNull() ?: 0,
            statusTerm = statusTerm,
            genres = genres,
        )
    }

    private fun parseMetadata(content: String?): SeriesMetadataDto? {
        if (content.isNullOrBlank()) return null

        val jsonString = metadataRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return runCatching { jsonString.parseAs<SeriesMetadataDto>() }.getOrNull()
    }

    private fun statusTerm(entry: FeedEntryDto, metadata: SeriesMetadataDto): String? {
        val normalizedStatus = metadata.status?.lowercase(Locale.ROOT)
        if (normalizedStatus != null) {
            if (normalizedStatus.contains("complete")) return "Status_Completed"
            if (normalizedStatus.contains("ongoing")) return "Status_Ongoing"
        }

        return entry.category
            .orEmpty()
            .mapNotNull { it.term }
            .firstOrNull { it.startsWith("Status_") }
    }

    private fun genreTerms(entry: FeedEntryDto, metadata: SeriesMetadataDto): Set<String> {
        val fromLabels = entry.category
            .orEmpty()
            .mapNotNull { it.term }
            .map { it.trim() }
            .filterNot { term ->
                term.startsWith("ID_") ||
                    term.startsWith("Type_") ||
                    term.startsWith("Status_") ||
                    term.equals("Data_Node", ignoreCase = true) ||
                    term.startsWith("Parent_")
            }

        val fromMetadata = metadata.tags.orEmpty()

        return (fromLabels + fromMetadata)
            .map(::decodeHtmlEntities)
            .toSet()
    }

    private fun chapterReaderUrl(sourcePath: String, seriesId: String, chapterNumber: String): String {
        val url = "$baseUrl$sourcePath".toHttpUrl().newBuilder()
            .addQueryParameter("ch", chapterNumber)
            .addQueryParameter("sid", seriesId)
            .build()

        return url.toString().removePrefix(baseUrl)
    }

    private fun parseDate(date: String?): Long {
        val normalizedDate = normalizeDateForZFormat(date)
        val withMillis = isoDateMillisFormat.tryParse(normalizedDate)
        if (withMillis != 0L) return withMillis
        return isoDateFormat.tryParse(normalizedDate)
    }

    private fun normalizeDateForZFormat(date: String?): String? {
        if (date == null) return null
        if (date.endsWith("Z")) return "${date.removeSuffix("Z")}+0000"
        return timezoneColonRegex.replace(date, "$1$2")
    }

    private fun toRelativeUrl(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.host != baseHttpUrl.host) return null

        val query = httpUrl.encodedQuery
        return if (query.isNullOrEmpty()) {
            httpUrl.encodedPath
        } else {
            "${httpUrl.encodedPath}?$query"
        }
    }

    private fun feedUrlBuilder() = "$baseUrl/feeds/posts/default".toHttpUrl().newBuilder()
        .addQueryParameter("alt", "json")
        .addQueryParameter("orderby", "updated")

    private fun jsonHeaders() = headersBuilder()
        .add("Content-Type", "application/json")
        .build()

    private fun isChapterNumberMatch(number: Double, rawChapterNumber: String): Boolean {
        val asDouble = rawChapterNumber.toDoubleOrNull()
        return if (asDouble != null) {
            abs(number - asDouble) < 0.0001
        } else {
            formatChapterNumber(number) == rawChapterNumber
        }
    }

    private fun formatChapterNumber(number: Double): String {
        val longValue = number.toLong()
        return if (number == longValue.toDouble()) {
            longValue.toString()
        } else {
            number.toString().trimEnd('0').trimEnd('.')
        }
    }

    private fun decodeHtmlEntities(value: String): String = Jsoup.parse(value).text()

    private fun toHighResolutionImageUrl(url: String): String {
        if (!url.contains("googleusercontent.com", ignoreCase = true)) {
            return url
        }

        val replaced = googleImageSizeSegmentRegex.replace(url, "/s3200-rw/")
        if (replaced != url) {
            return replaced
        }

        return "${url.removeSuffix("/")}/s3200-rw/"
    }

    companion object {
        private const val WORKER_API_URL = "https://seikowo.shimakazevn.workers.dev/api/v1/posts"
        private const val WORKER_BLOG_ID = "5099059547407963215"

        private val popularDataRegex = Regex(
            """window\.__POPULAR_POST__\s*=\s*JSON\.stringify\(\{[\s\S]*?data\s*:\s*\[(.*?)\]\s*\}\)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val popularItemRegex = Regex(
            """\{[\s\S]*?title\s*:\s*"([^"]+)"[\s\S]*?url\s*:\s*"([^"]+)"[\s\S]*?featuredImage\s*:\s*"([^"]*)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val metadataRegex = Regex(
            """<script[^>]+id=["']seikowo-metadata["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE,
        )

        private val securePayloadRegex = Regex(
            """<[^>]+id=["'](?:post-metadata-secure|seikowo-data-node)["'][^>]*>([\s\S]*?)</(?:script|div)>""",
            RegexOption.IGNORE_CASE,
        )

        private val postIdRegex = Regex(
            """window\.__POSTS__\s*=\s*JSON\.stringify\(\{\s*id:\s*"(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        private val postIdFallbackRegex = Regex(
            """'postId':\s*'(\d+)'""",
            RegexOption.IGNORE_CASE,
        )

        private val htmlTagRegex = Regex("""<[^>]*>""")
        private val whitespaceRegex = Regex("""\s+""")
        private val googleImageSizeSegmentRegex = Regex(
            """/s\d+(?:-[a-z0-9]+)?/""",
            RegexOption.IGNORE_CASE,
        )
        private val timezoneColonRegex = Regex("""([+-]\d{2}):(\d{2})$""")
    }
}
