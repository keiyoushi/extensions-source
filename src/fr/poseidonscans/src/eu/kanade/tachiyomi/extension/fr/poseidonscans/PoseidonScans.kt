package eu.kanade.tachiyomi.extension.fr.poseidonscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PoseidonScans : ParsedHttpSource() {

    override val name = "Poseidon Scans"
    override val baseUrl = "https://poseidonscans.fr"
    override val lang = "fr"
    override val supportsLatest = true

    override val client = network.cloudflareClient
    private val json: Json by injectLazy()

    private fun String.toAbsoluteUrl(): String {
        return if (this.startsWith("http")) this else baseUrl + this
    }

    private fun String.toApiCoverUrl(): String {
        if (this.startsWith("http")) return this
        if (this.contains("storage/covers/")) return "$baseUrl/api/covers/${this.substringAfter("storage/covers/")}"
        if (this.startsWith("/api/covers/")) return baseUrl + this
        if (this.startsWith("/")) return baseUrl + this
        return "$baseUrl/api/covers/$this"
    }

    @kotlinx.serialization.Serializable
    data class LatestApiManga(
        val title: String,
        val slug: String,
        val coverImage: String?,
    )

    @kotlinx.serialization.Serializable
    data class LatestApiResponse(
        val success: Boolean,
        val data: List<LatestApiManga> = emptyList(),
        val total: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class MangaDetailsData(
        val title: String,
        val slug: String,
        val description: String?,
        val coverImage: String?,
        val type: String?,
        val status: String?,
        val artist: String?,
        val author: String?,
        val alternativeNames: String?,
        val categories: List<CategoryData>? = emptyList(),
        val chapters: List<ChapterData>? = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class CategoryData(val name: String)

    @kotlinx.serialization.Serializable
    data class ChapterData(
        val number: Float,
        val title: String? = null,
        val createdAt: String,
        val isPremium: Boolean? = false,
    )

    @kotlinx.serialization.Serializable
    data class PageImageUrlData(
        val originalUrl: String,
        val order: Int,
    )

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/manga/lastchapters?limit=16&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseBody = response.body.string()
        val apiResponse = try {
            json.decodeFromString<LatestApiResponse>(responseBody)
        } catch (e: Exception) {
            return MangasPage(emptyList(), false)
        }

        val mangas = apiResponse.data.mapNotNull { apiManga ->
            if (apiManga.slug.isBlank()) {
                return@mapNotNull null
            }
            SManga.create().apply {
                title = apiManga.title
                url = "/serie/${apiManga.slug}"
                thumbnail_url = apiManga.coverImage?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
            }
        }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector(): String =
        "main > section:nth-of-type(3) div.flex.gap-4 > div.group"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            val anchor = element.selectFirst("a.block")
                ?: throw Exception("Popular: Anchor not found")
            url = anchor.attr("href").takeIf { it.isNotBlank() }
                ?: throw Exception("Popular: Empty href")

            val img = element.selectFirst("img")
                ?: throw Exception("Popular: Image not found")

            title = element.selectFirst("h3.text-sm.sm\\:text-base")?.text()
                ?.takeIf { it.isNotBlank() }
                ?: img.attr("alt").takeIf { it.isNotBlank() }
                ?: "Titre Inconnu" // Unknown Title

            thumbnail_url = img.attr("src").takeIf { it.isNotBlank() }?.toApiCoverUrl()
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Extracts Next.js page data, trying __NEXT_DATA__ script first, then self.__next_f.push.
    private fun extractNextJsPageData(document: Document): JsonObject? {
        val currentUrl = document.location()
        val isSeriesPage = currentUrl.contains("/series")

        try {
            document.selectFirst("script#__NEXT_DATA__")?.data()?.also { scriptData ->
                try {
                    val rootJson = json.decodeFromString<JsonObject>(scriptData)
                    val pageProps = rootJson["props"]?.jsonObject?.get("pageProps")?.jsonObject
                    if (pageProps != null) {
                        if (isSeriesPage) {
                            if (pageProps.containsKey("initialData") || pageProps.containsKey("mangas") || pageProps.containsKey("series")) {
                                return pageProps
                            }
                        } else {
                            if (pageProps.containsKey("initialData") || pageProps.containsKey("manga") ||
                                pageProps.containsKey("chapter") || pageProps.containsKey("images")
                            ) {
                                return pageProps
                            }
                        }
                    }
                    rootJson["initialData"]?.jsonObject?.let { initialData ->
                        if (isSeriesPage) {
                            if (initialData.containsKey("mangas") || initialData.containsKey("series")) {
                                return initialData
                            }
                        } else {
                            if (initialData.containsKey("manga") || initialData.containsKey("chapter") || initialData.containsKey("images")) {
                                return initialData
                            }
                        }
                    }
                } catch (e: Exception) {
                    // silently ignore
                }
            }

            val nextFPushRegex = Regex("""self\.__next_f\.push\(\s*\[\s*1\s*,\s*"(.*)"\s*\]\s*\)""", RegexOption.DOT_MATCHES_ALL)
            val mangaSlugForDetails = if (!isSeriesPage) currentUrl.substringAfterLast("/serie/", "").substringBefore('/', "").substringBefore('?', "") else ""
            var foundRelevantObject: JsonObject? = null

            scriptLoop@ for (script in document.select("script")) {
                val scriptContent = script.data()
                if (!scriptContent.contains("self.__next_f.push")) continue
                var objectFoundInThisScript = false

                nextFPushRegex.findAll(scriptContent).forEach nextFPushMatch@{ matchResult ->
                    if (objectFoundInThisScript || foundRelevantObject != null) return@nextFPushMatch
                    if (matchResult.groupValues.size < 2) return@nextFPushMatch

                    val rawDataString = matchResult.groupValues[1]
                    val cleanedDataString = rawDataString.replace("\\\\", "\\").replace("\\\"", "\"")

                    if (isSeriesPage) {
                        val seriesMarkers = listOf("\"mangas\":[", "\"series\":[")
                        for (marker in seriesMarkers) {
                            var searchIdx = -1
                            while (true) {
                                searchIdx = cleanedDataString.indexOf(marker, startIndex = searchIdx + 1)
                                if (searchIdx == -1) break
                                var objectStartIndex = -1
                                var braceDepth = 0
                                for (i in searchIdx downTo 0) {
                                    when (cleanedDataString[i]) {
                                        '}' -> braceDepth++
                                        '{' -> { if (braceDepth == 0) { objectStartIndex = i; break }; braceDepth-- }
                                    }
                                }
                                if (objectStartIndex != -1) {
                                    val potentialJson = extractJsonObjectString(cleanedDataString, objectStartIndex)
                                    if (potentialJson != null) {
                                        try {
                                            val parsedContainer = json.decodeFromString<JsonObject>(potentialJson)
                                            if (parsedContainer.containsKey(marker.substringBefore(':').trim('"')) ||
                                                parsedContainer.containsKey("initialData")
                                            ) {
                                                foundRelevantObject = parsedContainer
                                                objectFoundInThisScript = true
                                                return@nextFPushMatch
                                            }
                                        } catch (e: Exception) { /* silently ignore */ }
                                    }
                                }
                            }
                            if (objectFoundInThisScript) break
                        }
                    } else { // Non-series page
                        fun tryParseAndValidate(marker: String, data: String): JsonObject? {
                            var searchIndex = -1
                            while (true) {
                                searchIndex = data.indexOf(marker, startIndex = searchIndex + 1)
                                if (searchIndex == -1) break
                                val objectStartIndex = searchIndex + marker.length - 1
                                val potentialJson = extractJsonObjectString(data, objectStartIndex) ?: continue
                                try {
                                    val parsedObject = json.decodeFromString<JsonObject>(potentialJson)
                                    val isSane = when (marker) {
                                        "\"initialData\":{" -> parsedObject.containsKey("manga") || parsedObject.containsKey("chapter") || parsedObject.containsKey("images")
                                        "\"manga\":{" -> parsedObject["slug"]?.jsonPrimitive?.content?.contains(mangaSlugForDetails) == true
                                        "\"chapter\":{" -> parsedObject.containsKey("images")
                                        else -> true
                                    }
                                    if (isSane) return parsedObject
                                } catch (e: Exception) { /* silently ignore */ }
                            }
                            return null
                        }
                        if (foundRelevantObject == null) foundRelevantObject = tryParseAndValidate("\"initialData\":{", cleanedDataString)
                        if (foundRelevantObject == null) foundRelevantObject = tryParseAndValidate("\"manga\":{", cleanedDataString)
                        if (foundRelevantObject == null) foundRelevantObject = tryParseAndValidate("\"chapter\":{", cleanedDataString)

                        if (foundRelevantObject != null) {
                            objectFoundInThisScript = true
                            return@nextFPushMatch
                        }
                    }
                }
                if (objectFoundInThisScript || foundRelevantObject != null) break@scriptLoop
            }
            if (foundRelevantObject != null) return foundRelevantObject
        } catch (e: Exception) { /* silently ignore general errors */ }
        return null
    }

    private fun extractJsonObjectString(data: String, startIndex: Int): String? {
        if (startIndex < 0 || startIndex >= data.length || data[startIndex] != '{') {
            return null
        }
        var braceBalance = 0
        var inString = false
        var escape = false
        var endIndex = -1

        for (i in startIndex until data.length) {
            val char = data[i]
            if (escape) {
                escape = false
                continue
            }
            if (char == '\\' && inString) {
                if (i + 1 < data.length && "\"\\/bfnrtu".contains(data[i + 1])) {
                    escape = true
                    continue
                }
            }
            if (char == '"') {
                inString = !inString
            }
            if (!inString) {
                when (char) {
                    '{' -> braceBalance++
                    '}' -> {
                        braceBalance--
                        if (braceBalance == 0) {
                            endIndex = i
                            break
                        }
                    }
                }
            }
            if (braceBalance < 0) return null
        }
        return if (endIndex != -1) data.substring(startIndex, endIndex + 1) else null
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val pageData = extractNextJsPageData(document)
            ?: throw Exception("Could not extract Next.js data. URL: ${document.location()}")

        val mangaDetailsJson = pageData["manga"]?.jsonObject
            ?: pageData["initialData"]?.jsonObject?.get("manga")?.jsonObject
            ?: pageData.takeIf { it.containsKey("slug") && it.containsKey("title") }
            ?: throw Exception("JSON 'manga' structure not found. JSON: ${pageData.toString().take(500)}")

        val mangaDto = try {
            json.decodeFromString<MangaDetailsData>(mangaDetailsJson.toString())
        } catch (e: Exception) {
            throw Exception("Error parsing manga details: ${e.message}. JSON: $mangaDetailsJson")
        }

        return SManga.create().apply {
            title = mangaDto.title
            thumbnail_url = "$baseUrl/api/covers/${mangaDto.slug}.webp"
            author = mangaDto.author?.takeIf { it.isNotBlank() }
            artist = mangaDto.artist?.takeIf { it.isNotBlank() }

            val genresList = mangaDto.categories?.mapNotNull { it.name?.trim() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            genre = genresList.joinToString(", ") { genreName ->
                genreName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
            }

            status = parseStatus(mangaDto.status)

            var potentialDescription: String? = null
            val descriptionSelector = "p.text-gray-300.leading-relaxed.whitespace-pre-line"

            try {
                val htmlDescriptionElement = document.selectFirst(descriptionSelector)
                if (htmlDescriptionElement != null) {
                    val htmlText = htmlDescriptionElement.text()?.trim()
                    if (!htmlText.isNullOrBlank()) {
                        potentialDescription = htmlText
                            .replaceFirst("Dans : ${mangaDto.title}", "")
                            .trim()
                    }
                }
            } catch (e: Exception) { /* Silently ignore error fetching HTML description */ }

            if (potentialDescription.isNullOrBlank()) {
                val jsonDescription = mangaDto.description?.trim()
                if (!jsonDescription.isNullOrBlank() && jsonDescription.length > 5 && !jsonDescription.startsWith("$")) {
                    potentialDescription = jsonDescription
                }
            }

            var finalDesc = potentialDescription?.takeIf { it.isNotBlank() } ?: "Aucune description."

            mangaDto.alternativeNames?.takeIf { it.isNotBlank() }?.let { altNames ->
                val trimmedAltNames = altNames.trim()
                if (finalDesc == "Aucune description.") {
                    finalDesc = "Noms alternatifs: $trimmedAltNames"
                } else {
                    finalDesc += "\n\nNoms alternatifs: $trimmedAltNames"
                }
            }
            description = finalDesc
            url = "/serie/${mangaDto.slug}"
        }
    }

    private fun parseStatus(statusString: String?): Int {
        return when (statusString?.trim()?.lowercase(Locale.FRENCH)) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "en pause", "hiatus" -> SManga.ON_HIATUS
            "annulé", "abandonné" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val pageData = extractNextJsPageData(document)
            ?: throw Exception("Could not extract Next.js data for chapter list.")

        val mangaDetailsJson = pageData["manga"]?.jsonObject
            ?: pageData["initialData"]?.jsonObject?.get("manga")?.jsonObject
            ?: pageData.takeIf { it.containsKey("slug") && it.containsKey("title") }
            ?: throw Exception("JSON 'manga' structure not found for chapters. JSON: ${pageData.toString().take(500)}")

        val mangaDto = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<MangaDetailsData>(mangaDetailsJson.toString())
        } catch (e: Exception) {
            throw Exception("Error parsing chapters: ${e.message}. JSON: $mangaDetailsJson")
        }

        return mangaDto.chapters
            ?.filter { it.isPremium != true }
            ?.mapNotNull { ch ->
                val chapterNumberString = ch.number.toString().removeSuffix(".0")
                SChapter.create().apply {
                    name = ch.title?.takeIf { it.isNotBlank() } ?: "Chapitre $chapterNumberString"
                    url = "/serie/${mangaDto.slug}/chapter/$chapterNumberString"
                    date_upload = parseIsoDate(ch.createdAt)
                    chapter_number = ch.number
                }
            }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    private fun parseIsoDate(dateString: String?): Long {
        return try {
            if (dateString.isNullOrBlank()) return 0L
            val cleanedDateString = if (dateString.startsWith("\"\$D")) {
                dateString.removePrefix("\"\$D").removeSuffix("\"")
            } else if (dateString.startsWith("\$D")) {
                dateString.removePrefix("\$D")
            } else if (dateString.startsWith("\"") && dateString.endsWith("\"") && dateString.length > 2) {
                dateString.substring(1, dateString.length - 1)
            } else {
                dateString
            }
            isoDateFormatter.parse(cleanedDateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageData = extractNextJsPageData(document)
            ?: throw Exception("Could not extract Next.js data for page list.")

        val chapterPageUrl = document.location()

        val imagesListJson = pageData["images"]?.jsonArray
            ?: pageData["chapter"]?.jsonObject?.get("images")?.jsonArray
            ?: pageData["initialData"]?.jsonObject?.get("images")?.jsonArray
            ?: pageData["initialData"]?.jsonObject?.get("chapter")?.jsonObject?.get("images")?.jsonArray
            ?: throw Exception("JSON 'images' structure not found. Data: ${pageData.toString().take(500)}")

        val imagesDataList = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<PageImageUrlData>>(imagesListJson.toString())
        } catch (e: Exception) {
            throw Exception("Error parsing image list: ${e.message}. JSON: $imagesListJson")
        }

        return imagesDataList.map { pageDto ->
            Page(
                index = pageDto.order,
                url = chapterPageUrl, // For Referer in imageRequest
                imageUrl = pageDto.originalUrl.toAbsoluteUrl(),
            )
        }.sortedBy { it.index }
    }

    override fun imageRequest(page: Page): Request {
        val refererUrl = page.url
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", if (refererUrl.isNotBlank()) refererUrl else "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga) // Data is on the same page
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    /**
     * Tachiyomi's `page` parameter is not directly used as /series does not paginate via URL params.
     * We fetch all series and filter client-side based on the query.
     * The query is passed as `app_query` URL parameter for retrieval in `searchMangaParse`.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (e: Exception) {
                query // Fallback if encoding fails (highly unlikely)
            }
            "$baseUrl/series?app_query=$encodedQuery"
        } else {
            "$baseUrl/series"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestUrl = response.request.url
        val searchQuery = requestUrl.queryParameter("app_query")?.takeIf { it.isNotBlank() } ?: ""

        val pageDataJson = extractNextJsPageData(document)
            ?: return MangasPage(emptyList(), false)

        val mangaListJsonArray = pageDataJson["mangas"]?.jsonArray
            ?: pageDataJson["series"]?.jsonArray
            ?: pageDataJson["initialData"]?.jsonObject?.get("mangas")?.jsonArray
            ?: pageDataJson["initialData"]?.jsonObject?.get("series")?.jsonArray
            ?: return MangasPage(emptyList(), false)

        val allMangas = mangaListJsonArray.mapNotNull { mangaElement ->
            try {
                val mangaObject = mangaElement.jsonObject
                val title = mangaObject["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val slug = mangaObject["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val cover = mangaObject["coverImage"]?.jsonPrimitive?.content

                SManga.create().apply {
                    this.title = title
                    this.url = "/serie/$slug"
                    this.thumbnail_url = cover?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
                }
            } catch (e: Exception) {
                null
            }
        }

        val filteredMangas = if (searchQuery.isNotBlank()) {
            allMangas.filter { manga ->
                manga.title.contains(searchQuery, ignoreCase = true)
            }
        } else {
            allMangas
        }

        // /series loads all items at once (client-side 'load more'), so no next page from this specific request.
        val hasNextPage = false
        return MangasPage(filteredMangas, hasNextPage)
    }

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Uses JSON API")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Uses JSON API")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Uses JSON API")

    override fun chapterListSelector() = throw UnsupportedOperationException("Uses JSON")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Uses JSON")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Uses pageListParse")

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Uses JSON")
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Uses JSON")
    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException("No URL pagination for search")

    override fun getFilterList(): FilterList = FilterList()
}
