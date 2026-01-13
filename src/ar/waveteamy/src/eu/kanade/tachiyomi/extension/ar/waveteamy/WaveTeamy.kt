package eu.kanade.tachiyomi.extension.ar.waveteamy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WaveTeamy : HttpSource() {

    override val name = "WaveTeamy"

    override val baseUrl = "https://waveteamy.com"

    override val lang = "ar"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body.string())
        val mangas = mutableListOf<SManga>()

        // Select manga cards - they have href="/series/{id}"
        document.select("a[href^=/series/]").forEach { element ->
            val href = element.attr("href")
            val id = href.removePrefix("/series/")

            // Skip non-numeric IDs (like /series?page=2)
            if (!id.all { it.isDigit() }) return@forEach

            // Get title from h2 or h3
            val title = element.select("h2, h3").firstOrNull()?.text()?.trim()
                ?: element.attr("alt")
                ?: return@forEach

            if (title.isEmpty()) return@forEach

            // Get thumbnail
            val img = element.select("img").firstOrNull()
            val thumbnail = img?.attr("src")?.takeIf { it.isNotEmpty() }
                ?: img?.attr("data-src")

            mangas.add(
                SManga.create().apply {
                    url = "/series/$id"
                    this.title = title
                    thumbnail_url = thumbnail
                },
            )
        }

        val hasNextPage = document.select("a[href*='page=']").any {
            it.text().contains("التالي") || it.text().contains("Next") || it.attr("href").contains("page=${extractPage(response) + 1}")
        }

        return MangasPage(mangas.distinctBy { it.url }, hasNextPage)
    }

    private fun extractPage(response: Response): Int {
        return response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/series?search=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.body.string()

        return SManga.create().apply {
            // Extract mangaData JSON from the page
            val mangaDataJson = extractMangaData(html)

            if (mangaDataJson != null) {
                title = mangaDataJson["name"]?.jsonPrimitive?.content ?: ""
                description = mangaDataJson["story"]?.jsonPrimitive?.content
                    ?.replace("\\n", "\n")
                author = mangaDataJson["author"]?.jsonPrimitive?.content
                artist = mangaDataJson["artist"]?.jsonPrimitive?.content

                val coverPath = mangaDataJson["cover"]?.jsonPrimitive?.content
                thumbnail_url = if (!coverPath.isNullOrEmpty()) {
                    "https://wcloud.site/$coverPath"
                } else {
                    null
                }

                val statusValue = mangaDataJson["status"]?.jsonPrimitive?.content?.toIntOrNull()
                status = when (statusValue) {
                    0 -> SManga.ONGOING
                    1 -> SManga.COMPLETED
                    2 -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }

                val typeValue = mangaDataJson["type"]?.jsonPrimitive?.content ?: ""
                genre = listOfNotNull(typeValue.takeIf { it.isNotEmpty() }).joinToString()
            }
        }
    }

    private fun extractMangaData(html: String): JsonObject? {
        // Pattern: "mangaData":{"id":553,"name":"Fighting Ward",...}
        val pattern = """"mangaData":\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""".toRegex()
        val match = pattern.find(html) ?: return null

        return try {
            val jsonStr = "{${match.groupValues[1]}}"
            json.parseToJsonElement(jsonStr).jsonObject
        } catch (e: Exception) {
            // Try with escaped quotes
            val escapedPattern = """\"mangaData\":\{([^}]+)\}""".toRegex()
            val escapedMatch = escapedPattern.find(html) ?: return null
            try {
                val unescaped = escapedMatch.groupValues[1].replace("\\\"", "\"")
                json.parseToJsonElement("{$unescaped}").jsonObject
            } catch (e: Exception) {
                null
            }
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        val chapters = mutableListOf<SChapter>()

        val seriesId = response.request.url.pathSegments.lastOrNull() ?: ""

        // Extract chaptersData array from the page
        // Pattern: "chaptersData":[{"id":15410,"chapter":49,...},...]
        val chaptersPattern = """"chaptersData":\[([^\]]+)\]""".toRegex()
        val chaptersMatch = chaptersPattern.find(html)

        if (chaptersMatch != null) {
            val chaptersArrayStr = "[${chaptersMatch.groupValues[1]}]"
            try {
                val chaptersArray = json.parseToJsonElement(chaptersArrayStr).jsonArray

                chaptersArray.forEach { chapterElement ->
                    val chapterObj = chapterElement.jsonObject
                    val chapterId = chapterObj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val chapterNum = chapterObj["chapter"]?.jsonPrimitive?.content ?: return@forEach
                    val chapterTitle = chapterObj["title"]?.jsonPrimitive?.content?.trim() ?: ""
                    val postTime = chapterObj["postTime"]?.jsonPrimitive?.content ?: ""

                    chapters.add(
                        SChapter.create().apply {
                            url = "/series/$seriesId/$chapterId"
                            name = if (chapterTitle.isNotEmpty()) {
                                "الفصل $chapterNum: $chapterTitle"
                            } else {
                                "الفصل $chapterNum"
                            }
                            date_upload = parseDate(postTime)
                            chapter_number = chapterNum.toFloatOrNull() ?: -1f
                        },
                    )
                }
            } catch (e: Exception) {
                // Try with escaped quotes
                parseChaptersEscaped(html, seriesId, chapters)
            }
        } else {
            // Try with escaped quotes
            parseChaptersEscaped(html, seriesId, chapters)
        }

        return chapters
    }

    private fun parseChaptersEscaped(html: String, seriesId: String, chapters: MutableList<SChapter>) {
        // Pattern for escaped JSON: \"chaptersData\":[{\"id\":15410,\"chapter\":49,...}]
        val chapterPattern = """\{\"id\":(\d+),\"chapter\":(\d+)[^}]*\"title\":\"([^\"]*)\",.*?\"postTime\":\"([^\"]*)\"""".toRegex()

        chapterPattern.findAll(html).forEach { match ->
            val chapterId = match.groupValues[1]
            val chapterNum = match.groupValues[2]
            val chapterTitle = match.groupValues[3].trim()
            val postTime = match.groupValues[4]

            chapters.add(
                SChapter.create().apply {
                    url = "/series/$seriesId/$chapterId"
                    name = if (chapterTitle.isNotEmpty()) {
                        "الفصل $chapterNum: $chapterTitle"
                    } else {
                        "الفصل $chapterNum"
                    }
                    date_upload = parseDate(postTime)
                    chapter_number = chapterNum.toFloatOrNull() ?: -1f
                },
            )
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val pages = mutableListOf<Page>()

        // Extract image URLs from the chapter page
        // Pattern: projects/371/1/1749963066128-0-01.jpg or similar
        val imagePattern = """(projects/\d+/\d+/[^"'\s\\]+\.(jpg|png|webp))""".toRegex()
        val matches = imagePattern.findAll(html)

        val imagePaths = matches
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        if (imagePaths.isNotEmpty()) {
            imagePaths.forEachIndexed { index, path ->
                pages.add(Page(index, "", "https://wcloud.site/$path"))
            }
            return pages
        }

        // Fallback: Try to find images in HTML
        val document = Jsoup.parse(html)
        document.select("img[src*=wcloud], img[src*=projects]").forEachIndexed { index, img ->
            val src = img.attr("src").takeIf { it.isNotEmpty() } ?: img.attr("data-src")
            if (src.isNotEmpty()) {
                pages.add(Page(index, "", src))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun getFilterList() = FilterList()
}
