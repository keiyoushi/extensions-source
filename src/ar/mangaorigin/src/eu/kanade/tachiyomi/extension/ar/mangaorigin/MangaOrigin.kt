package eu.kanade.tachiyomi.extension.ar.mangaorigin

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Source
abstract class MangaOrigin : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        // Only the series posts carry this sentence, with the link to their chapters
        val search = buildString {
            append("\"$SERIES_SENTENCE\"")
            genre?.let { append(" label:\"$it\"") }
            if (query.isNotBlank()) append(" ${query.trim()}")
        }
        val url = feedUrl(baseUrl, "summary")
            .addQueryParameter("q", search)
            .addQueryParameter("start-index", ((page - 1) * PAGE_SIZE + 1).toString())
            .addQueryParameter("max-results", (PAGE_SIZE + 1).toString())
            .build()
        val entries = client.get(url, Fetch.XHR).parseAs<FeedResponse>().entries
        val mangas = entries.take(PAGE_SIZE).filter(EntryDto::isSeries).map(EntryDto::toSManga)
        return MangasPage(mangas, entries.size > PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val isReader = url.host == READER_HOST
        if (!isReader && url.host != baseUrl.toHttpUrl().host) return null
        val path = url.encodedPath
        val label = when {
            LABEL_PATH_REGEX.matches(path) -> url.pathSegments[2]
            // The team blog mostly posts a chapter's announcement at the chapter's path on the reader blog
            POST_PATH_REGEX.matches(path) -> {
                val entry = findEntry(baseUrl, path)
                    ?: (if (isReader) findEntry(READER_URL, path, "summary") else null)
                    ?: return null
                if (entry.isSeries) return entry.toDetailedSManga().apply { initialized = true }
                entry.seriesLabel ?: return null
            }
            else -> return null
        }
        val series = getSeries(label) ?: return null
        return getEntry(baseUrl, series.path).toDetailedSManga().apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val readerLabel = manga.memo[READER_LABEL]?.stringOrNull
        if (!fetchDetails && readerLabel != null) {
            return SMangaUpdate(manga, if (fetchChapters) getChapterList(readerLabel) else chapters)
        }
        val entry = getEntry(baseUrl, manga.url)
        val label = readerLabel ?: entry.seriesLabel?.let { getReaderLabel(it) }
        val details = entry.toDetailedSManga().apply {
            memo = buildJsonObject { label?.let { put(READER_LABEL, it) } }
        }
        val chapterList = when {
            !fetchChapters -> chapters
            label == null -> emptyList()
            else -> getChapterList(label)
        }
        return SMangaUpdate(details, chapterList)
    }

    // The chapters are on the reader blog, under a label that is sometimes spelled differently
    // ("Kaguya-Sama", "In the Bleak Midwinter"): the team blog's announcements link to them
    private suspend fun getReaderLabel(seriesLabel: String): String? {
        val url = feedUrl(baseUrl, "default", seriesLabel).addQueryParameter("max-results", "25").build()
        val paths = client.get(url, Fetch.XHR).parseAs<FeedResponse>().entries
            .flatMap { entry -> READER_LINK_REGEX.findAll(entry.html.orEmpty()).map { it.groupValues[1] } }
            .distinct()
        return paths.take(5).firstNotNullOfOrNull { findEntry(READER_URL, it, "summary")?.seriesLabel }
    }

    private suspend fun getChapterList(readerLabel: String): List<SChapter> = coroutineScope {
        val first = getChapterPage(readerLabel, 1)
        val rest = (CHAPTER_PAGE_SIZE + 1..first.total step CHAPTER_PAGE_SIZE)
            .map { start -> async { getChapterPage(readerLabel, start) } }
            .awaitAll()
        (listOf(first) + rest).flatMap(FeedResponse::entries).map(EntryDto::toSChapter)
    }

    private suspend fun getChapterPage(readerLabel: String, start: Int): FeedResponse {
        val url = feedUrl(READER_URL, "summary", readerLabel)
            .addQueryParameter("start-index", start.toString())
            .addQueryParameter("max-results", CHAPTER_PAGE_SIZE.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    override fun getChapterUrl(chapter: SChapter) = "$READER_URL${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = getEntry(READER_URL, chapter.url).html!!
        return Jsoup.parseBodyFragment(html, READER_URL).select("img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src").toOriginalSize())
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    // A label of the team blog lists its series post with the chapter announcements. The reader
    // blog spells some labels differently ("Kaguya-Sama"): then the series whose text has the label
    private suspend fun getSeries(label: String): EntryDto? = searchSeries("label:\"$label\"").firstOrNull()
        ?: searchSeries(label).singleOrNull()

    private suspend fun searchSeries(terms: String): List<EntryDto> {
        val url = feedUrl(baseUrl, "summary")
            .addQueryParameter("q", "\"$SERIES_SENTENCE\" $terms")
            .addQueryParameter("max-results", "5")
            .build()
        return client.get(url, Fetch.XHR).parseAs<FeedResponse>().entries.filter(EntryDto::isSeries)
    }

    private suspend fun getEntry(blogUrl: String, path: String, projection: String = "default"): EntryDto = client.get(entryUrl(blogUrl, path, projection), Fetch.XHR).parseAs<FeedResponse>().entries.single()

    // Blogger answers 404 for a path without a post, and some links of the site lead nowhere
    private suspend fun findEntry(blogUrl: String, path: String, projection: String = "default"): EntryDto? {
        val response = client.get(entryUrl(blogUrl, path, projection), Fetch.XHR, ensureSuccess = false)
        if (response.code == 404) {
            response.close()
            return null
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.parseAs<FeedResponse>().entries.singleOrNull()
    }

    private fun entryUrl(blogUrl: String, path: String, projection: String) = feedUrl(blogUrl, projection).addEncodedQueryParameter("path", path).build()

    private fun feedUrl(blogUrl: String, projection: String, label: String? = null) = blogUrl.toHttpUrl().newBuilder()
        .addPathSegments("feeds/posts/$projection")
        .apply { label?.let { addPathSegment("-").addPathSegment(it) } }
        .addQueryParameter("alt", "json")

    companion object {
        private const val READER_HOST = "mangaoriginread.blogspot.com"
        private const val READER_URL = "https://$READER_HOST"
        private const val READER_LABEL = "readerLabel"
        private const val SERIES_SENTENCE = "جميع الفصول"

        // Blogger leaves some posts out of feed pages of 100 or more
        private const val PAGE_SIZE = 25
        private const val CHAPTER_PAGE_SIZE = 50

        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")
        private val LABEL_PATH_REGEX = Regex("""/search/label/[^/]+""")
        private val READER_LINK_REGEX = Regex("""https?://${Regex.escape(READER_HOST)}(/\d{4}/\d{2}/[^"'?#]+\.html)""")
    }
}
