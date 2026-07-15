package eu.kanade.tachiyomi.extension.en.mangalix

import android.util.JsonReader
import android.util.JsonToken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

@Source
abstract class Mangalix : HttpSource() {

    override val supportsLatest = true

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = network.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    private val archiveHeaders by lazy {
        headersBuilder()
            .set("Accept", "application/gzip")
            .set("Accept-Encoding", "identity")
            .build()
    }

    private val imageHeaders by lazy {
        headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
    }

    @Volatile
    private var catalogCache: CatalogCache? = null

    @Volatile
    private var archiveCache: ArchiveCache? = null

    @Volatile
    private var chapterCache: ChapterCache? = null

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.fromCallable {
        getCatalog().toPage(page)
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.fromCallable {
        getCatalog().sortedByDescending(MangaDto::latestTimestamp).toPage(page)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        var result = getCatalog().asSequence()
            .filter { manga -> query.isBlank() || manga.matchesQuery(query) }
            .filter { manga -> statusFilter?.matches(manga.status) != false }
            .filter { manga -> genreFilter?.matches(manga.genres) != false }
            .toList()

        result = when (sortFilter?.selected) {
            SortFilter.LATEST -> result.sortedByDescending(MangaDto::latestTimestamp)
            SortFilter.RATING -> result.sortedByDescending(MangaDto::rating)
            SortFilter.TITLE -> result.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SortFilter.RELEASE_YEAR -> result.sortedByDescending(MangaDto::releaseYear)
            else -> result
        }

        if (sortFilter?.ascending == true) result = result.reversed()
        result.toPage(page)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.fromCallable {
        getCatalog().firstOrNull { it.slug == manga.slug() }?.toSManga(baseUrl) ?: manga
    }

    override fun mangaDetailsRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.slug()}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val slug = manga.slug()
        val archive = getArchive()
        getChapters(archive, slug).distinctBy { it.id to it.number }.map { chapter ->
            SChapter.create().apply {
                val number = chapter.number.toString().removeSuffix(".0")
                url = "$baseUrl/manga/$slug/chapter/$number".toHttpUrl().newBuilder()
                    .addQueryParameter("id", chapter.id)
                    .build()
                    .toString()
                    .removePrefix(baseUrl)
                name = chapter.title.ifBlank { "Chapter $number" }
                chapter_number = chapter.number.toFloat()
                date_upload = chapter.releaseDate.toMangaTimestamp()
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String = (baseUrl + chapter.url).toHttpUrl().newBuilder()
        .query(null)
        .build()
        .toString()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val slug = chapterUrl.pathSegments.getOrNull(1)
            ?: throw IOException("Missing manga slug")
        val chapterNumber = chapterUrl.pathSegments.getOrNull(3)?.toDoubleOrNull()
            ?: throw IOException("Missing chapter number")
        val chapterId = chapterUrl.queryParameter("id")
            ?: throw IOException("Missing chapter id")

        getChapters(getArchive(), slug)
            .firstOrNull { it.id == chapterId && it.number == chapterNumber }
            ?.pages
            .orEmpty()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl.resolveImageUrl())
            }
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    @Synchronized
    private fun getCatalog(): List<MangaDto> {
        val cached = catalogCache
        if (cached != null && System.currentTimeMillis() - cached.savedAt < CATALOG_CACHE_LIFETIME) {
            return cached.mangas
        }

        return loadCatalog().also {
            catalogCache = CatalogCache(it, System.currentTimeMillis())
        }
    }

    private fun loadCatalog(): List<MangaDto> {
        val document = client.newCall(GET(baseUrl, headers)).execute().use { response ->
            response.requireSuccess()
            response.asJsoup()
        }
        val scriptUrl = document.selectFirst("script[type=module][src*=assets/main-], script[src*=assets/main-]")
            ?.absUrl("src")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("Main script not found")

        val script = client.newCall(GET(scriptUrl, headers)).execute().use { response ->
            response.requireSuccess()
            response.body.string()
        }

        CATALOG_START_REGEX.findAll(script).forEach { match ->
            val element = runCatching {
                JsLiteralParser(script, match.range.first).parse()
            }.getOrNull() ?: return@forEach
            val candidate = runCatching {
                element.parseAs<List<MangaDto>>()
            }.getOrNull() ?: return@forEach

            if (candidate.isNotEmpty() && candidate.all { it.slug.isNotBlank() && it.title.isNotBlank() }) {
                return candidate.distinctBy(MangaDto::slug)
            }
        }

        throw IOException("Manga catalog not found")
    }

    private fun MangaDto.matchesQuery(query: String): Boolean {
        val terms = query.trim().lowercase(Locale.ROOT).split(WHITESPACE_REGEX)
        val searchable = "$title $author $slug".lowercase(Locale.ROOT)
        return terms.all(searchable::contains)
    }

    private fun List<MangaDto>.toPage(page: Int): MangasPage {
        val start = ((page - 1).coerceAtLeast(0)) * PAGE_SIZE
        if (start >= size) return MangasPage(emptyList(), false)
        val end = (start + PAGE_SIZE).coerceAtMost(size)
        return MangasPage(subList(start, end).map { it.toSManga(baseUrl) }, end < size)
    }

    @Synchronized
    private fun getArchive(): ByteArray {
        val cached = archiveCache
        if (cached != null && System.currentTimeMillis() - cached.savedAt < ARCHIVE_CACHE_LIFETIME) {
            return cached.bytes
        }

        return client.newCall(GET("$baseUrl/chapters.json.gz", archiveHeaders)).execute().use { response ->
            response.requireSuccess()
            response.body.bytes().also(::cacheArchive)
        }
    }

    private fun cacheArchive(bytes: ByteArray) {
        if (bytes.size < 2 || bytes[0] != GZIP_MAGIC_FIRST || bytes[1] != GZIP_MAGIC_SECOND) {
            throw IOException("Invalid chapter archive")
        }
        archiveCache = ArchiveCache(bytes, System.currentTimeMillis())
        chapterCache = null
    }

    @Synchronized
    private fun getChapters(archive: ByteArray, slug: String): List<ChapterDto> {
        chapterCache?.takeIf { it.archive === archive && it.slug == slug }?.let { return it.chapters }

        return readChapters(archive, slug).also {
            chapterCache = ChapterCache(archive, slug, it)
        }
    }

    private fun readChapters(archive: ByteArray, slug: String): List<ChapterDto> = withArchiveReader(archive) { reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == slug) {
                return@withArchiveReader reader.readChapterArray()
            }
            reader.skipValue()
        }
        emptyList()
    }

    private inline fun <T> withArchiveReader(archive: ByteArray, block: (JsonReader) -> T): T = GZIPInputStream(ByteArrayInputStream(archive)).use { gzip ->
        JsonReader(InputStreamReader(gzip, Charsets.UTF_8)).use(block)
    }

    private fun JsonReader.readChapterArray(): List<ChapterDto> {
        val chapters = mutableListOf<ChapterDto>()
        beginArray()
        while (hasNext()) {
            beginObject()
            var id = ""
            var number = 0.0
            var title = ""
            var releaseDate: String? = null
            var pages = emptyList<String>()

            while (hasNext()) {
                when (nextName()) {
                    "id" -> id = nextString()
                    "number" -> number = nextDouble()
                    "title" -> title = nextString()
                    "releaseDate" -> releaseDate = nextNullableString()
                    "pages" -> pages = readStringArray()
                    else -> skipValue()
                }
            }
            endObject()
            if (id.isNotBlank()) {
                chapters += ChapterDto(id, number, title, pages, releaseDate)
            }
        }
        endArray()
        return chapters
    }

    private fun JsonReader.readStringArray(): List<String> {
        val values = mutableListOf<String>()
        beginArray()
        while (hasNext()) values += nextString()
        endArray()
        return values
    }

    private fun JsonReader.nextNullableString(): String? = if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextString()
    }

    private fun SManga.slug(): String = (baseUrl + url).toHttpUrl().pathSegments.getOrNull(1)
        ?: throw IOException("Missing manga slug")

    private fun String.resolveImageUrl(): String = when {
        startsWith("\$TEMP") -> replaceFirst("\$TEMP", "https://temp.compsci88.com")
        startsWith("\$HOT") -> replaceFirst("\$HOT", "https://scans-hot.planeptune.us")
        startsWith("\$LST") -> replaceFirst("\$LST", "https://scans.lastation.us")
        startsWith("\$LOW") -> replaceFirst("\$LOW", "https://official.lowee.us")
        startsWith("\$MFK") -> replaceFirst("\$MFK", "https://images.mangafreak.me")
        startsWith("/cdn-readmanga/") -> "https://cdn.readmanga.cc${this.removePrefix("/cdn-readmanga")}"
        startsWith("//") -> "https:$this"
        startsWith("/") -> "$baseUrl$this"
        else -> this
    }

    private fun Response.requireSuccess() {
        if (!isSuccessful) throw IOException("HTTP $code for ${request.url}")
    }

    private data class CatalogCache(val mangas: List<MangaDto>, val savedAt: Long)

    private data class ArchiveCache(val bytes: ByteArray, val savedAt: Long)

    private data class ChapterCache(val archive: ByteArray, val slug: String, val chapters: List<ChapterDto>)

    companion object {
        private const val PAGE_SIZE = 16
        private const val CATALOG_CACHE_LIFETIME = 30 * 60 * 1000L
        private const val ARCHIVE_CACHE_LIFETIME = 30 * 60 * 1000L
        private const val GZIP_MAGIC_FIRST: Byte = 0x1F
        private const val GZIP_MAGIC_SECOND: Byte = -117
        private val CATALOG_START_REGEX = Regex("""\[\{id\s*:""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
