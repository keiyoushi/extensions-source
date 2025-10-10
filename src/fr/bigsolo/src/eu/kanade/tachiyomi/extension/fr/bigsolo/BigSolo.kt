package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BigSolo : ParsedHttpSource() {
    override val name = "BigSolo"
    override val baseUrl = "https://bigsolo.org"
    override val lang = "fr"
    override val supportsLatest = false

    private var currentSearchQuery = ""

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
        )

    override fun popularMangaRequest(page: Int): Request {
        currentSearchQuery = ""
        return GET("$baseUrl/data/config.json", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        currentSearchQuery = query
        return GET("$baseUrl/data/config.json", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val configJson = response.body.string()
        val jsonObject = try {
            JSONObject(configJson)
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Failed to parse config.json: ${e.message}", e)
        }
        val seriesFiles = try {
            jsonObject.getJSONArray("LOCAL_SERIES_FILES")
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Missing LOCAL_SERIES_FILES in config.json", e)
        }

        val mangaList = mutableListOf<SManga>()

        // Filter series files according to the stored query
        for (i in 0 until seriesFiles.length()) {
            val fileName = seriesFiles.getString(i)
            val fileUrl = "$baseUrl/data/series/$fileName"

            try {
                val fileResponse = client.newCall(GET(fileUrl, headers)).execute()
                val fileContent = fileResponse.body.string()
                val seriesJson = JSONObject(fileContent)

                val title = seriesJson.optString("title")

                // Filter by title if a query is provided
                if (currentSearchQuery.isBlank() || title.contains(
                        currentSearchQuery,
                        ignoreCase = true,
                    )
                ) {
                    val manga = SManga.create().apply {
                        this.title = title
                        artist = seriesJson.optString("artist")
                        author = seriesJson.optString("author")
                        thumbnail_url = seriesJson.optString("cover_low")
                        url = "/${toSlug(title)}"
                    }
                    mangaList.add(manga)
                }
            } catch (e: java.io.IOException) {
                // Could not load the file, skip and continue
                // throw or log if needed, here we skip silently
                continue
            } catch (e: org.json.JSONException) {
                // Malformed JSON, skip and continue
                continue
            }
        }

        return MangasPage(mangaList, false)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun searchMangaSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val jsonData = document.selectFirst("#series-data-placeholder")?.html()
        if (jsonData != null) {
            val seriesJson = try {
                JSONObject(jsonData)
            } catch (e: org.json.JSONException) {
                throw IllegalStateException("Failed to parse manga details JSON", e)
            }
            title = seriesJson.optString("title")
            description = seriesJson.optString("description")
            artist = seriesJson.optString("artist")
            author = seriesJson.optString("author")
            genre = seriesJson.optJSONArray("tags")?.let { tagsArray ->
                List(tagsArray.length()) { index -> tagsArray.getString(index) }.joinToString(", ")
            } ?: ""
            status = when (seriesJson.optString("release_status")) {
                "En cours" -> SManga.ONGOING
                "Finis", "Fini" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = seriesJson.optString("cover_hq")
        } else {
            throw IllegalStateException("JSON data not found for manga details")
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val currentUrl = document.location()
        val chapterNumber = currentUrl.substringAfterLast("/")

        // Extract the ID from the chapter URL
        val jsonData = document.selectFirst("#reader-data-placeholder")?.html()
        val seriesJson = try {
            JSONObject(jsonData).optJSONObject("series")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse reader data JSON", e)
        }
        val chaptersJson = seriesJson?.optJSONObject("chapters")
        val currentChapter = chaptersJson?.optJSONObject(chapterNumber)
        val groups = currentChapter?.optJSONObject("groups")

        var chapterId = ""
        if (groups != null) {
            val keys = groups.keys()
            if (keys.hasNext()) {
                val firstGroupKey = keys.next()
                val firstGroup = groups.optString(firstGroupKey)
                chapterId = firstGroup.substringAfterLast("/")
            }
        }

        // Request the API with the ID
        val pagesResponse =
            try {
                client.newCall(GET("$baseUrl/api/imgchest-chapter-pages?id=$chapterId", headers)).execute()
            } catch (e: java.io.IOException) {
                throw IllegalStateException("Failed to fetch chapter pages", e)
            }
        val pagesJson = try {
            org.json.JSONArray(pagesResponse.body.string())
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Failed to parse chapter pages JSON", e)
        }
        val pages = mutableListOf<Page>()

        // Parse the JSON response to create the pages
        for (i in 0 until pagesJson.length()) {
            pages.add(
                Page(
                    i,
                    imageUrl = pagesJson.getJSONObject(i).optString("link"),
                ),
            )
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // Chapters
    override fun chapterListSelector() = "#series-data-placeholder"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val jsonData = document.selectFirst("#series-data-placeholder")?.html()
        if (jsonData != null) {
            val seriesJson = try {
                JSONObject(jsonData)
            } catch (e: org.json.JSONException) {
                throw IllegalStateException("Failed to parse series data JSON", e)
            }
            val chaptersJson = seriesJson.optJSONObject("chapters")
            val chapterList = mutableListOf<SChapter>()

            if (chaptersJson != null) {
                val keys = chaptersJson.keys().asSequence().toList()
                val multipleChapters = keys.size > 1

                for (chapterNumber in keys) {
                    val chapterData = chaptersJson.getJSONObject(chapterNumber)
                    if (chapterData.optBoolean("licencied", false)) continue

                    val title = chapterData.optString("title")
                    val volumeNumber = chapterData.optString("volume")
                    val baseName = if (multipleChapters) {
                        volumeNumber.takeIf { it.isNotBlank() }?.let { "Vol. $it " }.orEmpty() +
                            "Ch. $chapterNumber" +
                            title.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty()
                    } else {
                        // If only one chapter: just the title, otherwise fallback
                        if (title.isNotBlank()) "One Shot – $title" else "One Shot"
                    }

                    val chapter = SChapter.create().apply {
                        name = baseName
                        url = "/${toSlug(seriesJson.optString("title"))}/$chapterNumber"
                        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                        date_upload = chapterData.optLong("last_updated") * 1000L
                    }
                    chapterList.add(chapter)
                }
            }

            return chapterList.sortedByDescending { it.chapter_number }
        } else {
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    fun toSlug(input: String?): String {
        if (input == null) return ""

        val accentsMap = mapOf(
            'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
            'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
            'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
            'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
            'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
            'ç' to 'c', 'ñ' to 'n',
        )

        return input
            .lowercase()
            .map { accentsMap[it] ?: it }
            .joinToString("")
            .replace("[^a-z0-9\\s-]".toRegex(), "")
            .replace("\\s+".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-')
    }
}
