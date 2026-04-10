package eu.kanade.tachiyomi.extension.es.samatodencomics

import android.text.Html
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.time.OffsetDateTime

class SamatoDenComics : HttpSource() {

    override val name = "Samato's Den: Comics"
    override val baseUrl = "https://samatoden.blogspot.com"
    override val lang = "es"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json, text/html, */*")

    override fun popularMangaRequest(page: Int): Request = GET(feedUrl(page), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseFeedPage(response.body.string())

    override fun latestUpdatesRequest(page: Int): Request = GET(feedUrl(page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseFeedPage(response.body.string())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(feedUrl(page, query.trim()), headers)

    override fun searchMangaParse(response: Response): MangasPage = parseFeedPage(response.body.string())

    override fun mangaDetailsRequest(manga: SManga): Request = GET(normalizePostFeedUrl(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga = entryToManga(parseSingleEntry(response.body.string()))

    override fun chapterListRequest(manga: SManga): Request = GET(normalizePostFeedUrl(manga.url), headers)

    override fun chapterListParse(response: Response): List<SChapter> = entryToChapters(parseSingleEntry(response.body.string()))

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val entry = parseSingleEntry(response.body.string())
        val html = entry.content?.text.orEmpty()
        val chapterKey = response.request.url.queryParameter(CHAPTER_KEY_PARAM).orEmpty()
        val pageUrls = parseChapterPageUrls(html, chapterKey)

        return pageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList()

    private fun feedUrl(page: Int, query: String = ""): String {
        val startIndex = ((page - 1) * PAGE_SIZE) + 1
        return "$baseUrl/feeds/posts/default/-/comics"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", PAGE_SIZE.toString())
            .addQueryParameter("start-index", startIndex.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
            }
            .build()
            .toString()
    }

    private fun parseFeedPage(payload: String): MangasPage {
        val feed = json.decodeFromString(BloggerFeedDto.serializer(), payload).feed
            ?: return MangasPage(emptyList(), false)

        val mangaList = feed.entries.orEmpty().map(::entryToManga)

        val total = feed.totalResults?.text?.toIntOrNull() ?: mangaList.size
        val start = feed.startIndex?.text?.toIntOrNull() ?: 1
        val perPage = feed.itemsPerPage?.text?.toIntOrNull() ?: mangaList.size
        val hasNextPage = (start + perPage - 1) < total

        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseSingleEntry(payload: String): BloggerEntryDto {
        val root = json.decodeFromString(BloggerFeedDto.serializer(), payload)
        return root.entry ?: root.feed?.entries?.firstOrNull()
            ?: error("No se encontro ninguna entrada de comic en la respuesta de Blogger")
    }

    private fun entryToManga(entry: BloggerEntryDto): SManga {
        val html = entry.content?.text.orEmpty()
        return SManga.create().apply {
            url = normalizePostFeedUrl(linkHref(entry, "self").orEmpty())
            title = entry.title?.text.orEmpty()
            author = extractArtist(html)
            artist = extractArtist(html)
            description = extractArtist(html)
            genre = extractCategories(entry).joinToString().ifBlank { null }
            status = if (html.contains("En Progreso", ignoreCase = true) || html.contains("Ongoing", ignoreCase = true)) {
                SManga.ONGOING
            } else {
                SManga.COMPLETED
            }
            thumbnail_url = extractThumbnail(entry, html)
        }
    }

    private fun entryToChapters(entry: BloggerEntryDto): List<SChapter> {
        val html = entry.content?.text.orEmpty()
        val postTitle = entry.title?.text.orEmpty()
        val publishedAt = parseDateMillis(entry.published?.text)
        val basePostUrl = normalizePostFeedUrl(linkHref(entry, "self").orEmpty())

        val generatedChapterTitles = extractChapterTitles(html)
        val generatedChapters = GENERATED_CHAPTER_REGEX.findAll(html).map { match ->
            val key = match.groupValues[1]
            val title = match.groupValues[2].ifBlank {
                generatedChapterTitles[key] ?: "Episodio $key"
            }

            createChapter(
                name = title,
                chapterKey = key,
                postFeedUrl = basePostUrl,
                uploadedAt = publishedAt,
                chapterNumber = key.toFloatOrNull() ?: 0f,
            )
        }.toList()
        if (generatedChapters.isNotEmpty()) return generatedChapters

        val arrayChapterTitles = extractChapterTitles(html)
        val arrayChapters = ARRAY_CHAPTER_REGEX.findAll(html).map { match ->
            val key = match.groupValues[1]
            createChapter(
                name = arrayChapterTitles[key] ?: "Episodio $key",
                chapterKey = key,
                postFeedUrl = basePostUrl,
                uploadedAt = publishedAt,
                chapterNumber = key.toFloatOrNull() ?: 0f,
            )
        }.toList()
        if (arrayChapters.isNotEmpty()) return arrayChapters

        val singlePages = parseSinglePageList(html)
        return if (singlePages.isNotEmpty()) {
            listOf(
                createChapter(
                    name = postTitle.ifBlank { "Capitulo 1" },
                    chapterKey = SINGLE_CHAPTER_KEY,
                    postFeedUrl = basePostUrl,
                    uploadedAt = publishedAt,
                    chapterNumber = 1f,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun parseChapterPageUrls(html: String, chapterKey: String): List<String> {
        val generatedChapter = GENERATED_CHAPTER_REGEX.findAll(html).firstOrNull { it.groupValues[1] == chapterKey }
        if (generatedChapter != null) {
            return buildSequencePages(
                base = generatedChapter.groupValues[3],
                start = generatedChapter.groupValues[4].toInt(),
                end = generatedChapter.groupValues[5].toInt(),
                extension = ".jpg",
            )
        }

        val arrayChapter = ARRAY_CHAPTER_REGEX.findAll(html).firstOrNull { it.groupValues[1] == chapterKey }
        if (arrayChapter != null) {
            return buildSequencePages(
                base = arrayChapter.groupValues[2],
                start = arrayChapter.groupValues[3].toInt(),
                end = arrayChapter.groupValues[4].toInt(),
                extension = inferExtensionFromBase(arrayChapter.groupValues[2]),
            )
        }

        return if (chapterKey == SINGLE_CHAPTER_KEY) parseSinglePageList(html) else emptyList()
    }

    private fun parseSinglePageList(html: String): List<String> {
        val directLoop = SINGLE_LOOP_REGEX.find(html)
        if (directLoop != null) {
            return buildSequencePages(
                base = directLoop.groupValues[3],
                start = directLoop.groupValues[1].toInt(),
                end = directLoop.groupValues[2].toInt(),
                extension = directLoop.groupValues[4],
            )
        }

        val assignmentLoop = ASSIGNMENT_LOOP_REGEX.find(html)
        if (assignmentLoop != null) {
            return buildSequencePages(
                base = assignmentLoop.groupValues[3],
                start = assignmentLoop.groupValues[1].toInt(),
                end = assignmentLoop.groupValues[2].toInt(),
                extension = assignmentLoop.groupValues[4],
            )
        }

        return emptyList()
    }

    private fun createChapter(
        name: String,
        chapterKey: String,
        postFeedUrl: String,
        uploadedAt: Long,
        chapterNumber: Float,
    ): SChapter = SChapter.create().apply {
        url = postFeedUrl.toHttpUrl().newBuilder()
            .addQueryParameter(CHAPTER_KEY_PARAM, chapterKey)
            .build()
            .toString()
        this.name = name
        date_upload = uploadedAt
        this.chapter_number = chapterNumber
    }

    private fun buildSequencePages(base: String, start: Int, end: Int, extension: String): List<String> = (start..end).map { "$base$it$extension" }

    private fun inferExtensionFromBase(base: String): String = base.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".jpg"

    private fun extractChapterTitles(html: String): Map<String, String> = OPTION_TITLE_REGEX.findAll(html).associate { match ->
        match.groupValues[1] to cleanHtml(match.groupValues[2])
    }

    private fun extractArtist(html: String): String? {
        val doc = Jsoup.parseBodyFragment(html)
        val artistBlock = doc.selectFirst("#artist-tags") ?: return null
        artistBlock.select("strong").remove()
        return artistBlock.text().trim().trimStart(':').trim().ifBlank { null }
    }

    private fun extractThumbnail(entry: BloggerEntryDto, html: String): String? {
        val inlineImage = HIDDEN_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!inlineImage.isNullOrBlank()) return inlineImage

        return entry.mediaThumbnail?.url?.substringBefore("=s72")
    }

    private fun extractCategories(entry: BloggerEntryDto): List<String> = entry.categories.orEmpty().mapNotNull { it.term?.takeIf(String::isNotBlank) }

    private fun linkHref(entry: BloggerEntryDto, rel: String): String? = entry.links.orEmpty().firstOrNull { it.rel == rel }?.href

    private fun normalizePostFeedUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.queryParameter("alt") == "json") return parsed.toString()
        return parsed.newBuilder()
            .addQueryParameter("alt", "json")
            .build()
            .toString()
    }

    private fun parseDateMillis(value: String?): Long = value?.takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L

    private fun cleanHtml(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val PAGE_SIZE = 30
        private const val CHAPTER_KEY_PARAM = "samatoChapter"
        private const val SINGLE_CHAPTER_KEY = "single"

        private val HIDDEN_IMAGE_REGEX = Regex("""<img[^>]+src="([^"]+)"""", setOf(RegexOption.IGNORE_CASE))
        private val OPTION_TITLE_REGEX = Regex("""<option\s+value="(\d+)">([^<]+)</option>""", setOf(RegexOption.IGNORE_CASE))
        private val GENERATED_CHAPTER_REGEX = Regex(
            """(\d+)\s*:\s*\{\s*title:\s*"([^"]+)"\s*,\s*pages:\s*generatePages\("([^"]+)",\s*(\d+),\s*(\d+)\)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val ARRAY_CHAPTER_REGEX = Regex(
            """(\d+)\s*:\s*\[\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*\]""",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val SINGLE_LOOP_REGEX = Regex(
            """for\s*\(let\s+i\s*=\s*(\d+);\s*i\s*<=\s*(\d+);\s*i\+\+\)\s*\{\s*pages\.push\(`([^`$]+)\$\{i\}([^`]+)`\);""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val ASSIGNMENT_LOOP_REGEX = Regex(
            """for\s*\(let\s+i\s*=\s*(\d+);\s*i\s*<=\s*(\d+);\s*i\+\+\)\s*\{\s*pages\.push\(\s*`([^`$]+)\$\{i\}([^`]+)`\s*\);""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
