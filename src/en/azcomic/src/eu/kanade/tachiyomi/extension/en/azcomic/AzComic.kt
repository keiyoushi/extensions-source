package eu.kanade.tachiyomi.extension.en.azcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class AzComic : HttpSource() {

    override val name = "azComic"

    override val baseUrl = "https://azcomic.com"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }

    @Volatile
    private var cachedSeries: List<SeriesEntry>? = null

    @Volatile
    private var isPrefetchingSeries = false

    override val client = network.cloudflareClient

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val series = getSeries().sortedByDescending { it.updatedAt }
        return Observable.just(series.toMangasPage(page))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchPopularManga(page)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val series = applyFilters(getSeries(), query, filters)
        val hasFilters = query.isNotBlank() || filters.hasActiveFilters()
        val sorted = if (hasFilters) {
            series.sortedBy { it.title.lowercase(Locale.ROOT) }
        } else {
            series.sortedByDescending { it.updatedAt }
        }

        return Observable.just(sorted.toMangasPage(page))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        prefetchSeriesIfNeeded()
        val categories = cachedSeries
            ?.mapNotNull { it.category }
            ?.distinct()
            ?.sorted()
            .orEmpty()

        return FilterList(
            CategoryFilter(categories),
            LetterFilter(),
        )
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val slug = manga.url.substringAfterLast('/')
        val details = getSeries().firstOrNull { it.slug == slug }?.toSManga() ?: manga
        return Observable.just(details.apply { initialized = true })
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val slug = manga.url.substringAfterLast('/')
        val chapters = getSeries().firstOrNull { it.slug == slug }?.chapters.orEmpty()
        return Observable.just(chapters.map { it.toSChapter() })
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request {
        val fullUrl = (baseUrl + chapter.url).toHttpUrl()
        val urlParam = buildString {
            append(fullUrl.encodedPath.trimStart('/'))
            fullUrl.encodedQuery?.let { query ->
                append('?')
                append(query)
            }
        }
        val apiUrl = "$baseUrl/get_image.php".toHttpUrl().newBuilder()
            .addQueryParameter("url", urlParam)
            .build()
        return GET(apiUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.parseAs<ImagePayload>()
        return payload.images.orEmpty().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringAfterLast('/')
        val series = getSeries().firstOrNull { it.slug == slug }
        val target = series?.latestUrl ?: manga.url
        return baseUrl + target
    }

    private fun getSeries(): List<SeriesEntry> = cachedSeries ?: synchronized(this) {
        cachedSeries ?: buildSeries(fetchComics()).also { cachedSeries = it }
    }

    private fun prefetchSeriesIfNeeded() {
        if (cachedSeries != null || isPrefetchingSeries) return
        val shouldFetch = synchronized(this) {
            if (cachedSeries != null || isPrefetchingSeries) {
                false
            } else {
                isPrefetchingSeries = true
                true
            }
        }
        if (!shouldFetch) return
        Thread {
            try {
                cachedSeries = buildSeries(fetchComics())
            } finally {
                isPrefetchingSeries = false
            }
        }.start()
    }

    private fun fetchComics(): List<ComicEntry> {
        val request = GET("$baseUrl/get_comic.php", headers)
        val payload = client.newCall(request).execute().parseAs<List<ComicEntry>>()
        return payload.filter { it.title.isNotBlank() && it.cover.isNotBlank() && it.url.isNotBlank() }
    }

    private fun buildSeries(entries: List<ComicEntry>): List<SeriesEntry> {
        val grouped = LinkedHashMap<String, MutableList<ComicEntry>>()
        entries.forEach { entry ->
            val baseTitle = entry.title.seriesTitle()
            val slug = entry.seriesSlug(baseTitle)
            grouped.getOrPut(slug) { mutableListOf() }.add(entry)
        }

        return grouped.map { (slug, comics) ->
            val latestEntry = comics.maxByOrNull { it.updatedAtMillis().takeIf { value -> value > 0 } ?: it.num }
                ?: comics.first()
            val title = latestEntry.title.seriesTitle()
            val chapters = comics.map { it.toChapterEntry(title) }
            val sortedChapters = chapters.sortedWith(
                compareByDescending<ChapterEntry> { it.chapterNumber != null }
                    .thenByDescending { it.chapterNumber ?: 0f }
                    .thenByDescending { it.dateUpload }
                    .thenByDescending { it.order },
            )
            val latestChapter = sortedChapters.firstOrNull()
            val coverEntry = comics.minByOrNull { it.chapterNumber() ?: Float.MAX_VALUE }
                ?: comics.minByOrNull { it.num }
                ?: latestEntry
            SeriesEntry(
                slug = slug,
                title = title,
                category = latestEntry.category,
                cover = coverEntry.cover,
                latestUrl = "/${latestEntry.url}",
                updatedAt = latestChapter?.dateUpload ?: latestEntry.updatedAtMillis(),
                chapters = sortedChapters,
            )
        }
    }

    private fun applyFilters(series: List<SeriesEntry>, query: String, filters: FilterList): List<SeriesEntry> {
        val category = filters.filterIsInstance<CategoryFilter>().firstOrNull()?.selected
        val letter = filters.filterIsInstance<LetterFilter>().firstOrNull()?.selected
        val queryText = query.trim().lowercase(Locale.ROOT)

        return series.filter { entry ->
            val matchesCategory = category == null || entry.category == category
            val matchesLetter = entry.title.matchesLetter(letter)
            val matchesQuery = queryText.isBlank() || entry.title.lowercase(Locale.ROOT).contains(queryText)
            matchesCategory && matchesLetter && matchesQuery
        }
    }

    private fun List<SeriesEntry>.toMangasPage(page: Int): MangasPage {
        if (isEmpty()) return MangasPage(emptyList(), false)
        val from = (page - 1) * PAGE_SIZE
        if (from >= size) return MangasPage(emptyList(), false)
        val to = minOf(from + PAGE_SIZE, size)
        val mangas = subList(from, to).map { it.toSManga() }
        return MangasPage(mangas, to < size)
    }

    private fun FilterList.hasActiveFilters(): Boolean {
        val category = filterIsInstance<CategoryFilter>().firstOrNull()?.selected
        val letter = filterIsInstance<LetterFilter>().firstOrNull()?.selected
        return category != null || letter != null
    }

    private fun SeriesEntry.toSManga(): SManga = SManga.create().apply {
        url = "/series/$slug"
        title = this@toSManga.title
        thumbnail_url = cover
        genre = category
        status = SManga.UNKNOWN
        initialized = true
    }

    private fun ComicEntry.toChapterEntry(seriesTitle: String): ChapterEntry = ChapterEntry(
        name = title.chapterName(seriesTitle),
        url = "/$url",
        dateUpload = updatedAtMillis(),
        chapterNumber = chapterNumber(),
        order = num,
    )

    private fun ChapterEntry.toSChapter(): SChapter = SChapter.create().apply {
        name = this@toSChapter.name
        url = this@toSChapter.url
        date_upload = this@toSChapter.dateUpload
        chapterNumber?.let { chapter_number = it }
    }

    private fun ComicEntry.updatedAtMillis(): Long {
        val raw = updatedAt ?: return 0L
        return dateFormat.tryParse(raw)
    }

    private fun String.seriesTitle(): String {
        val base = replace(SERIES_TITLE_REGEX, "").trim()
        return if (base.isNotBlank()) base else this
    }

    private fun String.chapterName(seriesTitle: String): String {
        val match = CHAPTER_NAME_REGEX.find(this)
        if (match != null) {
            return substring(match.range.first).trim()
        }
        if (startsWith(seriesTitle, ignoreCase = true)) {
            val rest = substring(seriesTitle.length).trim().trimStart('-', ':')
            if (rest.isNotBlank()) return rest
        }
        return this
    }

    private fun String.matchesLetter(letter: String?): Boolean {
        if (letter == null) return true
        val first = trim().firstOrNull() ?: return false
        return if (letter == "#") {
            !first.isLetter() || !first.uppercaseChar().isAtoZ()
        } else {
            first.uppercaseChar() == letter[0]
        }
    }

    private fun Char.isAtoZ(): Boolean = this in 'A'..'Z'

    private fun ComicEntry.seriesSlug(seriesTitle: String): String {
        val match = COVER_SLUG_REGEX.find(cover)
        if (match != null) return match.groupValues[1]
        val fromUrl = url.substringAfter('/').substringAfter('-')
        val cleanedUrl = fromUrl.substringBefore("-chapter-")
        val slug = cleanedUrl.ifBlank { seriesTitle }
        return slug.toSlug()
    }

    private fun ComicEntry.chapterNumber(): Float? {
        val coverMatch = COVER_CHAPTER_REGEX.find(cover)?.groupValues?.get(1)
        val titleMatch = CHAPTER_NUMBER_REGEX.find(title)?.groupValues?.get(1)
        val urlMatch = URL_CHAPTER_REGEX.find(url)?.groupValues?.get(1)
        return listOf(coverMatch, titleMatch, urlMatch)
            .firstNotNullOfOrNull { it?.toFloatOrNull() }
    }

    private fun String.toSlug(): String = lowercase(Locale.ROOT)
        .replace(SLUG_CLEANUP_REGEX, "-")
        .trim('-')

    @Serializable
    private data class ComicEntry(
        val num: Long,
        val category: String? = null,
        val title: String,
        val cover: String,
        val url: String,
        @SerialName("updated_at") val updatedAt: String? = null,
    )

    @Serializable
    private data class ImagePayload(
        val images: List<String>? = null,
    )

    private data class ChapterEntry(
        val name: String,
        val url: String,
        val dateUpload: Long,
        val chapterNumber: Float?,
        val order: Long,
    )

    private data class SeriesEntry(
        val slug: String,
        val title: String,
        val category: String?,
        val cover: String?,
        val latestUrl: String,
        val updatedAt: Long,
        val chapters: List<ChapterEntry>,
    )

    private class CategoryFilter(categories: List<String>) :
        Filter.Select<String>(
            "Category",
            mutableListOf("All").apply { addAll(categories) }.toTypedArray(),
        ) {
        private val categoryValues = categories
        val selected: String? get() = if (state == 0) null else categoryValues[state - 1]
    }

    private class LetterFilter :
        Filter.Select<String>(
            "Alphabetic",
            LETTER_OPTIONS.toTypedArray(),
        ) {
        val selected: String? get() = if (state == 0) null else LETTER_OPTIONS[state]
    }

    private companion object {
        private const val PAGE_SIZE = 36
        private val COVER_SLUG_REGEX = Regex("""/uploads/manga/([^/]+)/""")
        private val COVER_CHAPTER_REGEX = Regex("""/chapters/([^/]+)/""")
        private val SERIES_TITLE_REGEX = Regex("""\s+(Chapter|Issue)\b.*$""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NAME_REGEX = Regex("""\b(Chapter|Issue)\b.*$""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("""\b(?:Chapter|Issue)\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        private val URL_CHAPTER_REGEX = Regex("""-(?:chapter|issue)-([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        private val SLUG_CLEANUP_REGEX = Regex("""[^a-z0-9]+""")
        private val LETTER_OPTIONS = buildList {
            add("All")
            add("#")
            ('A'..'Z').forEach { add(it.toString()) }
        }
    }
}
