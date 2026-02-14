package eu.kanade.tachiyomi.extension.ru.inkstory

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.util.LinkedHashMap

class InkStory :
    HttpSource(),
    ConfigurableSource {

    override val name = "InkStory"
    override val baseUrl = "https://inkstory.net"
    override val lang = "ru"
    override val supportsLatest = true

    private val apiBaseUrl = "https://api.inkstory.net"
    private val json: Json by injectLazy()
    private val preferences: SharedPreferences by getPreferencesLazy()

    private val secretKeyByChapter = object : LinkedHashMap<String, String>(SECRET_KEY_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > SECRET_KEY_CACHE_MAX
    }
    private val secretKeyLock = Any()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageDecryptInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json, text/plain, */*")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_QUALITY
            title = "Image quality"
            entries = IMAGE_QUALITY_OPTIONS
            entryValues = IMAGE_QUALITY_OPTIONS
            summary = "%s"
            setDefaultValue(DEFAULT_IMAGE_QUALITY)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_IMAGE_TYPE
            title = "Image type"
            entries = IMAGE_TYPE_OPTIONS
            entryValues = IMAGE_TYPE_OPTIONS
            summary = "%s"
            setDefaultValue(DEFAULT_IMAGE_TYPE)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_IMAGE_WIDTH
            title = "Image width"
            entries = IMAGE_WIDTH_OPTIONS
            entryValues = IMAGE_WIDTH_OPTIONS
            summary = "%s"
            setDefaultValue(DEFAULT_IMAGE_WIDTH)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_BRANCH_MODE
            title = "\u0420\u0435\u0436\u0438\u043C \u0432\u0435\u0442\u043E\u043A \u0433\u043B\u0430\u0432"
            entries = BRANCH_MODE_ENTRIES
            entryValues = BRANCH_MODE_VALUES
            summary = "%s"
            setDefaultValue(DEFAULT_CHAPTER_BRANCH_MODE)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_BRANCH_QUERY
            title = "\u041F\u0440\u0435\u0434\u043F\u043E\u0447\u0438\u0442\u0430\u0435\u043C\u0430\u044F \u0432\u0435\u0442\u043A\u0430"
            dialogTitle = "\u041F\u0440\u0435\u0434\u043F\u043E\u0447\u0438\u0442\u0430\u0435\u043C\u0430\u044F \u0432\u0435\u0442\u043A\u0430"
            summary = "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F \u0432 \u0440\u0435\u0436\u0438\u043C\u0435 \"\u041F\u0440\u0435\u0434\u043F\u043E\u0447\u0438\u0442\u0430\u0435\u043C\u0430\u044F \u0432\u0435\u0442\u043A\u0430\" (\u043F\u043E\u0438\u0441\u043A \u043F\u043E \u0447\u0430\u0441\u0442\u0438 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u044F \u043A\u043E\u043C\u0430\u043D\u0434\u044B)"
            setDefaultValue(DEFAULT_PREFERRED_BRANCH_QUERY)
        }.let(screen::addPreference)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFieldFilter(),
        SortOrderFilter(),
        StatusFilter(),
        CountryFilter(),
        ContentStatusFilter(),
        FormatFilter(),
        GenreIncludeFilter(),
        StrictLabelEqualFilter(),
        GenreExcludeFilter(),
        RatingRangeFilter(),
        YearRangeFilter(),
        ChaptersRangeFilter(),
    )

    override fun popularMangaRequest(page: Int): Request = booksRequest(
        page = page,
        sort = POPULAR_SORT,
    )

    override fun popularMangaParse(response: Response): MangasPage = booksParse(response)

    override fun latestUpdatesRequest(page: Int): Request = booksRequest(
        page = page,
        sort = LATEST_SORT,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = booksParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val parsedFilters = InkStorySearchFilters.from(if (filters.isEmpty()) getFilterList() else filters)
        val normalizedQuery = query.trim().takeIf(String::isNotEmpty)
        val sort = when {
            normalizedQuery == null && !parsedFilters.hasActiveFilters() -> POPULAR_SORT
            else -> parsedFilters.sort
        }

        return booksRequest(
            page = page,
            sort = sort,
            query = normalizedQuery,
            searchFilters = parsedFilters,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = booksParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast('/')
        return GET("$apiBaseUrl/v2/books/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val book = json.decodeFromString<BookDto>(response.body.string())

        val authorNames = book.relations
            .asSequence()
            .filter { it.type == "AUTHOR" }
            .mapNotNull { it.publisher?.name?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .toList()
            .joinToString(", ")
            .ifBlank { null }

        val artistNames = book.relations
            .asSequence()
            .filter { it.type == "ARTIST" }
            .mapNotNull { it.publisher?.name?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .toList()
            .joinToString(", ")
            .ifBlank { null }

        val genres = buildList {
            addAll(book.labels.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) })
            addAll(book.formats.map { format -> format.lowercase().replace('_', ' ') })
        }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

        val links = book.externalLinks
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()

        return SManga.create().apply {
            url = "/content/${book.slug}"
            title = resolveTitle(book.name, book.slug)
            thumbnail_url = book.poster
            description = buildString {
                book.description?.trim()?.takeIf(String::isNotEmpty)?.let { append(it) }
                if (links.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("External links:\n")
                    append(links.joinToString("\n"))
                }
            }.ifBlank { null }
            author = authorNames
            artist = artistNames
            genre = genres
            status = parseStatus(book.status)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val book = json.decodeFromString<BookDto>(response.body.string())
        val branchMap = fetchBranchNames(book.id)

        val chapters = client.newCall(GET("$apiBaseUrl/v2/chapters?bookId=${book.id}", headers))
            .execute()
            .use { chaptersResponse ->
                json.decodeFromString<List<ChapterDto>>(chaptersResponse.body.string())
            }
            .let { preprocessChapters(it, branchMap) }

        return chapters.map { chapter ->
            SChapter.create().apply {
                val vol = formatDecimal(chapter.volume)
                val num = formatDecimal(chapter.number)
                val baseChapterName = when {
                    vol != null && num != null -> "\u0422\u043E\u043C $vol \u0413\u043B\u0430\u0432\u0430 $num"
                    num != null -> "\u0413\u043B\u0430\u0432\u0430 $num"
                    vol != null -> "\u0422\u043E\u043C $vol"
                    else -> "\u0413\u043B\u0430\u0432\u0430"
                }
                val subtitle = listOf(chapter.name, chapter.title)
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.trim()
                val readableName = if (!subtitle.isNullOrBlank() && !baseChapterName.equals(subtitle, true)) {
                    "$baseChapterName - $subtitle"
                } else {
                    baseChapterName
                }

                setUrlWithoutDomain("/chapter/${chapter.id}")
                name = if (chapter.donut == true) "\uD83D\uDD12 $readableName" else readableName
                chapter_number = chapter.number?.toFloat() ?: -1f
                date_upload = parseDate(chapter.createdAt)
                scanlator = resolveBranchName(chapter.branchId, branchMap)
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast('/')
        return GET("$apiBaseUrl/v2/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = json.decodeFromString<ChapterDto>(response.body.string())
        val chapterId = chapter.id
        val secretKey = getSecretKey(chapterId)
        val pages = chapter.pages
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .mapIndexedNotNull { index, page ->
                page.image?.takeIf(String::isNotBlank)?.let { imageUrl ->
                    val normalized = normalizeImageUrl(imageUrl)
                    Page(
                        index = index,
                        url = if (normalized.requiresXorDecode) encodePageMeta(chapterId, secretKey) else "",
                        imageUrl = normalized.url,
                    )
                }
            }

        if (pages.isEmpty()) {
            throw Exception("No pages found for this chapter")
        }

        return pages
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: error("Image URL is missing")
        val (chapterId, imageKey) = decodePageMeta(page.url)
        val imageHeaders = headersBuilder().apply {
            if (imageKey.isNotBlank()) {
                add(IMAGE_KEY_HEADER, imageKey)
            }
            if (chapterId.isNotBlank()) {
                add(IMAGE_CHAPTER_HEADER, chapterId)
            }
        }.build()
        return GET(imageUrl, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun booksRequest(
        page: Int,
        sort: String,
        query: String? = null,
        searchFilters: InkStorySearchFilters? = null,
    ): Request {
        val url = "$apiBaseUrl/v2/books".toHttpUrl().newBuilder()
            .addQueryParameter("size", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sort)
            .apply {
                if (!query.isNullOrBlank()) {
                    addQueryParameter("search", query)
                }
                searchFilters?.statuses?.forEach { addQueryParameter("status", it) }
                searchFilters?.countries?.forEach { addQueryParameter("country", it) }
                searchFilters?.contentStatuses?.forEach { addQueryParameter("contentStatus", it) }
                searchFilters?.formats?.forEach { addQueryParameter("formats", it) }
                searchFilters?.labelIncludes?.forEach { addQueryParameter("labelsInclude", it) }
                searchFilters?.labelExcludes?.forEach { addQueryParameter("labelsExclude", it) }
                if (searchFilters?.strictLabelEqual == true) {
                    addQueryParameter("strictLabelEqual", "true")
                }
                searchFilters?.averageRatingMin?.let { addQueryParameter("averageRatingMin", it) }
                searchFilters?.averageRatingMax?.let { addQueryParameter("averageRatingMax", it) }
                searchFilters?.yearMin?.let { addQueryParameter("yearMin", it) }
                searchFilters?.yearMax?.let { addQueryParameter("yearMax", it) }
                searchFilters?.chaptersCountMin?.let { addQueryParameter("chaptersCountMin", it) }
                searchFilters?.chaptersCountMax?.let { addQueryParameter("chaptersCountMax", it) }
            }
            .build()

        return GET(url, headers)
    }

    private fun booksParse(response: Response): MangasPage {
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val totalHits = response.header("x-estimated-total-hits")?.toIntOrNull()
        val books = json.decodeFromString<List<BookDto>>(response.body.string())

        val mangas = books.map { book ->
            SManga.create().apply {
                url = "/content/${book.slug}"
                title = resolveTitle(book.name, book.slug)
                thumbnail_url = book.poster
            }
        }

        val hasNextPage = if (totalHits != null) {
            currentPage * PAGE_SIZE < totalHits
        } else {
            mangas.size >= PAGE_SIZE
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun fetchBranchNames(bookId: String): Map<String, String?> {
        val branches = client.newCall(GET("$apiBaseUrl/v2/branches?bookId=$bookId", headers))
            .execute()
            .use { branchResponse ->
                json.decodeFromString<List<BranchDto>>(branchResponse.body.string())
            }

        return branches.associate { branch ->
            val name = branch.publishers
                .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
            branch.id to name
        }
    }

    private fun preprocessChapters(
        chapters: List<ChapterDto>,
        branchMap: Map<String, String?>,
    ): List<ChapterDto> {
        val processed = when (chapterBranchMode()) {
            ChapterBranchMode.ALL -> chapters
            ChapterBranchMode.LATEST -> deduplicateChapters(chapters)
            ChapterBranchMode.PREFERRED -> preferredBranchChapters(chapters, branchMap)
        }

        return processed.sortedWith(
            compareByDescending<ChapterDto> { it.volume ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.number ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { parseDate(it.createdAt) },
        )
    }

    private fun deduplicateChapters(chapters: List<ChapterDto>): List<ChapterDto> {
        val byIdentity = chapters
            .filter { it.volume != null || it.number != null }
            .groupBy { ChapterIdentity(it.volume, it.number) }
            .values
            .mapNotNull { chapterGroup ->
                chapterGroup.maxByOrNull { parseDate(it.createdAt) }
            }

        val withoutIdentity = chapters.filter { it.volume == null && it.number == null }
        return byIdentity + withoutIdentity
    }

    private fun preferredBranchChapters(
        chapters: List<ChapterDto>,
        branchMap: Map<String, String?>,
    ): List<ChapterDto> {
        val preferredQuery = preferredBranchQuery()
        if (preferredQuery.isNullOrBlank()) {
            return deduplicateChapters(chapters)
        }

        val preferred = chapters.filter { chapter ->
            resolveBranchName(chapter.branchId, branchMap)
                ?.contains(preferredQuery, ignoreCase = true) == true
        }

        val source = if (preferred.isNotEmpty()) preferred else chapters
        return deduplicateChapters(source)
    }

    private fun chapterBranchMode(): ChapterBranchMode {
        val value = preferences.getString(PREF_CHAPTER_BRANCH_MODE, DEFAULT_CHAPTER_BRANCH_MODE)
        return ChapterBranchMode.fromValue(value)
    }

    private fun preferredBranchQuery(): String? = preferences.getString(PREF_PREFERRED_BRANCH_QUERY, DEFAULT_PREFERRED_BRANCH_QUERY)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun resolveBranchName(branchId: String?, branchMap: Map<String, String?>): String? {
        if (branchId.isNullOrBlank()) return null
        return branchMap[branchId]?.takeIf(String::isNotBlank) ?: "\u0412\u0435\u0442\u043A\u0430 $branchId"
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ONGOING" -> SManga.ONGOING
        "DONE" -> SManga.COMPLETED
        "FROZEN" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun resolveTitle(name: NameDto, fallbackSlug: String): String = name.ru?.takeIf(String::isNotBlank)
        ?: name.en?.takeIf(String::isNotBlank)
        ?: name.original?.takeIf(String::isNotBlank)
        ?: fallbackSlug

    private fun formatDecimal(value: Double?): String? = value?.let {
        if (it % 1.0 == 0.0) {
            it.toLong().toString()
        } else {
            it.toString().trimEnd('0').trimEnd('.')
        }
    }

    private fun parseDate(value: String?): Long = runCatching { value?.let { Instant.parse(it).toEpochMilli() } ?: 0L }
        .getOrDefault(0L)

    private fun getSecretKey(chapterId: String, forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            synchronized(secretKeyLock) {
                secretKeyByChapter[chapterId]?.let { return it }
            }
        }

        val fetched = fetchSecretKey(chapterId)
        if (fetched.isNullOrBlank()) {
            synchronized(secretKeyLock) {
                return secretKeyByChapter[chapterId] ?: DEFAULT_SECRET_KEY
            }
        }

        synchronized(secretKeyLock) {
            secretKeyByChapter[chapterId] = fetched
        }
        return fetched
    }

    private fun fetchSecretKey(chapterId: String): String? {
        val request = GET("$baseUrl/chapter/$chapterId", headers)
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body.string()
                SECRET_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
            }
        }.getOrNull()
    }

    private fun encodePageMeta(chapterId: String, key: String): String = "$chapterId$PAGE_META_DELIMITER$key"

    private fun decodePageMeta(meta: String): Pair<String, String> {
        if (meta.isBlank()) return "" to ""
        val splitIndex = meta.indexOf(PAGE_META_DELIMITER)
        if (splitIndex == -1) return "" to meta
        val chapterId = meta.substring(0, splitIndex)
        val key = meta.substring(splitIndex + PAGE_META_DELIMITER.length)
        return chapterId to key
    }

    private fun normalizeImageUrl(rawImageUrl: String): NormalizedImage {
        var imageUrl = rawImageUrl
        var codec = detectImageCodec(imageUrl)

        if (codec == ImageCodec.SEC) {
            imageUrl = replaceFileNameMode(imageUrl, 'x')
            codec = ImageCodec.XOR
        }

        if (codec != ImageCodec.XOR) {
            return NormalizedImage(url = imageUrl, requiresXorDecode = false)
        }

        val parsed = imageUrl.toHttpUrlOrNull()
            ?: return NormalizedImage(url = imageUrl, requiresXorDecode = true)

        val width = preferenceValue(
            key = PREF_IMAGE_WIDTH,
            allowedValues = IMAGE_WIDTH_OPTIONS.toSet(),
            defaultValue = DEFAULT_IMAGE_WIDTH,
        )
        val type = preferenceValue(
            key = PREF_IMAGE_TYPE,
            allowedValues = IMAGE_TYPE_OPTIONS.toSet(),
            defaultValue = DEFAULT_IMAGE_TYPE,
        )
        val quality = preferenceValue(
            key = PREF_IMAGE_QUALITY,
            allowedValues = IMAGE_QUALITY_OPTIONS.toSet(),
            defaultValue = DEFAULT_IMAGE_QUALITY,
        )

        val withParams = parsed.newBuilder().apply {
            if (parsed.queryParameter("width") == null) addQueryParameter("width", width)
            if (parsed.queryParameter("type") == null) addQueryParameter("type", type)
            if (parsed.queryParameter("quality") == null) addQueryParameter("quality", quality)
        }.build().toString()

        return NormalizedImage(url = withParams, requiresXorDecode = true)
    }

    private fun preferenceValue(key: String, allowedValues: Set<String>, defaultValue: String): String {
        val value = preferences.getString(key, defaultValue)
        return if (value in allowedValues) value!! else defaultValue
    }

    private fun detectImageCodec(imageUrl: String): ImageCodec? {
        val fileName = imageUrl.substringAfterLast('/').substringBefore('?')
        val baseName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        if (baseName.length != IMAGE_NAME_LENGTH) return null
        return when (baseName.getOrNull(IMAGE_MODE_INDEX)) {
            's' -> ImageCodec.SEC
            'x' -> ImageCodec.XOR
            else -> null
        }
    }

    private fun replaceFileNameMode(imageUrl: String, replacementMode: Char): String {
        val parsed = imageUrl.toHttpUrlOrNull() ?: return imageUrl
        val pathSegments = parsed.pathSegments.toMutableList()
        val fileName = pathSegments.lastOrNull() ?: return imageUrl
        val baseName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        if (baseName.length != IMAGE_NAME_LENGTH || baseName.getOrNull(IMAGE_MODE_INDEX) == null) {
            return imageUrl
        }
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val updatedBaseName = baseName.substring(0, IMAGE_MODE_INDEX) +
            replacementMode +
            baseName.substring(IMAGE_MODE_INDEX + 1)
        val updatedName = if (ext.isBlank()) updatedBaseName else "$updatedBaseName.$ext"
        pathSegments[pathSegments.lastIndex] = updatedName
        return parsed.newBuilder()
            .encodedPath("/" + pathSegments.joinToString("/"))
            .build()
            .toString()
    }

    private fun decodeXor(payload: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray()
        if (keyBytes.isEmpty()) return payload
        return ByteArray(payload.size) { index ->
            (payload[index].toInt() xor keyBytes[index % keyBytes.size].toInt()).toByte()
        }
    }

    private fun looksLikeImage(payload: ByteArray): Boolean {
        if (payload.size < MIN_IMAGE_SIGNATURE_SIZE) return false

        val isJpeg = payload[0] == 0xFF.toByte() && payload[1] == 0xD8.toByte() && payload[2] == 0xFF.toByte()
        if (isJpeg) return true

        val isPng = payload[0] == 0x89.toByte() && payload[1] == 0x50.toByte() &&
            payload[2] == 0x4E.toByte() && payload[3] == 0x47.toByte()
        if (isPng) return true

        val isGif = payload[0] == 0x47.toByte() && payload[1] == 0x49.toByte() &&
            payload[2] == 0x46.toByte() && payload[3] == 0x38.toByte()
        if (isGif) return true

        val isWebp = payload[0] == 0x52.toByte() && payload[1] == 0x49.toByte() &&
            payload[2] == 0x46.toByte() && payload[3] == 0x46.toByte() &&
            payload[8] == 0x57.toByte() && payload[9] == 0x45.toByte() &&
            payload[10] == 0x42.toByte() && payload[11] == 0x50.toByte()

        return isWebp
    }

    private data class NormalizedImage(
        val url: String,
        val requiresXorDecode: Boolean,
    )

    private data class ChapterIdentity(
        val volume: Double?,
        val number: Double?,
    )

    private enum class ImageCodec {
        SEC,
        XOR,
    }

    private enum class ChapterBranchMode(val value: String) {
        ALL("all"),
        LATEST("latest"),
        PREFERRED("preferred"),
        ;

        companion object {
            fun fromValue(value: String?): ChapterBranchMode = values().firstOrNull { it.value == value } ?: ALL
        }
    }

    @Serializable
    data class BookDto(
        val id: String,
        val slug: String,
        val name: NameDto = NameDto(),
        val poster: String? = null,
        val description: String? = null,
        val status: String? = null,
        val labels: List<LabelDto> = emptyList(),
        val formats: List<String> = emptyList(),
        val relations: List<RelationDto> = emptyList(),
        val externalLinks: List<String> = emptyList(),
    )

    @Serializable
    data class NameDto(
        val en: String? = null,
        val ru: String? = null,
        val original: String? = null,
    )

    @Serializable
    data class LabelDto(
        val name: String? = null,
    )

    @Serializable
    data class RelationDto(
        val type: String? = null,
        val publisher: PublisherDto? = null,
    )

    @Serializable
    data class PublisherDto(
        val name: String? = null,
    )

    @Serializable
    data class BranchDto(
        val id: String,
        val publishers: List<PublisherDto> = emptyList(),
    )

    @Serializable
    data class ChapterDto(
        val id: String,
        val name: String? = null,
        val title: String? = null,
        val number: Double? = null,
        val volume: Double? = null,
        val branchId: String? = null,
        val createdAt: String? = null,
        val donut: Boolean? = null,
        val pages: List<ChapterPageDto> = emptyList(),
    )

    @Serializable
    data class ChapterPageDto(
        val index: Int? = null,
        val image: String? = null,
    )

    companion object {
        private const val PAGE_SIZE = 30

        private const val POPULAR_SORT = "viewsCount,desc"
        private const val LATEST_SORT = "updatedAt,desc"

        private const val IMAGE_NAME_LENGTH = 36
        private const val IMAGE_MODE_INDEX = 14
        private const val MIN_IMAGE_SIGNATURE_SIZE = 12

        private const val IMAGE_KEY_HEADER = "X-InkStory-Xor-Key"
        private const val IMAGE_CHAPTER_HEADER = "X-InkStory-Chapter-Id"

        private const val PREF_IMAGE_QUALITY = "inkstory_image_quality"
        private const val PREF_IMAGE_TYPE = "inkstory_image_type"
        private const val PREF_IMAGE_WIDTH = "inkstory_image_width"
        private const val PREF_CHAPTER_BRANCH_MODE = "inkstory_chapter_branch_mode"
        private const val PREF_PREFERRED_BRANCH_QUERY = "inkstory_preferred_branch_query"

        private const val DEFAULT_IMAGE_QUALITY = "75"
        private const val DEFAULT_IMAGE_TYPE = "webp"
        private const val DEFAULT_IMAGE_WIDTH = "1600"
        private const val DEFAULT_CHAPTER_BRANCH_MODE = "all"
        private const val DEFAULT_PREFERRED_BRANCH_QUERY = ""

        private const val SECRET_KEY_CACHE_MAX = 500
        private const val DEFAULT_SECRET_KEY = "UySkp0BzPhwlvP2V"
        private const val PAGE_META_DELIMITER = "|"

        private val IMAGE_QUALITY_OPTIONS = arrayOf("50", "75", "100")
        private val IMAGE_TYPE_OPTIONS = arrayOf("webp", "jpeg")
        private val IMAGE_WIDTH_OPTIONS = arrayOf("700", "1200", "1600")
        private val BRANCH_MODE_ENTRIES = arrayOf(
            "\u0412\u0441\u0435 \u0432\u0435\u0442\u043A\u0438",
            "\u041F\u043E\u0441\u043B\u0435\u0434\u043D\u044F\u044F \u0432\u0435\u0440\u0441\u0438\u044F \u0433\u043B\u0430\u0432\u044B",
            "\u041F\u0440\u0435\u0434\u043F\u043E\u0447\u0438\u0442\u0430\u0435\u043C\u0430\u044F \u0432\u0435\u0442\u043A\u0430",
        )
        private val BRANCH_MODE_VALUES = arrayOf(
            ChapterBranchMode.ALL.value,
            ChapterBranchMode.LATEST.value,
            ChapterBranchMode.PREFERRED.value,
        )

        private val SECRET_KEY_REGEX = "\"secret-key\",\"([^\"]+)\"".toRegex()
    }

    private inner class ImageDecryptInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val chapterId = request.header(IMAGE_CHAPTER_HEADER).orEmpty()
            val initialKey = request.header(IMAGE_KEY_HEADER).orEmpty()

            val cleanRequest = request.newBuilder()
                .removeHeader(IMAGE_KEY_HEADER)
                .removeHeader(IMAGE_CHAPTER_HEADER)
                .build()

            val response = chain.proceed(cleanRequest)
            if (!response.isSuccessful) return response

            var key = initialKey.ifBlank {
                if (chapterId.isNotBlank()) getSecretKey(chapterId) else ""
            }
            if (key.isBlank()) return response

            val sourceBody = response.body
            val encryptedPayload = sourceBody.bytes()
            if (encryptedPayload.isEmpty()) {
                return response.newBuilder()
                    .body(encryptedPayload.toResponseBody(sourceBody.contentType()))
                    .build()
            }

            var decodedPayload = decodeXor(encryptedPayload, key)
            var isDecodedImage = looksLikeImage(decodedPayload)

            if (!isDecodedImage && chapterId.isNotBlank()) {
                val refreshedKey = getSecretKey(chapterId, forceRefresh = true)
                if (refreshedKey != key) {
                    val refreshedPayload = decodeXor(encryptedPayload, refreshedKey)
                    if (looksLikeImage(refreshedPayload)) {
                        decodedPayload = refreshedPayload
                        isDecodedImage = true
                    }
                }
            }

            if (!isDecodedImage) {
                return response.newBuilder()
                    .body(encryptedPayload.toResponseBody(sourceBody.contentType()))
                    .build()
            }

            val contentType = sourceBody.contentType() ?: "image/jpeg".toMediaTypeOrNull()
            val decodedBody = decodedPayload.toResponseBody(contentType)
            return response.newBuilder().body(decodedBody).build()
        }
    }
}
