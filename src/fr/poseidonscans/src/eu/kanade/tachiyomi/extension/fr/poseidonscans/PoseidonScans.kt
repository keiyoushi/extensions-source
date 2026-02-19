package eu.kanade.tachiyomi.extension.fr.poseidonscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PoseidonScans : HttpSource() {

    override val name = "Poseidon Scans"
    override val baseUrl = "https://poseidon-scans.co"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2
    private val nextFPushRegex = Regex("""self\.__next_f\.push\(\s*\[\s*1\s*,\s*"(.*)"\s*\]\s*\)""", RegexOption.DOT_MATCHES_ALL)

    override val client = network.cloudflareClient

    private fun String.toAbsoluteUrl(): String = if (this.startsWith("http")) this else baseUrl + this

    private fun String.toApiCoverUrl(): String {
        if (this.startsWith("http")) return this
        if (this.contains("storage/covers/")) return "$baseUrl/api/covers/${this.substringAfter("storage/covers/")}"
        if (this.startsWith("/api/covers/")) return baseUrl + this
        if (this.startsWith("/")) return baseUrl + this
        return "$baseUrl/api/covers/$this"
    }

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga/lastchapters?limit=16&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val apiResponse = try {
            response.parseAs<LatestApiResponse>()
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

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("main > section:nth-of-type(3) div.flex.gap-4 > div.group")
            .mapNotNull { element ->
                val anchor = element.selectFirst("a.block")
                    ?: return@mapNotNull null

                val href = anchor.attr("href").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val img = element.selectFirst("img")

                val title = element.selectFirst("h3.text-sm.sm\\:text-base")?.text()
                    ?.takeIf { it.isNotBlank() }
                    ?: img?.attr("alt")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(href)

                    this.title = title
                    this.thumbnail_url = img?.attr("src")?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
                }
            }

        return MangasPage(mangas, false)
    }

    // Extracts Next.js page data, trying __NEXT_DATA__ script first, then self.__next_f.push.
    private fun extractNextJsPageData(document: Document): JsonObject? {
        val currentHttpUrl = document.location().toHttpUrlOrNull()
        val isSeriesPage = currentHttpUrl?.pathSegments?.getOrNull(0) == "series"

        try {
            document.selectFirst("script#__NEXT_DATA__")?.data()?.also { scriptData ->
                try {
                    val rootJson = scriptData.parseAs<JsonObject>()
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
                    Log.e("PoseidonScans", "Error parsing __NEXT_DATA__: ${e.message}")
                }
            }

            val mangaSlugForDetails = if (!isSeriesPage) {
                currentHttpUrl?.pathSegments?.let { segments ->
                    val serieIndex = segments.indexOf("serie")
                    if (serieIndex != -1 && serieIndex + 1 < segments.size) {
                        segments[serieIndex + 1]
                    } else {
                        ""
                    }
                } ?: ""
            } else {
                ""
            }
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

                                        '{' -> {
                                            if (braceDepth == 0) {
                                                objectStartIndex = i
                                                break
                                            }
                                            braceDepth--
                                        }
                                    }
                                }
                                if (objectStartIndex != -1) {
                                    val potentialJson = extractJsonObjectString(cleanedDataString, objectStartIndex)
                                    if (potentialJson != null) {
                                        try {
                                            val parsedContainer = potentialJson.parseAs<JsonObject>()
                                            if (parsedContainer.containsKey(marker.substringBefore(':').trim('"')) ||
                                                parsedContainer.containsKey("initialData")
                                            ) {
                                                foundRelevantObject = parsedContainer
                                                objectFoundInThisScript = true
                                                return@nextFPushMatch
                                            }
                                        } catch (e: Exception) {
                                            Log.e("PoseidonScans", "Error parsing nested JSON data: ${e.message}")
                                        }
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
                                    val parsedObject = potentialJson.parseAs<JsonObject>()
                                    val isSane = when (marker) {
                                        "\"initialData\":{" -> parsedObject.containsKey("manga") || parsedObject.containsKey("chapter") || parsedObject.containsKey("images")
                                        "\"manga\":{" -> parsedObject["slug"]?.jsonPrimitive?.content?.contains(mangaSlugForDetails) == true
                                        "\"chapter\":{" -> parsedObject.containsKey("images")
                                        else -> true
                                    }
                                    if (isSane) return parsedObject
                                } catch (e: Exception) {
                                    Log.e("PoseidonScans", "Error parsing validation JSON data: ${e.message}")
                                }
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
        } catch (e: Exception) {
            Log.e("PoseidonScans", "General error in extractNextJsPageData: ${e.message}")
            return null
        }
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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val pageData = extractNextJsPageData(document)
            ?: throw Exception("Could not extract Next.js data. URL: ${document.location()}")

        val mangaDetailsJson = pageData["manga"]?.jsonObject
            ?: pageData["initialData"]?.jsonObject?.get("manga")?.jsonObject
            ?: pageData.takeIf { it.containsKey("slug") && it.containsKey("title") }
            ?: throw Exception("JSON 'manga' structure not found. JSON: ${pageData.toString().take(500)}")

        val mangaDto = try {
            mangaDetailsJson.toString().parseAs<MangaDetailsData>()
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
            } catch (e: Exception) {
                Log.e("PoseidonScans", "Error fetching HTML description: ${e.message}")
            }

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
            setUrlWithoutDomain("/serie/${mangaDto.slug}")
        }
    }

    private fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase(Locale.FRENCH)) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        "en pause", "hiatus" -> SManga.ON_HIATUS
        "annulé", "abandonné" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
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
            mangaDetailsJson.toString().parseAs<MangaDetailsData>()
        } catch (e: Exception) {
            throw Exception("Error parsing chapters: ${e.message}. JSON: $mangaDetailsJson")
        }

        return mangaDto.chapters
            ?.mapNotNull { ch ->
                // If chapter is premium, check if premium period has expired
                if (ch.isPremium == true) {
                    ch.premiumUntil?.let { premiumUntilString ->
                        val premiumUntilDate = parseIsoDate(premiumUntilString)
                        if (premiumUntilDate > 0) {
                            // Exclude if premium period is still active
                            if (System.currentTimeMillis() <= premiumUntilDate) {
                                return@mapNotNull null
                            }
                        } else {
                            // If we can't parse the premium until date, exclude the chapter for safety
                            return@mapNotNull null
                        }
                    } ?: return@mapNotNull null // If premiumUntil is null but isPremium is true, exclude
                }
                val chapterNumberString = ch.number.toString().removeSuffix(".0")
                SChapter.create().apply {
                    val isVolume = ch.isVolume == true || (
                        ch.number == ch.number.toInt().toFloat() &&
                            ch.title?.lowercase()?.contains("volume") == true
                        )

                    val baseName = if (isVolume) {
                        "Volume $chapterNumberString"
                    } else {
                        "Chapitre $chapterNumberString"
                    }

                    name = ch.title?.trim()?.takeIf { it.isNotBlank() }
                        ?.let { title -> "$baseName - $title" }
                        ?: baseName
                    setUrlWithoutDomain("/serie/${mangaDto.slug}/chapter/$chapterNumberString")
                    date_upload = parseIsoDate(ch.createdAt)
                    chapter_number = ch.number
                }
            }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    private fun parseIsoDate(dateString: String?): Long {
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
        return isoDateFormatter.tryParse(cleanedDateString)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageData = extractNextJsPageData(document)
            ?: throw Exception("Could not extract Next.js data for page list.")

        val pageDataDto = pageData.toString().parseAs<PageDataRoot>()
        val chapterPageUrl = document.location()

        val imagesListJson = pageDataDto.images
            ?: pageDataDto.chapter?.images
            ?: pageDataDto.initialData?.images
            ?: pageDataDto.initialData?.chapter?.images
            ?: throw Exception("JSON 'images' structure not found. Data: ${pageData.toString().take(500)}")

        val imagesDataList = try {
            imagesListJson.toString().parseAs<List<PageImageUrlData>>()
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.grid a.block.group").mapNotNull { element ->
            try {
                val url = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = element.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val thumbnailUrlPath = element.selectFirst("img[alt]")
                    ?.attr("srcset")
                    ?.substringBefore(" ")
                    ?.let {
                        URLDecoder.decode(it, "UTF-8")
                            .substringAfter("url=")
                            .substringBefore("&")
                    }

                SManga.create().apply {
                    this.setUrlWithoutDomain(url)
                    this.title = title
                    this.thumbnail_url = thumbnailUrlPath?.takeIf { it.isNotBlank() }?.toApiCoverUrl()
                }
            } catch (e: Exception) {
                Log.e("PoseidonScans", "Error parsing manga from HTML element", e)
                null
            }
        }

        val hasNextPage = document.select("nav[aria-label=Pagination] a:contains(Suivant)").isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")
    override fun getFilterList(): FilterList = FilterList()
}
