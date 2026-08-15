package eu.kanade.tachiyomi.extension.vi.seikowo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Instant

@Source
abstract class Seikowo : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val scriptData = client.get(url).asJsoup()
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

    private fun toRelativeUrl(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.host != baseUrl.toHttpUrl().host) return null

        val query = httpUrl.encodedQuery
        return if (query.isNullOrEmpty()) {
            httpUrl.encodedPath
        } else {
            "${httpUrl.encodedPath}?$query"
        }
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val startIndex = ((page - 1) * 30) + 1
        val url = feedUrlBuilder()
            .addQueryParameter("max-results", "30")
            .addQueryParameter("start-index", startIndex.toString())
            .build()

        val feed = client.get(url).parseAs<FeedResponseDto>().feed
        val rawEntries = feed.entry.orEmpty()
        val mangas = rawEntries.mapNotNull(::toCatalogueEntry).map { it.toSManga() }

        val total = feed.totalResults?.value?.toIntOrNull()
        val hasNextPage = if (total != null) {
            startIndex - 1 + rawEntries.size < total
        } else {
            rawEntries.size >= 30
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun feedUrlBuilder() = "$baseUrl/feeds/posts/default".toHttpUrl().newBuilder()
        .addQueryParameter("alt", "json")
        .addQueryParameter("orderby", "updated")

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue
        val sort = filters.firstInstanceOrNull<SortByFilter>()?.selectedValue ?: "updated"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue

        val filtered = fetchCatalogueEntries()
            .asSequence()
            .filter { entry ->
                query.isEmpty() || entry.title.contains(query, ignoreCase = true)
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

    private suspend fun fetchCatalogueEntries(): List<CatalogueEntry> = coroutineScope {
        val feedBatchSize = 150
        val firstFeed = fetchCatalogueFeed(1, feedBatchSize)
        val totalResults = firstFeed.totalResults?.value?.toIntOrNull() ?: firstFeed.entry.orEmpty().size
        val remainingFeeds = generateSequence(feedBatchSize + 1) { it + feedBatchSize }
            .takeWhile { it <= totalResults }
            .map { startIndex ->
                async { fetchCatalogueFeed(startIndex, feedBatchSize) }
            }
            .toList()
            .awaitAll()

        (listOf(firstFeed) + remainingFeeds)
            .flatMap { it.entry.orEmpty() }
            .mapNotNull(::toCatalogueEntry)
    }

    private suspend fun fetchCatalogueFeed(startIndex: Int, maxResults: Int) = feedUrlBuilder()
        .addQueryParameter("max-results", maxResults.toString())
        .addQueryParameter("start-index", startIndex.toString())
        .build()
        .let { client.get(it).parseAs<FeedResponseDto>().feed }

    private fun toCatalogueEntry(entry: FeedEntryDto): CatalogueEntry? {
        val metadata = parseMetadata(entry.content?.value) ?: return null
        val postId = entry.id?.value
            ?.substringAfterLast("post-", missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val title = metadata.title

        val absoluteUrl = entry.link
            .orEmpty()
            .firstOrNull { it.rel == "alternate" }
            ?.href
            ?: return null

        val relativeUrl = toRelativeUrl(absoluteUrl) ?: return null

        return CatalogueEntry(
            postId = postId,
            title = decodeHtmlEntities(title),
            url = relativeUrl,
            thumbnailUrl = metadata.coverImage ?: entry.thumbnail?.url,
            updatedAt = parseDate(entry.updated?.value),
            publishedAt = parseDate(entry.published?.value),
            commentsCount = entry.commentsCount?.value?.toIntOrNull() ?: 0,
            statusTerm = statusTerm(entry, metadata),
            genres = genreTerms(entry, metadata),
        )
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

        return (fromLabels + metadata.tags.orEmpty())
            .map { decodeHtmlEntities(it).trim() }
            .filterNot(::isInternalLabel)
            .toSet()
    }

    private fun isInternalLabel(label: String): Boolean = label.isBlank() ||
        label.startsWith("ID_", ignoreCase = true) ||
        label.startsWith("Type_", ignoreCase = true) ||
        label.startsWith("Status_", ignoreCase = true) ||
        label.startsWith("Parent_", ignoreCase = true) ||
        label.equals("Data_Node", ignoreCase = true)

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.endsWith(".html")) return null

        val manga = SManga.create().apply { this.url = url.encodedPath }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val postId = manga.memo["postId"]?.string ?: fetchPostId(manga.url)

        val entry = fetchFeedEntry(postId)
        val metadata = parseMetadata(entry.content?.value)
            ?: throw Exception("Cannot find metadata")

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = decodeHtmlEntities(metadata.title)
            author = metadata.author?.let(::decodeHtmlEntities)
            artist = metadata.artist?.let(::decodeHtmlEntities)
            description = metadata.description?.let(::decodeHtmlEntities)
            thumbnail_url = metadata.coverImage
            status = parseStatus(metadata.status)
            genre = metadata.tags?.joinToString()?.let(::decodeHtmlEntities)
            memo = buildJsonObject { put("postId", JsonPrimitive(postId)) }
        }

        val seriesId = metadata.seriesId
        val sourcePath = manga.url.toHttpUrlOrNull()?.encodedPath ?: manga.url.substringBefore('?')

        val updatedChapters = metadata.chapters
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

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchPostId(mangaUrl: String): String {
        val body = client.get("$baseUrl$mangaUrl").use { it.body.string() }
        return postIdRegex.find(body)?.groupValues?.getOrNull(1)
            ?: postIdFallbackRegex.find(body)?.groupValues?.getOrNull(1)
            ?: throw Exception("Cannot find post ID")
    }

    private fun parseMetadata(content: String?): SeriesMetadataDto? {
        if (content.isNullOrBlank()) return null

        val jsonString = metadataRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return runCatching { jsonString.parseAs<SeriesMetadataDto>() }.getOrNull()
    }

    private fun chapterReaderUrl(sourcePath: String, seriesId: String, chapterNumber: String): String {
        val url = "$baseUrl$sourcePath".toHttpUrl().newBuilder()
            .addQueryParameter("ch", chapterNumber)
            .addQueryParameter("sid", seriesId)
            .build()

        return url.toString().removePrefix(baseUrl)
    }

    private fun parseDate(date: String?): Long = date?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        "complete" in status.lowercase(Locale.ROOT) -> SManga.COMPLETED
        "ongoing" in status.lowercase(Locale.ROOT) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchFeedEntry(postId: String): FeedEntryDto {
        val url = "$baseUrl/feeds/posts/default/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        return client.get(url).parseAs<FeedEntryResponseDto>().entry
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val requestUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val chapterNumber = requestUrl.queryParameter("ch") ?: throw Exception("Missing chapter number")
        val seriesId = requestUrl.queryParameter("sid") ?: throw Exception("Missing series ID")

        val chapters = fetchWorkerPosts(seriesId)
            .flatMap { post -> parseDecryptedChapters(post.content) }

        val targetChapter = chapters
            .asSequence()
            .filter { chapter ->
                val number = chapter.number ?: chapter.chapterNum
                number != null && isChapterNumberMatch(number, chapterNumber)
            }
            .maxByOrNull { chapter -> chapter.images?.size ?: 0 }
            ?: return emptyList()

        val imageUrls = targetChapter.images
            .orEmpty()
            .mapNotNull { image -> image.id ?: image.dataUrl }
            .map(::toHighResolutionImageUrl)

        if (imageUrls.isEmpty()) return emptyList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

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

    private suspend fun fetchWorkerPosts(seriesId: String): List<WorkerPostDto> {
        val listPayload = WorkerListRequestDto(
            action = "list",
            labels = listOf("Data_Node", "Parent_$seriesId"),
            maxResults = 50,
            fetchFields = "items(id,content)",
            blogId = workerBlogId,
        )

        val items = client.post(workerApiUrl, listPayload.toJsonRequestBody())
            .parseAs<WorkerListResponseDto>()
            .items
            .orEmpty()

        if (items.none { it.content.isNullOrBlank() }) {
            return items
        }

        return coroutineScope {
            items.map { post ->
                async {
                    if (!post.content.isNullOrBlank()) {
                        post
                    } else {
                        post.id?.let { fetchWorkerPost(it) }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun fetchWorkerPost(postId: String): WorkerPostDto? {
        val getPayload = WorkerGetRequestDto(
            action = "get",
            id = postId,
            fetchFields = "id,content",
            blogId = workerBlogId,
        )

        return runCatching {
            client.post(workerApiUrl, getPayload.toJsonRequestBody()).parseAs<WorkerPostDto>()
        }.getOrNull()
    }

    private fun isChapterNumberMatch(number: Double, rawChapterNumber: String): Boolean {
        val asDouble = rawChapterNumber.toDoubleOrNull()
        return if (asDouble != null) {
            abs(number - asDouble) < 0.0001
        } else {
            formatChapterNumber(number) == rawChapterNumber
        }
    }

    private fun formatChapterNumber(number: Double): String = number.toString().removeSuffix(".0")

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

    // ============================== Filters ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = fetchCatalogueEntries()
        .flatMap { it.genres }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf(StatusFilter(), SortByFilter())
        val genres = data?.parseAs<List<String>>().orEmpty()
        if (genres.isNotEmpty()) filters += GenreFilter(genres)
        return FilterList(filters)
    }

    // ============================== Helpers ===============================

    private fun decodeHtmlEntities(value: String): String = Jsoup.parse(value).text()

    private val workerApiUrl = "https://seikowo.shimakazevn.workers.dev/api/v1/posts"
    private val workerBlogId = "5099059547407963215"

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
}
