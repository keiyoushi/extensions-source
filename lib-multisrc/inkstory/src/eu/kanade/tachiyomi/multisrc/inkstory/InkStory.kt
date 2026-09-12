package eu.kanade.tachiyomi.multisrc.inkstory

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

abstract class InkStory :
    KeiSource(),
    ConfigurableSource {

    private val domain: String get() = baseUrl.toHttpUrl().topPrivateDomain() ?: baseUrl.toHttpUrl().host
    private val apiUrl: String get() = "https://api.$domain/v2"

    private val preferences by getPreferencesLazy {
        val keysToRemove = listOf(
            "inkstory_image_quality",
            "inkstory_image_type",
            "inkstory_image_width",
        )
        if (keysToRemove.any(::contains)) {
            edit().apply {
                keysToRemove.forEach(::remove)
            }.apply()
        }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        // User Agent required by source. Don't change
        set("User-Agent", "Tachiyomi (+https://github.com/keiyoushi/extensions-source)")
        set("Accept", "application/json, text/plain, */*")
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::imageDecryptInterceptor)
    }

    // ============================== Interceptors ===============================
    private fun imageDecryptInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response

        val contentType = response.body.contentType()
        if (contentType?.subtype == "json") {
            return response
        }

        if (detectImageCodec(response.request.url.toString()) != ImageCodec.XOR) {
            return response
        }

        val source = response.body.source()
        val buffer = Buffer()
        source.readAll(buffer)

        if (buffer.size < MIN_IMAGE_SIGNATURE_SIZE) {
            return response.newBuilder()
                .body(buffer.asResponseBody(contentType, buffer.size))
                .build()
        }

        // Peek the first bytes without consuming from buffer
        val peek = buffer.peek().readByteArray(MIN_IMAGE_SIGNATURE_SIZE.toLong())
        if (looksLikeImage(peek) != null) {
            return response.newBuilder()
                .body(buffer.asResponseBody(contentType, buffer.size))
                .build()
        }

        // Verify decrypted header
        val decryptedHeader = ByteArray(MIN_IMAGE_SIGNATURE_SIZE) { index ->
            (peek[index].toInt() xor SECRET_KEY_BYTES[index % SECRET_KEY_BYTES.size].toInt()).toByte()
        }

        val detectedType = looksLikeImage(decryptedHeader)
            ?: return response.newBuilder()
                .body(buffer.asResponseBody(contentType, buffer.size))
                .build()

        val payload = buffer.readByteArray()
        for (i in payload.indices) {
            payload[i] = (payload[i].toInt() xor SECRET_KEY_BYTES[i % SECRET_KEY_BYTES.size].toInt()).toByte()
        }

        val mediaType = detectedType.toMediaType()
        return response.newBuilder()
            .body(payload.toResponseBody(mediaType))
            .build()
    }

    private fun looksLikeImage(payload: ByteArray): String? {
        if (payload.size < MIN_IMAGE_SIGNATURE_SIZE) return null

        val isJpeg = payload[0] == 0xFF.toByte() && payload[1] == 0xD8.toByte() && payload[2] == 0xFF.toByte()
        if (isJpeg) return "image/jpeg"

        val isPng = payload[0] == 0x89.toByte() && payload[1] == 0x50.toByte() &&
            payload[2] == 0x4E.toByte() && payload[3] == 0x47.toByte()
        if (isPng) return "image/png"

        val isGif = payload[0] == 0x47.toByte() && payload[1] == 0x49.toByte() &&
            payload[2] == 0x46.toByte() && payload[3] == 0x38.toByte()
        if (isGif) return "image/gif"

        val isWebp = payload[0] == 0x52.toByte() && payload[1] == 0x49.toByte() &&
            payload[2] == 0x46.toByte() && payload[3] == 0x46.toByte() &&
            payload[8] == 0x57.toByte() && payload[9] == 0x45.toByte() &&
            payload[10] == 0x42.toByte() && payload[11] == 0x50.toByte()
        if (isWebp) return "image/webp"

        val isAvif = payload[4] == 0x66.toByte() && payload[5] == 0x74.toByte() &&
            payload[6] == 0x79.toByte() && payload[7] == 0x70.toByte() &&
            payload[8] == 0x61.toByte() && payload[9] == 0x76.toByte() &&
            payload[10] == 0x69.toByte()
        if (isAvif) return "image/avif"

        return null
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("viewsCount", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapter-update-feed")
            .addQueryParameter("onlyBorderChapters", "true")
            .addQueryParameter("page", (page - 1).coerceAtLeast(0).toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .build()
        return client.get(url).use { response ->
            val manga = response.parseAs<List<MangaFromSearchDto>>().map { it.book.toSManga() }
            MangasPage(manga, manga.size >= PAGE_SIZE)
        }
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("viewsCount", page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = "$apiUrl/books".toHttpUrl().newBuilder().apply {
            var sort = sortBy
            var order = "desc"

            filters?.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("labelsInclude", it) }
                        filter.excluded?.forEach { addQueryParameter("labelsExclude", it) }
                    }
                    is StrictLabelEqualFilter -> if (filter.state) {
                        addQueryParameter("strictLabelEqual", "true")
                    }
                    is FormatFilter -> filter.selected?.forEach { addQueryParameter("formats", it) }
                    is ContentStatusFilter -> filter.selected?.forEach { addQueryParameter("contentStatus", it) }
                    is CountryFilter -> filter.selected?.forEach { addQueryParameter("country", it) }
                    is StatusFilter -> filter.selected?.forEach { addQueryParameter("status", it) }
                    is ChaptersRangeFilter -> {
                        filter.minValue?.let { normalizeInt(it, 0, 100000)?.let { v -> addQueryParameter("chaptersCountMin", v) } }
                        filter.maxValue?.let { normalizeInt(it, 0, 100000)?.let { v -> addQueryParameter("chaptersCountMax", v) } }
                    }
                    is RatingRangeFilter -> {
                        filter.minValue?.let { normalizeDecimal(it)?.let { v -> addQueryParameter("averageRatingMin", v) } }
                        filter.maxValue?.let { normalizeDecimal(it)?.let { v -> addQueryParameter("averageRatingMax", v) } }
                    }
                    is YearRangeFilter -> {
                        filter.minValue?.let { normalizeInt(it, 1900, 2100)?.let { v -> addQueryParameter("yearMin", v) } }
                        filter.maxValue?.let { normalizeInt(it, 1900, 2100)?.let { v -> addQueryParameter("yearMax", v) } }
                    }
                    is OrderBy -> {
                        sort = filter.selected
                        order = filter.order
                    }
                    else -> {}
                }
            }
            if (query?.isNotBlank() == true) addQueryParameter("search", query)
            addQueryParameter("page", (page - 1).coerceAtLeast(0).toString())
            addQueryParameter("size", PAGE_SIZE.toString())
            addQueryParameter("sort", "$sort,$order")
        }.build()

        return client.get(url).use { response ->
            val mangas = response.parseAs<List<BookFromSearchDto>>().map { it.toSManga() }
            val totalHits = response.header("x-estimated-total-hits")?.toIntOrNull()
            val hasNextPage = if (totalHits != null) {
                page * PAGE_SIZE < totalHits
            } else {
                mangas.size >= PAGE_SIZE
            }
            MangasPage(mangas, hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "content" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = url.pathSegments[1]
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ===============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaAsync = async {
            if (fetchDetails) {
                val url = "$apiUrl/books/${manga.url}"
                client.get(url).parseAs<MangaFullDto>().toSManga()
            } else {
                manga
            }
        }
        val chaptersAsync = async {
            if (fetchChapters) {
                val branches = client.get("$apiUrl/branches?bookId=${manga.memo["id"]!!.string}&moderationStatus=APPROVED")
                    .parseAs<List<BranchDto>>()
                    .associate { it.id to it.publisherName() }
                val branchType = prefBranch()
                val branchQuery = prefBranchQuery()
                val url = "$apiUrl/chapters?bookId=${manga.memo["id"]!!.string}&moderationStatus=APPROVED"
                val data = client.get(url).parseAs<List<ChapterDto>>().map { it.toSChapter(branches, manga.url) }
                when (branchType) {
                    "all" -> data
                    "preferred" -> {
                        val preferred = if (branchQuery.isNotBlank()) {
                            data.filter { it.scanlator?.contains(branchQuery, ignoreCase = true) == true }.ifEmpty { data }
                        } else {
                            data
                        }
                        deduplicateChapters(preferred)
                    }
                    "latest" -> deduplicateChapters(data)
                    else -> data
                }
            } else {
                chapters
            }
        }
        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // ============================== Chapters ===============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/content/${chapter.memo["slug"]!!.string}/${chapter.url}"

    private fun deduplicateChapters(chaptersList: List<SChapter>): List<SChapter> {
        val latestMap = mutableMapOf<Float, SChapter>()
        val result = mutableListOf<SChapter>()

        for (chapter in chaptersList) {
            if (chapter.chapter_number >= 0f) {
                val existing = latestMap[chapter.chapter_number]
                if (existing == null || chapter.date_upload > existing.date_upload) {
                    latestMap[chapter.chapter_number] = chapter
                }
            }
        }

        val seen = mutableSetOf<Float>()
        for (chapter in chaptersList) {
            if (chapter.chapter_number < 0f) {
                result.add(chapter)
            } else if (seen.add(chapter.chapter_number)) {
                latestMap[chapter.chapter_number]?.let(result::add)
            }
        }

        return result
    }

    // ============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/chapters/${chapter.url}"
        val chapter = client.get(url).parseAs<PagesDto>()

        return chapter.pages
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .mapIndexedNotNull { index, page ->
                page.image?.takeIf(String::isNotBlank)?.let { imageUrl ->
                    val normalized = normalizeImageUrl(imageUrl)
                    Page(index = index, imageUrl = normalized)
                }
            }
    }

    private fun normalizeImageUrl(imageUrl: String): String = if (detectImageCodec(imageUrl) == ImageCodec.SEC) {
        replaceFileNameMode(imageUrl)
    } else {
        imageUrl
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

    private fun replaceFileNameMode(imageUrl: String, replacementMode: Char = 'x'): String {
        val lastSlash = imageUrl.lastIndexOf('/')
        if (lastSlash == -1) return imageUrl
        val modeIndex = lastSlash + 1 + IMAGE_MODE_INDEX
        if (modeIndex >= imageUrl.length) return imageUrl

        val chars = imageUrl.toCharArray()
        chars[modeIndex] = replacementMode
        return String(chars)
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$apiUrl/labels")
            .parseAs<List<GenresDto>>()
            .filter { it.kind == "GENRE" }
            .map { it.name to it.slug }

        return FiltersData(
            genres = genres,
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters.add(OrderBy())

        data?.parseAs<FiltersData>()?.let {
            if (it.genres?.isNotEmpty() == true) {
                filters.add(GenreFilter(it.genres))
                filters.add(Filter.Separator())
                filters.add(StrictLabelEqualFilter())
            }
        }

        filters.addAll(
            listOf(
                StatusFilter(),
                CountryFilter(),
                ContentStatusFilter(),
                FormatFilter(),
                RatingRangeFilter(),
                YearRangeFilter(),
                ChaptersRangeFilter(),
            ),
        )

        return FilterList(filters)
    }

    // ============================== Preferences ===============================
    private fun prefBranch(): String = preferences.getString(PREF_CHAPTER_BRANCH_MODE, DEFAULT_CHAPTER_BRANCH_MODE) ?: DEFAULT_CHAPTER_BRANCH_MODE
    private fun prefBranchQuery(): String = preferences.getString(PREF_PREFERRED_BRANCH_QUERY, DEFAULT_PREFERRED_BRANCH_QUERY) ?: DEFAULT_PREFERRED_BRANCH_QUERY

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_BRANCH_MODE
            title = "Режим веток глав"
            entries = BRANCH_MODE.map { it.first }.toTypedArray()
            entryValues = BRANCH_MODE.map { it.second }.toTypedArray()
            summary = "%s"
            setDefaultValue(DEFAULT_CHAPTER_BRANCH_MODE)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_BRANCH_QUERY
            title = "Предпочитаемая ветка"
            dialogTitle = "Предпочитаемая ветка"
            summary = "Используется в режиме \"Предпочитаемая ветка\" (поиск по части названия команды)"
            setDefaultValue(DEFAULT_PREFERRED_BRANCH_QUERY)
        }.let(screen::addPreference)
    }

    // ============================== Utilities ===============================
    private fun normalizeDecimal(rawValue: String?, min: Double = 0.0, max: Double = 10.0): String? {
        val value = rawValue?.trim()?.replace(',', '.')?.takeIf(String::isNotEmpty) ?: return null
        val parsed = value.toDoubleOrNull() ?: return null
        if (parsed !in min..max) return null
        return if (parsed % 1.0 == 0.0) parsed.toLong().toString() else parsed.toString()
    }

    private fun normalizeInt(rawValue: String?, min: Int, max: Int): String? {
        val value = rawValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val parsed = value.toIntOrNull() ?: return null
        if (parsed !in min..max) return null
        return parsed.toString()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val IMAGE_NAME_LENGTH = 36
        private const val IMAGE_MODE_INDEX = 14
        private const val MIN_IMAGE_SIGNATURE_SIZE = 12
        private const val PREF_CHAPTER_BRANCH_MODE = "inkstory_chapter_branch_mode"
        private const val PREF_PREFERRED_BRANCH_QUERY = "inkstory_preferred_branch_query"
        private const val DEFAULT_CHAPTER_BRANCH_MODE = "all"
        private const val DEFAULT_PREFERRED_BRANCH_QUERY = ""
        private const val SECRET_KEY = "UySkp0BzPhwlvP2V"
        private val SECRET_KEY_BYTES = SECRET_KEY.toByteArray()
        private val BRANCH_MODE = arrayOf(
            "Все ветки" to "all",
            "Последняя версия главы" to "latest",
            "Предпочитаемая ветка" to "preferred",
        )
    }
}
