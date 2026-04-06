package eu.kanade.tachiyomi.extension.es.samatodencomics

import android.text.Html
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime

class SamatoDenComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(SamatoDenComics())
}

class SamatoDenComics : HttpSource() {

    override val name = "Samato's Den: Comics"
    override val baseUrl = "https://samatoden.blogspot.com"
    override val lang = "es"
    override val supportsLatest = true
    override val versionId = 1

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
        val html = entry.optJSONObject("content")?.optString("\$t").orEmpty()
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
        val queryPart = if (query.isBlank()) "" else "&q=${query.urlEncode()}"
        return "$baseUrl/feeds/posts/default/-/comics?alt=json&max-results=$PAGE_SIZE&start-index=$startIndex$queryPart"
    }

    private fun parseFeedPage(json: String): MangasPage {
        val root = JSONObject(json)
        val feed = root.optJSONObject("feed") ?: return MangasPage(emptyList(), false)
        val entries = feed.optJSONArray("entry") ?: JSONArray()
        val mangaList = buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                add(entryToManga(entries.getJSONObject(index)))
            }
        }

        val total = feed.optJSONObject("openSearch\$totalResults")?.optString("\$t")?.toIntOrNull() ?: mangaList.size
        val start = feed.optJSONObject("openSearch\$startIndex")?.optString("\$t")?.toIntOrNull() ?: 1
        val perPage = feed.optJSONObject("openSearch\$itemsPerPage")?.optString("\$t")?.toIntOrNull() ?: mangaList.size
        val hasNextPage = (start + perPage - 1) < total

        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseSingleEntry(json: String): JSONObject {
        val root = JSONObject(json)
        root.optJSONObject("entry")?.let { return it }

        val feed = root.optJSONObject("feed")
        val entries = feed?.optJSONArray("entry")
        if (entries != null && entries.length() > 0) {
            return entries.getJSONObject(0)
        }

        error("No se encontro ninguna entrada de comic en la respuesta de Blogger")
    }

    private fun entryToManga(entry: JSONObject): SManga {
        val html = entry.optJSONObject("content")?.optString("\$t").orEmpty()
        return SManga.create().apply {
            url = normalizePostFeedUrl(linkHref(entry, "self").orEmpty())
            title = entry.optJSONObject("title")?.optString("\$t").orEmpty()
            author = extractArtist(html)
            artist = extractArtist(html)
            description = extractArtist(html)
            genre = extractCategories(entry).joinToString(", ").ifBlank { null }
            status = if (html.contains("En Progreso", ignoreCase = true) || html.contains("Ongoing", ignoreCase = true)) {
                SManga.ONGOING
            } else {
                SManga.COMPLETED
            }
            thumbnail_url = extractThumbnail(entry, html)
            initialized = true
        }
    }

    private fun entryToChapters(entry: JSONObject): List<SChapter> {
        val html = entry.optJSONObject("content")?.optString("\$t").orEmpty()
        val postTitle = entry.optJSONObject("title")?.optString("\$t").orEmpty()
        val publishedAt = parseDateMillis(entry.optJSONObject("published")?.optString("\$t"))
        val basePostUrl = normalizePostFeedUrl(linkHref(entry, "self").orEmpty())

        val generatedChapterTitles = extractChapterTitles(html)
        val generatedChapters = GENERATED_CHAPTER_REGEX.findAll(html).map { match ->
            val key = match.groupValues[1]
            val title = match.groupValues[2].ifBlank {
                generatedChapterTitles[key] ?: "Episodio $key"
            }
            val base = match.groupValues[3]
            val start = match.groupValues[4].toInt()
            val end = match.groupValues[5].toInt()

            createChapter(
                name = title,
                chapterKey = key,
                postFeedUrl = basePostUrl,
                uploadedAt = publishedAt,
                chapterNumber = key.toFloatOrNull() ?: 0f,
            )
        }.toList()
        if (generatedChapters.isNotEmpty()) {
            return generatedChapters
        }

        val arrayChapterTitles = extractChapterTitles(html)
        val arrayChapters = ARRAY_CHAPTER_REGEX.findAll(html).map { match ->
            val key = match.groupValues[1]
            val base = match.groupValues[2]
            val start = match.groupValues[3].toInt()
            val end = match.groupValues[4].toInt()
            val extension = if (base.endsWith("_")) ".jpg" else inferExtensionFromBase(base)

            createChapter(
                name = arrayChapterTitles[key] ?: "Episodio $key",
                chapterKey = key,
                postFeedUrl = basePostUrl,
                uploadedAt = publishedAt,
                chapterNumber = key.toFloatOrNull() ?: 0f,
            )
        }.toList()
        if (arrayChapters.isNotEmpty()) {
            return arrayChapters
        }

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
            val base = arrayChapter.groupValues[2]
            val extension = if (base.endsWith("_")) ".jpg" else inferExtensionFromBase(base)
            return buildSequencePages(
                base = base,
                start = arrayChapter.groupValues[3].toInt(),
                end = arrayChapter.groupValues[4].toInt(),
                extension = extension,
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
        url = "$postFeedUrl&$CHAPTER_KEY_PARAM=${chapterKey.urlEncode()}"
        this.name = name
        date_upload = uploadedAt
        this.chapter_number = chapterNumber
        scanlator = null
    }

    private fun buildSequencePages(base: String, start: Int, end: Int, extension: String): List<String> = (start..end).map { "$base$it$extension" }

    private fun inferExtensionFromBase(base: String): String = when {
        base.contains(".webp") -> ".webp"
        base.contains(".png") -> ".png"
        else -> ".jpg"
    }

    private fun extractChapterTitles(html: String): Map<String, String> = OPTION_TITLE_REGEX.findAll(html).associate { match ->
        match.groupValues[1] to cleanHtml(match.groupValues[2])
    }

    private fun extractArtist(html: String): String? {
        ARTIST_REGEX.find(html)?.let { return cleanHtml(it.groupValues[1]) }
        ARTISTS_REGEX.find(html)?.let { return cleanHtml(it.groupValues[1]) }
        return null
    }

    private fun extractThumbnail(entry: JSONObject, html: String): String? {
        val inlineImage = HIDDEN_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!inlineImage.isNullOrBlank()) {
            return inlineImage
        }

        val mediaThumb = entry.optJSONObject("media\$thumbnail")?.optString("url")
        return mediaThumb?.substringBefore("=s72")
    }

    private fun extractCategories(entry: JSONObject): List<String> {
        val categories = entry.optJSONArray("category") ?: return emptyList()
        return buildList(categories.length()) {
            for (index in 0 until categories.length()) {
                categories.optJSONObject(index)?.optString("term")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun linkHref(entry: JSONObject, rel: String): String? {
        val links = entry.optJSONArray("link") ?: return null
        for (index in 0 until links.length()) {
            val link = links.optJSONObject(index) ?: continue
            if (link.optString("rel") == rel) {
                return link.optString("href")
            }
        }
        return null
    }

    private fun normalizePostFeedUrl(url: String): String = if (url.contains("alt=json")) url else "$url${if (url.contains("?")) "&" else "?"}alt=json"

    private fun parseDateMillis(value: String?): Long = value?.takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L

    private fun cleanHtml(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        private const val PAGE_SIZE = 30
        private const val CHAPTER_KEY_PARAM = "samatoChapter"
        private const val SINGLE_CHAPTER_KEY = "single"

        private val HIDDEN_IMAGE_REGEX = Regex("""<img[^>]+src="([^"]+)"""", setOf(RegexOption.IGNORE_CASE))
        private val ARTIST_REGEX = Regex("""<strong>\s*Artista:\s*</strong>\s*([^<]+)""", setOf(RegexOption.IGNORE_CASE))
        private val ARTISTS_REGEX = Regex("""<strong>\s*Artists:\s*</strong>\s*([^<]+)""", setOf(RegexOption.IGNORE_CASE))
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
