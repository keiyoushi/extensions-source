package eu.kanade.tachiyomi.extension.vi.yurineko

import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64
import kotlin.time.Instant

@Source
abstract class YuriNeko : KeiSource() {

    private val apiUrl get() = "https://api.${baseUrl.toHttpUrl().host}"
    private val cdnUrl get() = "https://cdn.${baseUrl.toHttpUrl().host}"
    private val webApiUrl get() = "$baseUrl/api/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(ImageDecryptor::interceptor)
        rateLimit(3)
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("limit", popularLimit.toString())
            .addQueryParameter("sort", "views")
            .build()
        val payload = client.get(url).parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = false,
        )
    }

    // ============================== Latest ================================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", latestLimit.toString())
            .addQueryParameter("sort", "latest")
            .build()
        val payload = client.get(url).parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = payload.page < payload.lastPage,
        )
    }

    // ============================== Search ================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tag = filters.firstInstanceOrNull<TagFilter>()?.selected
        val groupId = filters.firstInstanceOrNull<GroupFilter>()?.selected
        val doujinId = filters.firstInstanceOrNull<DoujinFilter>()?.selected
        val authorSlug = filters.firstInstanceOrNull<AuthorFilter>()?.selected
        val artistSlug = filters.firstInstanceOrNull<ArtistFilter>()?.selected
        val coupleSlug = filters.firstInstanceOrNull<CoupleFilter>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: "latest"

        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", searchLimit.toString())
            addQueryParameter("sort", sort)
            query.takeIf(String::isNotBlank)?.let { addQueryParameter("search", it) }
            tag?.let { addQueryParameter("tags", it) }
            groupId?.let { addQueryParameter("groupId", it) }
            authorSlug?.let { addQueryParameter("authorSlugs", it) }
            artistSlug?.let { addQueryParameter("artistSlugs", it) }
            coupleSlug?.let { addQueryParameter("coupleSlugs", it) }
            doujinId?.let {
                addQueryParameter("isDoujin", "true")
                addQueryParameter("originalStoryId", it)
            }
        }.build()
        val payload = client.get(url).parseAs<MangaListDto>()
        return MangasPage(
            mangas = payload.data.map(::mangaFromDto),
            hasNextPage = payload.page < payload.lastPage,
        )
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val doujins = async { fetchDoujinOptions() }
        val authors = async { fetchCategoryOptions("authors") }
        val artists = async { fetchCategoryOptions("artists") }
        val tags = async { fetchCategoryOptions("tags") }
        val groups = async { fetchGroupOptions() }
        val couples = async { fetchCategoryOptions("couples") }

        FilterData(
            doujins = doujins.await(),
            authors = authors.await(),
            artists = artists.await(),
            tags = tags.await(),
            groups = groups.await(),
            couples = couples.await(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private suspend fun fetchCategoryOptions(endpoint: String): List<FilterOption> = coroutineScope {
        val items = linkedMapOf<String, FilterOption>()
        val firstPage = fetchCategoryPage(endpoint, 1)
        val pages = listOf(firstPage) + (2..firstPage.pageCount.coerceAtLeast(1)).map { page ->
            async { fetchCategoryPage(endpoint, page) }
        }.awaitAll()

        pages.forEach { payload ->
            payload.data.forEach { item ->
                items.putIfAbsent(item.id, FilterOption(item.name, item.slug))
            }
        }

        items.values.sortedBy { it.name.lowercase() }
    }

    private suspend fun fetchCategoryPage(endpoint: String, page: Int): SlugCategoryListDto {
        val url = "$apiUrl/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", filterLimit.toString())
            .build()
        return client.get(url).parseAs()
    }

    private suspend fun fetchDoujinOptions(): List<FilterOption> = coroutineScope {
        val doujins = linkedMapOf<String, FilterOption>()
        val firstPage = fetchDoujinPage(1)
        val pages = listOf(firstPage) + (2..firstPage.pageCount.coerceAtLeast(1)).map { page ->
            async { fetchDoujinPage(page) }
        }.awaitAll()

        pages.forEach { payload ->
            payload.data.forEach { doujin ->
                doujins.putIfAbsent(doujin.id, FilterOption(doujin.displayName, doujin.id))
            }
        }

        doujins.values.sortedBy { it.name.lowercase() }
    }

    private suspend fun fetchDoujinPage(page: Int): DoujinListDto {
        val url = "$apiUrl/doujins".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", filterLimit.toString())
            .build()
        return client.get(url).parseAs()
    }

    private suspend fun fetchGroupOptions(): List<FilterOption> = coroutineScope {
        val groups = linkedMapOf<String, FilterOption>()
        val firstPage = fetchGroupPage(1)
        val pages = listOf(firstPage) + (2..firstPage.meta.totalPages.coerceAtLeast(1)).map { page ->
            async { fetchGroupPage(page) }
        }.awaitAll()

        pages.forEach { payload ->
            payload.items.forEach { group ->
                groups.putIfAbsent(group.id, FilterOption(group.name, group.id))
            }
        }

        groups.values.sortedBy { it.name.lowercase() }
    }

    private suspend fun fetchGroupPage(page: Int): GroupListDto {
        val url = "$apiUrl/groups".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", filterLimit.toString())
            .build()
        return client.get(url).parseAs()
    }

    // ============================== Details ===============================
    private suspend fun fetchMangaDetails(
        mangaId: String,
        mangaUrl: String,
        existingMemo: JsonObject = JsonObject(emptyMap()),
    ): SManga {
        val details = client.get("$apiUrl/mangas/$mangaId").parseAs<MangaDetailsDto>()

        val authors = details.linkedAuthors.map(LinkedPersonDto::name).joinToString()
        val artists = details.linkedArtists.map(LinkedPersonDto::name).joinToString()
        val genres = details.tags.map(TagDto::name).joinToString()

        return SManga.create().apply {
            setUrlWithoutDomain(mangaUrl)
            title = details.title
            author = authors.takeIf(String::isNotEmpty)
            artist = artists.takeIf(String::isNotEmpty)
            genre = genres.takeIf(String::isNotEmpty)
            status = parseStatus(details.status)
            description = details.description?.let(::htmlToText)
            thumbnail_url = cdnImageUrl(details.thumbnailUrl)
            memo = existingMemo.withMangaId(mangaId)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null

        val mangaId = url.mangaIdOrNull() ?: extractMangaIdFromDocument(client.get(url).asJsoup()) ?: return null
        return fetchMangaDetails(mangaId, "/manga/$mangaId")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val cachedMangaId = manga.memo["mangaId"]?.stringOrNull
        val mangaId = cachedMangaId
            ?: "$baseUrl${manga.url}".toHttpUrl().mangaIdOrNull()
            ?: extractMangaIdFromDocument(client.get("$baseUrl${manga.url}").asJsoup())
            ?: throw IllegalArgumentException("Không tìm thấy manga id từ URL: ${manga.url}")
        return coroutineScope {
            val updatedManga = async {
                when {
                    fetchDetails -> fetchMangaDetails(mangaId, "/manga/$mangaId", manga.memo)
                    cachedMangaId == null -> manga.apply { memo = memo.withMangaId(mangaId) }
                    else -> manga
                }
            }
            val updatedChapters = async {
                if (fetchChapters) fetchAllChapters(mangaId) else chapters
            }
            SMangaUpdate(updatedManga.await(), updatedChapters.await())
        }
    }

    private fun mangaFromDto(manga: MangaDto): SManga = SManga.create().apply {
        setUrlWithoutDomain("/manga/${manga.id}")
        title = manga.title
        thumbnail_url = cdnImageUrl(manga.thumbnailUrl)
        memo = memo.withMangaId(manga.id)
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun htmlToText(html: String): String = Jsoup.parseBodyFragment(html, baseUrl).text()

    // ============================== Chapters ==============================
    private suspend fun fetchAllChapters(mangaId: String): List<SChapter> = fetchChaptersFromChapterApi(mangaId).map { chapter ->
        SChapter.create().apply {
            setUrlWithoutDomain("/manga/$mangaId/${chapter.id}")
            name = chapterName(chapter)
            date_upload = parseChapterDate(chapter.publishedAt ?: chapter.createdAt)
        }
    }

    private suspend fun fetchChaptersFromChapterApi(mangaId: String): List<ChapterDto> = coroutineScope {
        val chapters = linkedMapOf<String, ChapterDto>()
        val firstPage = fetchChapterPage(mangaId, 1)
        val pages = listOf(firstPage) + (2..firstPage.pageCount.coerceAtLeast(1)).map { page ->
            async { fetchChapterPage(mangaId, page) }
        }.awaitAll()

        pages.forEach { payload ->
            payload.data.forEach { chapter ->
                chapters.putIfAbsent(chapter.id, chapter)
            }
        }

        chapters.values.sortedByDescending(::chapterSortValue)
    }

    private suspend fun fetchChapterPage(mangaId: String, page: Int): ChapterListDto {
        val url = "$webApiUrl/chapters/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", chapterListLimit.toString())
            .addQueryParameter("sort", "desc")
            .build()
        return client.get(url).parseAs()
    }

    private fun chapterSortValue(chapter: ChapterDto): Double {
        chapter.order?.let { return it }
        return chapterNumberRegex.find(chapter.chapterNumber)?.value?.toDoubleOrNull()
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

    private fun parseChapterDate(dateText: String?): Long = dateText
        ?.let(Instant::parseOrNull)
        ?.toEpochMilliseconds()
        ?: 0L

    // ============================== Pages =================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val imageUrls = parsePageUrls(document)

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun parsePageUrls(document: Document): List<String> = parsePageUrlsFromChapterData(document)
        .ifEmpty { parsePageUrlsFromNextImage(document) }

    private fun parsePageUrlsFromChapterData(document: Document): List<String> {
        val scriptText = document.select("script").joinToString("\n") { it.data() }

        return chapterPageUrlRegex.findAll(scriptText)
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
                resolved.takeIf { chapterImagePathRegex.containsMatchIn(it) }
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

    private fun extractMangaIdFromDocument(document: Document): String? = document.select("a[href*=/manga/]")
        .asSequence()
        .mapNotNull { it.absUrl("href").toHttpUrlOrNull()?.mangaIdOrNull() }
        .firstOrNull()

    private fun HttpUrl.mangaIdOrNull(): String? {
        val mangaIndex = pathSegments.indexOf("manga")
        val mangaId = if (mangaIndex != -1) pathSegments.getOrNull(mangaIndex + 1) else null

        return mangaId
            ?.takeIf(uuidRegex::matches)
            ?: pathSegments.firstOrNull(uuidRegex::matches)
    }

    private fun JsonObject.withMangaId(mangaId: String): JsonObject = JsonObject(this + ("mangaId" to mangaId.toJsonElement()))

    // =============================== Related ================================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.memo["mangaId"]?.stringOrNull
            ?: "$baseUrl${manga.url}".toHttpUrl().mangaIdOrNull()
            ?: throw IllegalArgumentException("Không tìm thấy manga id từ URL: ${manga.url}")
        val related = client.get("$apiUrl/mangas/$mangaId/related").parseAs<List<MangaDto>>()
        return related.map(::mangaFromDto)
    }

    private val popularLimit = 10
    private val latestLimit = 16
    private val searchLimit = 20
    private val chapterListLimit = 50
    private val filterLimit = 100

    private val uuidRegex = Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        RegexOption.IGNORE_CASE,
    )
    private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")
    private val chapterPageUrlRegex = Regex("""(?:/api/img\?[^"'\s]+|/?chapters/[^"'\\\s]+)""")
    private val chapterImagePathRegex = Regex("""(?:^|/)chapters/""")
}
