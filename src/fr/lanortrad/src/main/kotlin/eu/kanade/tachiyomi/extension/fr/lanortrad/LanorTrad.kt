package eu.kanade.tachiyomi.extension.fr.lanortrad

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.get
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.time.format.DateTimeFormatter

@Source
abstract class LanorTrad : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        return MangasPage(fetchSeries().map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val series = fetchSeries().sortedByDescending { it.lastUpdate }
        return MangasPage(series.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val series = fetchSeries()
        val filtered = if (query.isBlank()) {
            series
        } else {
            series.filter { it.title.contains(query, ignoreCase = true) }
        }
        return MangasPage(filtered.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // Series data is needed for chapters too: the series type drives oneshot collapsing in buildChapters.
        val seriesDeferred = if (fetchDetails || fetchChapters) async { fetchSeries() } else null
        val indexDeferred = if (fetchChapters) async { fetchChapterData() } else null
        val dto = seriesDeferred?.await()?.find { it.id == manga.url }
        val updatedManga = if (fetchDetails) {
            dto?.toSManga(baseUrl) ?: throw Exception("Manga not found")
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            val chapterData = indexDeferred?.await() ?: throw Exception("Chapters not found")
            buildChapters(manga.url, chapterData.index, dto?.type)
        } else {
            chapters
        }
        SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val seriesId = chapter.url.substringBeforeLast("/")
        val num = chapter.url.substringAfterLast("/")
        val chapterData = fetchChapterData()
        val pagesPath = chapterData.pageFiles[seriesId] ?: return emptyList()
        val seriesNode = chapterData.index[seriesId]?.obj ?: return emptyList()
        val defaultPrefix = seriesNode.getStringOrNull("p") ?: ""
        val folder = seriesNode["c"]?.array?.let { findFolder(it, defaultPrefix, num) } ?: return emptyList()
        val pagesFile = client.get("$baseUrl/$pagesPath").parseAs<JsonObject> {
            it.substringAfterLast("=").substringBeforeLast(";")
        }
        val files = runCatching {
            pagesFile[num]?.obj?.get("f")?.array?.map { it.string }.orEmpty()
        }.getOrNull().orEmpty()
        return files.mapIndexed { i, file ->
            Page(i, imageUrl = buildImageUrl(seriesId, folder, file))
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${slugify(manga.url)}/"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = slugify(chapter.url.substringBeforeLast("/"))
        return if (chapter.name == "Oneshot") {
            "$baseUrl/manga/$slug/lecture/"
        } else {
            "$baseUrl/manga/$slug/chapitre-${chapter.url.substringAfterLast("/")}/"
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val dtos = fetchSeries()
        val dto = when (url.pathSegments.firstOrNull()) {
            "manga" -> {
                val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                dtos.find { slugify(it.id) == slug }
            }
            "manga.html" -> url.queryParameter("id")?.let { id -> dtos.find { it.id == id } }
            "reader.html" -> url.queryParameter("manga")?.let { id -> dtos.find { it.id == id } }
            else -> return null
        } ?: return null
        return dto.toSManga(baseUrl)
    }

    private suspend fun fetchSeries(): List<Dto> = client.get("$baseUrl/js/data/series.js").parseAs {
        quoteUnquotedKeys(
            it.replace(COMMENT_REGEX, "")
                .substringAfter("window.SERIES =")
                .substringBeforeLast(";"),
        )
    }

    // Single read: chapter index and page map share one response.
    private suspend fun fetchChapterData(): ChapterData {
        val body = client.get("$baseUrl/js/data/chapters.js").use { it.body.string() }
        val index = body.substringAfter("return expand(")
            .substringBeforeLast("})();")
            .substringBeforeLast(");")
            .parseAs<JsonObject>()
        val pages = CHAPTER_PAGES_REGEX.find(body)?.groupValues?.get(1)?.parseAs<Map<String, String>>().orEmpty()
        return ChapterData(index, pages)
    }

    private class ChapterData(
        val index: JsonObject,
        val pageFiles: Map<String, String>,
    )

    private fun buildChapters(seriesId: String, index: JsonObject, seriesType: String?): List<SChapter> {
        val entries = index[seriesId]?.obj?.get("c")?.array ?: return emptyList()
        val chapters = entries.mapNotNull { entry ->
            runCatching {
                val item = entry.array
                val num = item[0].string
                val opts = item.getOrNull(2)?.obj
                SChapter.create().apply {
                    url = "$seriesId/$num"
                    name = "Chapitre $num"
                    chapter_number = num.toFloatOrNull() ?: -1f
                    date_upload = DATE_FORMAT.tryParseDate(opts?.getStringOrNull("d"))
                }
            }.getOrNull()
        }.distinctBy { it.url }.sortedByDescending { it.chapter_number }
        if (seriesType.equals("oneshot", true)) {
            val oneshot = chapters.firstOrNull() ?: return emptyList()
            oneshot.name = "Oneshot"
            return listOf(oneshot)
        }
        return chapters
    }

    private fun findFolder(entries: JsonArray, defaultPrefix: String, num: String): String? {
        for (entry in entries) {
            val item = runCatching { entry.array }.getOrNull() ?: continue
            if (runCatching { item[0].string }.getOrNull() != num) continue
            val opts = item.getOrNull(2)?.let { runCatching { it.obj }.getOrNull() }
            return opts?.getStringOrNull("f") ?: ((opts?.getStringOrNull("p") ?: defaultPrefix) + num)
        }
        return null
    }

    private fun buildImageUrl(seriesId: String, folder: String, file: String): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("Manga")
            .addPathSegment(seriesId)
        folder.split("/").forEach { builder.addPathSegment(it) }
        return builder.addPathSegment(file).build().toString()
    }

    private fun slugify(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .replace(NON_ALNUM_REGEX, "-")
        .trim('-')
        .lowercase()

    // Keys may share a line with braces or other entries, so a line-anchored
    // regex cannot quote them all; scan outside string literals instead.
    private fun quoteUnquotedKeys(input: String): String = buildString(input.length + 64) {
        var i = 0
        var inString = false
        while (i < input.length) {
            val c = input[i]
            if (inString) {
                append(c)
                if (c == '\\' && i + 1 < input.length) {
                    append(input[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') inString = false
                i++
                continue
            }
            when {
                c == '"' -> {
                    inString = true
                    append(c)
                    i++
                }
                c.isLetterOrDigit() || c == '_' -> {
                    var j = i
                    while (j < input.length && (input[j].isLetterOrDigit() || input[j] == '_')) j++
                    var k = j
                    while (k < input.length && input[k].isWhitespace()) k++
                    if (k < input.length && input[k] == ':') {
                        append('"').append(input, i, j).append("\":")
                        i = k + 1
                    } else {
                        append(input, i, j)
                        i = j
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    companion object {
        private val COMMENT_REGEX = Regex("""^\s*//.*$""", RegexOption.MULTILINE)
        private val CHAPTER_PAGES_REGEX = Regex("""window\.CHAPTER_PAGES\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
        private val COMBINING_MARKS_REGEX = Regex("""\p{Mn}+""")
        private val NON_ALNUM_REGEX = Regex("[^A-Za-z0-9]+")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
