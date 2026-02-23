package eu.kanade.tachiyomi.extension.fr.astralmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class AstralManga : HttpSource() {

    override val name = "AstralManga"

    override val baseUrl = "https://astral-manga.fr"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(8, 1)
        .build()

    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================== Popular ==========================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", "12")
            addQueryParameter("sortBy", "note")
            addQueryParameter("sortOrder", "desc")
            addQueryParameter("includeMode", "and")
            addQueryParameter("excludeMode", "or")
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaApiResponse(response)

    // ========================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", "12")
            addQueryParameter("sortBy", "publishDate")
            addQueryParameter("sortOrder", "desc")
            addQueryParameter("includeMode", "and")
            addQueryParameter("excludeMode", "or")
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaApiResponse(response)

    // ========================== Search ==========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", "12")

            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            var sortBy = "title"
            var sortOrder = "asc"

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        sortBy = filter.toUriPart()
                        sortOrder = if (sortBy == "title") "asc" else "desc"
                    }

                    is StatusFilter -> {
                        val statusValue = filter.toUriPart()
                        if (statusValue.isNotBlank()) addQueryParameter("status", statusValue)
                    }

                    is TypeFilter -> {
                        val typeValue = filter.toUriPart()
                        if (typeValue.isNotBlank()) addQueryParameter("type", typeValue)
                    }

                    is GenreFilter -> {
                        filter.state.filter { it.state }.forEach { addQueryParameter("tags", it.name) }
                    }

                    else -> {}
                }
            }

            addQueryParameter("sortBy", sortBy)
            addQueryParameter("sortOrder", sortOrder)
            addQueryParameter("includeMode", "and")
            addQueryParameter("excludeMode", "or")
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaApiResponse(response)

    override fun getFilterList() = getFilters()

    // ========================== Details ==========================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers.newBuilder().add("RSC", "1").build())

    override fun mangaDetailsParse(response: Response): SManga {
        val rscData = response.body.string()
        return parseMangaDetailsFromRsc(rscData, response.request.url)
    }

    // ========================== Chapters ==========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val rscData = response.body.string()
        val url = response.request.url

        val chapters = parseChaptersFromRsc(rscData, url)
        if (chapters.isNotEmpty()) return chapters

        // RSC data can be partial on first load; retry with cache-busting
        val retryUrl = url.newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val retryRequest = response.request.newBuilder()
            .url(retryUrl)
            .header("Cache-Control", "no-cache")
            .build()
        val retryResponse = client.newCall(retryRequest).execute()
        return parseChaptersFromRsc(retryResponse.body.string(), url)
    }

    // ========================== Pages ==========================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return parsePageList(document)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================== Parsing ==========================

    /**
     * Parse manga list from API JSON response.
     */
    private fun parseMangaApiResponse(response: Response): MangasPage {
        val dto = response.parseAs<MangaResponseDto>()

        val mangas = dto.mangas.map { mangaDto ->
            mangaDto.toSManga { s3Key -> presignS3Key(s3Key) }
        }

        val hasNextPage = (dto.mangas.size >= 12) && (mangas.size < dto.total)
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * Parse manga details from RSC data on the detail page.
     * The RSC data contains all manga, so we scope extraction to the
     * specific manga's JSON block using its UUID from the URL.
     */
    private fun parseMangaDetailsFromRsc(rscData: String, url: HttpUrl): SManga {
        val normalized = normalizeRscData(rscData)

        val mangaUuid = url.pathSegments[1]
        val mangaContext = findMangaContext(normalized, mangaUuid)
        return SManga.create().apply {
            setUrlWithoutDomain(url.toString())

            title = mangaContext?.let { FIELD_TITLE_REGEX.find(it)?.groupValues?.get(1) }
                ?: throw Exception("Title not found")

            val rscDescription = mangaContext?.let { FIELD_DESCRIPTION_REGEX.find(it)?.groupValues?.get(1) }
            description = resolveRscReference(normalized, rscDescription)

            val s3Key = mangaContext?.let { RSC_S3_LINK_REGEX.find(it)?.groupValues?.get(1) }
            thumbnail_url = s3Key?.let { presignS3Key(it) }

            val rscStatus = mangaContext?.let { FIELD_STATUS_REGEX.find(it)?.groupValues?.get(1) }
            status = when (rscStatus?.uppercase()) {
                "ON_GOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "CANCELLED" -> SManga.CANCELLED
                "HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val mangaType = mangaContext?.let { FIELD_TYPE_REGEX.find(it)?.groupValues?.get(1) }

            val rscGenres = mangaContext?.let { extractGenresFromRsc(it) } ?: emptyList()
            genre = (listOfNotNull(mangaType) + rscGenres).joinToString()

            // --- Metadata extraction (Authors, Artists, Teams, Year) ---
            val rscAuthors = mangaContext?.let { extractListFromRsc(it, FIELD_AUTHORS_REGEX) } ?: emptyList()
            val rscArtists = mangaContext?.let { extractListFromRsc(it, FIELD_ARTISTS_REGEX) } ?: emptyList()
            val rscTeams = mangaContext?.let { extractListFromRsc(it, FIELD_TEAMS_REGEX) } ?: emptyList()
            val rscYear = mangaContext?.let {
                val rawDate = FIELD_PUBLISH_DATE_REGEX.find(it)?.groupValues?.get(1)
                resolveRscReference(normalized, rawDate)?.substringBefore("-")
            }

            author = rscAuthors.joinToString().ifBlank { null }
            artist = rscArtists.joinToString().ifBlank { author }

            val extraInfo = StringBuilder()
            if (rscTeams.isNotEmpty()) {
                extraInfo.append("\n\nTeams: ").append(rscTeams.joinToString())
            }
            if (!rscYear.isNullOrBlank()) {
                extraInfo.append("\nAnnée: ").append(rscYear)
            }
            if (extraInfo.isNotEmpty()) {
                description = (description ?: "") + extraInfo.toString()
            }
        }
    }

    /**
     * Find the JSON context block for a specific manga by its UUID in the RSC data.
     */
    private fun findMangaContext(data: String, uuid: String): String? {
        if (uuid.isBlank()) return null

        val urlIdPattern = "\"urlId\":\"$uuid\""
        val uuidIdx = data.indexOf(urlIdPattern)
        if (uuidIdx < 0) {
            return null
        }

        val searchStart = maxOf(0, uuidIdx - 5000)
        val beforeUuid = data.substring(searchStart, uuidIdx)

        val lastTitleIdx = beforeUuid.lastIndexOf("\"title\":\"")
        val lastIdIdx = beforeUuid.lastIndexOf("\"id\":\"")
        val anchorIdx = maxOf(lastTitleIdx, lastIdIdx)

        val start = if (anchorIdx >= 0) {
            val braceIdx = beforeUuid.lastIndexOf("{", anchorIdx)
            searchStart + if (braceIdx >= 0) braceIdx else anchorIdx
        } else {
            maxOf(0, uuidIdx - 1000)
        }

        val searchEnd = minOf(data.length, uuidIdx + 5000)
        val afterUrlId = data.substring(uuidIdx, searchEnd)
        val linkIdx = afterUrlId.indexOf("\"link\":\"s3:")
        val end = if (linkIdx >= 0) {
            val endOfLink = afterUrlId.indexOf("\"", linkIdx + 12)
            uuidIdx + if (endOfLink >= 0) endOfLink + 100 else linkIdx + 300
        } else {
            minOf(data.length, uuidIdx + 3000)
        }

        return data.substring(start, minOf(data.length, end))
    }

    /**
     * Parse chapters from RSC data. The RSC "chapters" array contains ALL chapters
     */
    private fun parseChaptersFromRsc(rscData: String, url: HttpUrl): List<SChapter> {
        if (rscData.isEmpty()) return emptyList()

        val normalized = normalizeRscData(rscData)

        val chaptersIdx = normalized.indexOf("\"chapters\":[{")
        if (chaptersIdx < 0) return emptyList()

        val chaptersEnd = normalized.indexOf("],\"", chaptersIdx)
        val contentToSearch = if (chaptersEnd > chaptersIdx) {
            normalized.substring(chaptersIdx, chaptersEnd)
        } else {
            normalized.substring(chaptersIdx)
        }

        val urlId = url.pathSegments[1]
        val mangaContext = findMangaContext(normalized, urlId)
        val internalMangaId = mangaContext?.let { FIELD_ID_REGEX.find(it)?.groupValues?.get(1) }

        val chapterMatches = CHAPTER_REGEX.findAll(contentToSearch).toList()

        val chapters = mutableListOf<SChapter>()
        val seen = mutableSetOf<String>()

        for (match in chapterMatches) {
            val chapterId = match.groupValues[1]
            val orderId = match.groupValues[2].toIntOrNull() ?: continue
            val publishDate = match.groupValues[3]
            val chapterMangaId = match.groupValues[4]

            if (internalMangaId != null && chapterMangaId != internalMangaId) continue

            if (chapterId in seen) continue
            seen.add(chapterId)

            chapters.add(
                SChapter.create().apply {
                    this.url = "/manga/$urlId/chapter/$chapterId"
                    name = "Chapitre $orderId"
                    chapter_number = orderId.toFloat()
                    date_upload = DATE_FORMAT.tryParse(publishDate)
                    scanlator = "Astral Manga"
                },
            )
        }

        return chapters
    }

    /**
     * Parse page list from chapter page.
     */
    private fun parsePageList(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val rawRsc = extractRscData(document)
        if (rawRsc.isNotEmpty()) {
            val normalized = normalizeRscData(rawRsc)

            val imagesIdx = normalized.indexOf("\"images\":[{")
            if (imagesIdx >= 0) {
                val imagesEnd = normalized.indexOf("],\"", imagesIdx)
                val imagesContent = if (imagesEnd > imagesIdx) {
                    normalized.substring(imagesIdx, imagesEnd)
                } else {
                    normalized.substring(imagesIdx)
                }

                val matches = IMG_REGEX.findAll(imagesContent)

                matches.forEach { match ->
                    val link = match.groupValues[1]
                    val order = match.groupValues[2].toIntOrNull() ?: pages.size

                    val imageUrl = if (link.startsWith("s3:")) {
                        presignS3Key(link.substringAfter("s3:")) ?: ""
                    } else {
                        link
                    }

                    if (imageUrl.isNotEmpty()) {
                        pages.add(Page(order, imageUrl = imageUrl))
                    }
                }
            }
        }

        if (pages.isNotEmpty()) {
            return pages.sortedBy { it.index }
        }

        return document.select("img[alt~=^Page \\d+]").mapIndexed { index, img ->
            val imageUrl = img.absUrl("src").ifEmpty {
                val src = img.attr("src")
                if (src.startsWith("http")) src else "$baseUrl$src"
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    // ========================== RSC Utility ==========================

    /**
     * Extract RSC flight data from self.__next_f.push scripts.
     */
    private fun extractRscData(document: Document): String {
        val builder = StringBuilder()
        document.select("script").forEach { script ->
            val text = script.data()
            if (text.contains("__next_f.push")) {
                builder.append(text)
            }
        }
        return builder.toString()
    }

    /**
     * Normalize RSC flight data by removing heavy escaping.
     */
    private fun normalizeRscData(raw: String): String = UNICODE_REGEX.replace(raw) {
        it.groupValues[1].toInt(16).toChar().toString()
    }
        .replace("\\\\\\\"", "\"")
        .replace("\\\\\"", "\"")
        .replace("\\\"", "\"")
        .replace("\\\\/", "/")
        .replace("\\n", "\n")

    /**
     * Presign an S3 key using the /api/s3/presign-get endpoint.
     */
    private fun presignS3Key(s3Key: String): String? = try {
        val url = "$baseUrl/api/s3/presign-get".toHttpUrl().newBuilder()
            .addQueryParameter("key", s3Key)
            .build()
        val request = GET(url, headers)
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            response.parseAs<PresignResponseDto>().url
        } else {
            response.close()
            null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Extract genre names specifically from the "genres":[{"name":"..."},...] array
     */
    private fun extractGenresFromRsc(context: String): List<String> = extractListFromRsc(context, FIELD_GENRES_REGEX)

    private fun extractListFromRsc(context: String, regex: Regex): List<String> {
        val match = regex.find(context) ?: return emptyList()
        return NAME_REGEX.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private fun resolveRscReference(data: String, reference: String?): String? {
        if (reference == null || !reference.startsWith("$") || reference.length < 2) return reference
        if (reference.startsWith("\$D")) return reference.removePrefix("\$D")
        val refId = reference.removePrefix("$")
        val pattern = Regex("(?s)(?:^|\\\\n)$refId:T[a-f0-9]+,(.*?)(?=\\\\n[0-9]+:|\$|\")")
        val match = pattern.find(data) ?: return null
        return match.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"")
    }

    // ========================== General Utility ==========================

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        private val RSC_S3_LINK_REGEX = Regex(""""link"\s*:\s*"s3:([^"]+)"""")
        private val CHAPTER_REGEX = Regex(""""id"\s*:\s*"([0-9a-f-]{36})".*?"orderId"\s*:\s*(\d+).*?"publishDate"\s*:\s*"[^"]*?(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})[^"]*".*?"mangaId"\s*:\s*"([0-9a-f-]{36})"""")
        private val IMG_REGEX = Regex(""""link"\s*:\s*"([^"]+)".*?"orderId"\s*:\s*(\d+)""")
        private val UNICODE_REGEX = Regex("""\\u([0-9a-fA-F]{4})""")
        private val FIELD_GENRES_REGEX = Regex("\"genres\":\\[(.*?)\\]")
        private val FIELD_AUTHORS_REGEX = Regex("\"authors\":\\[(.*?)\\]")
        private val FIELD_ARTISTS_REGEX = Regex("\"artists\":\\[(.*?)\\]")
        private val FIELD_TEAMS_REGEX = Regex("\"teams\":\\[(.*?)\\]")
        private val FIELD_PUBLISH_DATE_REGEX = Regex("\"publishDate\":\"(.*?)\"")

        private val NAME_REGEX = Regex("\"name\":\"(.*?)\"")
        private val FIELD_TITLE_REGEX = Regex(""""title"\s*:\s*"([^"]+)"""")
        private val FIELD_DESCRIPTION_REGEX = Regex(""""description"\s*:\s*"([^"]+)"""")
        private val FIELD_STATUS_REGEX = Regex(""""status"\s*:\s*"([^"]+)"""")
        private val FIELD_TYPE_REGEX = Regex(""""type"\s*:\s*"([^"]+)"""")
        private val FIELD_ID_REGEX = Regex(""""id"\s*:\s*"([^"]+)"""")
    }
}
