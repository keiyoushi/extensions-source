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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
        val mangaUuid = response.request.url.pathSegments[1]
        val manga = response.extractNextJs<MangaDto> {
            it is JsonObject && it["urlId"]?.jsonPrimitive?.contentOrNull == mangaUuid
        } ?: throw Exception("Title not found")
        return manga.toSManga { presignS3Key(it) }
    }

    // ========================== Chapters ==========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        val mangaUuid = url.pathSegments[1]
        val rscBody = response.body.string()

        val chapters = parseChapters(rscBody, mangaUuid)
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
        return parseChapters(retryResponse.body.string(), mangaUuid)
    }

    // ========================== Pages ==========================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.extractNextJs<List<RscImageDto>>()
        if (images != null) {
            return images.sortedBy { it.orderId }.mapIndexed { index, img ->
                val imageUrl = if (img.link.startsWith("s3:")) {
                    presignS3Key(img.link.substringAfter("s3:")) ?: ""
                } else {
                    img.link
                }
                Page(index, imageUrl = imageUrl)
            }
        }

        return document.select("img[alt~=^Page \\d+]").mapIndexed { index, img ->
            val imageUrl = img.absUrl("src").ifEmpty {
                val src = img.attr("src")
                if (src.startsWith("http")) src else "$baseUrl$src"
            }
            Page(index, imageUrl = imageUrl)
        }
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

    private fun parseChapters(rscBody: String, mangaUuid: String): List<SChapter> {
        val manga = rscBody.extractNextJsRsc<MangaDto> {
            it is JsonObject && it["urlId"]?.jsonPrimitive?.contentOrNull == mangaUuid
        } ?: return emptyList()

        val chapters = rscBody.extractNextJsRsc<List<RscChapterDto>>()
            ?: return emptyList()

        val seen = mutableSetOf<String>()
        return chapters
            .filter { it.mangaId == manga.id }
            .filter { seen.add(it.id) }
            .map { ch ->
                SChapter.create().apply {
                    this.url = "/manga/$mangaUuid/chapter/${ch.id}"
                    name = "Chapitre ${ch.orderIdString}"
                    chapter_number = ch.orderId
                    date_upload = DATE_FORMAT.tryParse(ch.publishDate?.take(19))
                    scanlator = "Astral Manga"
                }
            }
    }

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

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }
}
