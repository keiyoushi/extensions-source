package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.time.OffsetDateTime

@Source
abstract class MikoRoku : KeiSource() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private var catalogCache: List<CatalogEntry>? = null

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog()
        .sortedByDescending { it.rating }
        .toMangasPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog().toMangasPage(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalize()
        return fetchCatalog()
            .filter { entry ->
                entry.title.normalize().contains(normalizedQuery) ||
                    entry.altTitle.split(";").any { it.normalize().contains(normalizedQuery) }
            }
            .toMangasPage(page)
    }

    private fun List<CatalogEntry>.toMangasPage(page: Int): MangasPage {
        val fromIndex = (page - 1) * PAGE_SIZE
        if (fromIndex >= size) return MangasPage(emptyList(), false)

        val toIndex = minOf(fromIndex + PAGE_SIZE, size)
        return MangasPage(subList(fromIndex, toIndex).map { it.toSManga() }, toIndex < size)
    }

    private suspend fun fetchCatalog(): List<CatalogEntry> = catalogCache ?: client.get(CATALOG_URL)
        .parseAs<List<CatalogEntry>>(json)
        .filter { it.slug.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.slug }
        .also { catalogCache = it }

    private fun CatalogEntry.toSManga(): SManga = SManga.create().apply {
        title = this@toSManga.title
        thumbnail_url = img
        setUrlWithoutDomain("/detail.html?slug=$slug")
    }

    private fun String.normalize(): String = lowercase().filter { it.isLetterOrDigit() }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // Old ZeistManga entries store outdated URLs; resolve them by title
        // so existing library entries keep working.
        val slug = manga.url.extractSlug()
            ?: resolveSlugByTitle(manga.title)
            ?: throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")

        val details = if (fetchDetails) {
            fetchMangaDetails(slug)
        } else {
            manga.apply { setUrlWithoutDomain("/detail.html?slug=$slug") }
        }

        val updatedChapters = if (fetchChapters) fetchChapterList(slug) else chapters

        return SMangaUpdate(manga = details, chapters = updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null
        val slug = url.queryParameter("slug") ?: return null
        return fetchMangaDetails(slug).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    private suspend fun fetchMangaDetails(slug: String): SManga {
        // Firestore carries the fresh metadata; the GitHub catalog stays
        // available when Firestore is quota-exceeded (HTTP 429).
        val fields = fetchFirestoreDoc(slug)?.get("fields")?.jsonObject
        val catalogEntry = fetchCatalog().find { it.slug == slug }

        return SManga.create().apply {
            setUrlWithoutDomain("/detail.html?slug=$slug")
            title = fields?.string("title")?.takeIf { it.isNotBlank() }
                ?: catalogEntry?.title.orEmpty()
            author = fields?.string("author")?.takeIf { it.isNotBlank() }
                ?: catalogEntry?.author.orEmpty()
            artist = fields?.string("artist")?.takeIf { it.isNotBlank() }
                ?: catalogEntry?.artist.orEmpty()
            description = fields?.string("desc")?.takeIf { it.isNotBlank() }?.toPlainText()
                ?: catalogEntry?.desc.orEmpty()
            genre = fields?.stringList("genres")?.takeIf { it.isNotEmpty() }?.joinToString()
                ?: catalogEntry?.genres?.joinToString().orEmpty()
            status = (fields?.string("status") ?: catalogEntry?.status.orEmpty()).toMangaStatus()
            thumbnail_url = fields?.string("img")?.takeIf { it.isNotBlank() }
                ?: catalogEntry?.img
        }
    }

    private fun String.toMangaStatus(): Int = when (lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String.toPlainText(): String = Jsoup.parseBodyFragment(this).text()

    private suspend fun resolveSlugByTitle(title: String): String? {
        val normalized = title.normalize()
        if (normalized.isEmpty()) return null

        return fetchCatalog().find { entry ->
            entry.title.normalize() == normalized ||
                entry.altTitle.split(";").any { it.normalize() == normalized }
        }?.slug
    }

    private fun String.extractSlug(): String? = SLUG_REGEX.find(this)?.groupValues?.get(1)

    private suspend fun fetchChapterList(slug: String): List<SChapter> {
        val fields = fetchFirestoreDoc(slug)?.get("fields")?.jsonObject
        val chapters = fields?.parseChapters()

        if (!chapters.isNullOrEmpty()) {
            return chapters.sortedForDisplay().map { it.toSChapter(slug) }
        }

        // Firestore unavailable (e.g. HTTP 429) or has no chapters:
        // fall back to the Blogger mirror feed.
        val title = fields?.string("title")
            ?: fetchCatalog().find { it.slug == slug }?.title
                .orEmpty()

        return fetchBloggerChapters(slug, title)
    }

    private fun JsonObject.parseChapters(): List<ChapterInfo> = keys.filter { it.startsWith("chapters.") }
        .mapNotNull { key ->
            val fields = get(key)?.jsonObject
                ?.get("mapValue")?.jsonObject
                ?.get("fields")?.jsonObject
                ?: return@mapNotNull null

            if (fields.bool("isDraft") == true) return@mapNotNull null

            ChapterInfo(
                key = key.removePrefix("chapters."),
                title = fields.string("title").orEmpty(),
                date = fields.long("date") ?: 0L,
                order = fields.long("order") ?: 0L,
                images = fields.stringList("images"),
            )
        }
        .filter { it.title.isNotBlank() }

    private fun List<ChapterInfo>.sortedForDisplay(): List<ChapterInfo> = sortedWith(
        compareByDescending<ChapterInfo> { it.number.takeIf { n -> !n.isNaN() } ?: -1.0 }
            .thenByDescending { it.order },
    )

    private val ChapterInfo.number: Double
        get() = CHAPTER_NUMBER_REGEX.find(title)?.groupValues?.get(1)?.toDoubleOrNull() ?: Double.NaN

    private fun ChapterInfo.toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = date
        setUrlWithoutDomain("/manga/$slug/chapter/$key")
    }

    private suspend fun fetchBloggerChapters(slug: String, mangaTitle: String): List<SChapter> {
        if (mangaTitle.isBlank()) return emptyList()

        val url = BLOGGER_FEED_URL.toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", "150")
            .addQueryParameter("q", mangaTitle)
            .build()

        val response = client.get(url, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return emptyList()
        }

        return response.parseAs<JsonObject>(json)
            .get("feed")?.jsonObject
            ?.get("entry")?.jsonArray
            .orEmpty()
            .mapNotNull { it.jsonObject.toBloggerChapter(slug, mangaTitle) }
            .sortedWith(
                compareByDescending<SChapter> {
                    CHAPTER_NUMBER_REGEX.find(it.name)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
                },
            )
    }

    private fun JsonObject.toBloggerChapter(slug: String, mangaTitle: String): SChapter? {
        val title = get("title")?.jsonObject?.get("\$t")?.jsonPrimitive?.contentOrNull
            ?: return null
        if (!title.contains(mangaTitle, ignoreCase = true)) return null

        val chapterName = CHAPTER_TITLE_REGEX.find(title)?.value ?: return null
        val postId = get("id")?.jsonObject?.get("\$t")?.jsonPrimitive?.contentOrNull
            ?.let { POST_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?: return null
        val published = get("published")?.jsonObject?.get("\$t")?.jsonPrimitive?.contentOrNull

        return SChapter.create().apply {
            name = chapterName
            date_upload = published.parseBlogDate()
            setUrlWithoutDomain("/manga/$slug/post/$postId")
        }
    }

    private fun String?.parseBlogDate(): Long = try {
        this?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L
    } catch (_: Exception) {
        0L
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        POST_URL_REGEX.find(chapter.url)?.let { match ->
            return fetchBloggerPostPages(match.groupValues[2], baseUrl + chapter.url)
        }

        val match = CHAPTER_URL_REGEX.find(chapter.url)
            ?: throw Exception("Unsupported chapter URL: ${chapter.url}")
        val slug = match.groupValues[1]
        val key = match.groupValues[2]

        // Firestore images first; Blogger mirror when Firestore is quota-exceeded.
        fetchFirestorePages(slug, key)?.let { return it }

        val title = fetchCatalog().find { it.slug == slug }?.title.orEmpty()
        return fetchBloggerChapterPages(title, chapter.name, baseUrl + chapter.url)
    }

    private suspend fun fetchFirestorePages(slug: String, key: String): List<Page>? {
        val chapterFields = fetchFirestoreDoc(slug)
            ?.get("fields")?.jsonObject
            ?.get("chapters.$key")?.jsonObject
            ?.get("mapValue")?.jsonObject
            ?.get("fields")?.jsonObject
            ?: return null

        val images = chapterFields.stringList("images")
        if (images.isEmpty()) return null

        val pageUrl = baseUrl + "/manga/$slug/chapter/$key"
        return images.mapIndexed { index, imageUrl -> Page(index, pageUrl, imageUrl) }
    }

    private suspend fun fetchBloggerPostPages(postId: String, referer: String): List<Page> {
        val url = "$BLOGGER_FEED_URL/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        val response = client.get(url, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            throw Exception("Failed to load chapter pages (HTTP ${response.code})")
        }

        val content = response.parseAs<JsonObject>(json)
            .get("entry")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("\$t")?.jsonPrimitive?.contentOrNull
            .orEmpty()

        return content.extractImages().mapIndexed { index, imageUrl ->
            Page(index, referer, imageUrl)
        }
    }

    private suspend fun fetchBloggerChapterPages(
        mangaTitle: String,
        chapterName: String,
        referer: String,
    ): List<Page> {
        if (mangaTitle.isBlank() || chapterName.isBlank()) {
            throw Exception("Chapter pages unavailable (Firestore quota exceeded)")
        }

        val url = BLOGGER_FEED_URL.toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", "5")
            .addQueryParameter("q", "$mangaTitle $chapterName")
            .build()

        val response = client.get(url, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            throw Exception("Chapter pages unavailable (Firestore quota exceeded)")
        }

        val images = response.parseAs<JsonObject>(json)
            .get("feed")?.jsonObject
            ?.get("entry")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("\$t")?.jsonPrimitive?.contentOrNull
            .orEmpty()
            .extractImages()

        if (images.isEmpty()) throw Exception("Chapter pages unavailable (Firestore quota exceeded)")

        return images.mapIndexed { index, imageUrl -> Page(index, referer, imageUrl) }
    }

    private fun String.extractImages(): List<String> = IMAGE_SRC_REGEX.findAll(this)
        .map { it.groupValues[1] }
        .filter { it.startsWith("http") }
        .distinct()
        .toList()

    private suspend fun fetchFirestoreDoc(slug: String): JsonObject? {
        val response = client.get("$FIRESTORE_DOC_URL/$slug", ensureSuccess = false)
        return if (response.code == 200) {
            response.parseAs<JsonObject>(json)
        } else {
            // 404 (unknown slug) and 429 (quota exceeded) both fall back
            // to the GitHub catalog / Blogger mirrors.
            response.close()
            null
        }
    }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? = get(key)?.jsonObject?.get("integerValue")?.jsonPrimitive?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? = get(key)?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.stringList(key: String): List<String> = get(key)?.jsonObject
        ?.get("arrayValue")?.jsonObject
        ?.get("values")?.jsonArray
        ?.mapNotNull { it.jsonObject.get("stringValue")?.jsonPrimitive?.contentOrNull }
        .orEmpty()

    private data class ChapterInfo(
        val key: String,
        val title: String,
        val date: Long,
        val order: Long,
        val images: List<String>,
    )

    companion object {
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/moemaomao/mymangadata/main/all-manga.json"
        private const val FIRESTORE_DOC_URL =
            "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga"
        private const val BLOGGER_FEED_URL =
            "https://www.mikodrive.my.id/feeds/posts/default"
        private const val PAGE_SIZE = 20

        private val SLUG_REGEX = """/detail\.html\?slug=([^&/]+)""".toRegex()
        private val CHAPTER_URL_REGEX = """/manga/([^/]+)/chapter/([^/]+)""".toRegex()
        private val POST_URL_REGEX = """/manga/([^/]+)/post/([^/]+)""".toRegex()
        private val CHAPTER_NUMBER_REGEX =
            """Chapter\s+(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)
        private val CHAPTER_TITLE_REGEX =
            """Chapter\s+\d+(?:\.\d+)?""".toRegex(RegexOption.IGNORE_CASE)
        private val POST_ID_REGEX = """post-(\d+)""".toRegex()
        private val IMAGE_SRC_REGEX = """<img[^>]+src="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
    }
}
