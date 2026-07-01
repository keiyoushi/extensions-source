package eu.kanade.tachiyomi.extension.ar.brownmanga

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.rateLimit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class BrownManga : HttpSource() {

    override val baseUrl = "https://brownmanga.site"

    override val name = "Brown Manga"

    override val lang = "ar"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    private fun apiQuery(body: String): Request {
        val apiHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        return POST("$baseUrl/api/query", apiHeaders, body.toRequestBody(jsonMediaType))
    }

    private fun queryBody(
        table: String,
        select: String = "*",
        filters: List<Map<String, String>>? = null,
        order: Map<String, Any>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"table\":\"$table\"")
        sb.append(",\"select\":\"$select\"")
        filters?.let {
            sb.append(",\"filters\":[")
            it.forEachIndexed { idx, f ->
                if (idx > 0) sb.append(",")
                sb.append("{\"col\":\"${f["col"]}\",\"op\":\"${f["op"]}\",\"val\":\"${f["val"]}\"}")
            }
            sb.append("]")
        }
        order?.let {
            sb.append(",\"order\":{\"column\":\"${it["column"]}\",\"ascending\":${it["ascending"]}}")
        }
        limit?.let { sb.append(",\"limit\":$it") }
        offset?.let { sb.append(",\"offset\":$it") }
        sb.append("}")
        return sb.toString()
    }

    private fun parseResponse(response: Response): String {
        val raw = response.body!!.string()
        if (raw.isBlank()) return "[]"
        if (raw.startsWith("{") || raw.startsWith("[")) return raw
        val lines = raw.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) return trimmed
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0 && colonIdx < 10) {
                val afterColon = trimmed.substring(colonIdx + 1).trim()
                if (afterColon.startsWith("[") || afterColon.startsWith("{")) return afterColon
            }
        }
        return raw
    }

    @Serializable
    data class ManhwaItem(
        val id: String? = null,
        val title: String? = null,
        val title_ar: String? = null,
        val slug: String? = null,
        val cover_url: String? = null,
        val status: String? = null,
        val type: String? = null,
        val description: String? = null,
        val description_ar: String? = null,
        val average_rating: Double? = null,
        val views: Int? = null,
    )

    @Serializable
    data class ChapterItem(
        val id: String? = null,
        val chapter_number: Double? = null,
        val title: String? = null,
        val created_at: String? = null,
        val is_locked: Boolean? = null,
        val published_at: String? = null,
        val lock_duration_days: Int? = null,
        val manhwa_id: String? = null,
    )

    @Serializable
    data class ChapterPage(
        val id: String? = null,
        val page_number: Int? = null,
        val image_url: String? = null,
        val chapter_id: String? = null,
    )

    override fun popularMangaRequest(page: Int): Request {
        val body = queryBody(
            table = "manhwa",
            select = "id, title, title_ar, slug, cover_url, status, type, description, description_ar, average_rating, views",
            order = mapOf("column" to "views", "ascending" to false),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val raw = parseResponse(response)
        val result = json.decodeFromString<List<ManhwaItem>>(raw)
        val mangas = result.map { item ->
            SManga.create().apply {
                url = "/series/${item.slug}"
                title = item.title_ar?.ifEmpty { item.title } ?: item.title ?: ""
                thumbnail_url = item.cover_url
                description = item.description_ar?.ifEmpty { item.description } ?: item.description
                status = when (item.status) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = queryBody(
            table = "chapters",
            select = "id, chapter_number, title, created_at, is_locked, published_at, lock_duration_days, manhwa_id, manhwa:manhwa_id(id, title, title_ar, slug, cover_url, status, type)",
            order = mapOf("column" to "created_at", "ascending" to false),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        @Serializable
        data class ManhwaRef(val id: String? = null, val title: String? = null, val title_ar: String? = null, val slug: String? = null, val cover_url: String? = null, val status: String? = null, val type: String? = null)

        @Serializable
        data class ChWithManhwa(val id: String? = null, val chapter_number: Double? = null, val title: String? = null, val created_at: String? = null, val manhwa_id: String? = null, val manhwa: ManhwaRef? = null)

        val raw = parseResponse(response)
        val result = json.decodeFromString<List<ChWithManhwa>>(raw)
        val mangas = result
            .filter { it.manhwa != null }
            .map { item ->
                SManga.create().apply {
                    url = "/series/${item.manhwa!!.slug}"
                    title = item.manhwa.title_ar?.ifEmpty { item.manhwa.title } ?: item.manhwa.title ?: ""
                    thumbnail_url = item.manhwa.cover_url
                    status = when (item.manhwa.status) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }.distinctBy { it.url }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = queryBody(
            table = "manhwa",
            select = "id, title, title_ar, slug, cover_url, status, type, description, description_ar, average_rating, views",
            filters = listOf(
                mapOf("col" to "title_ar", "op" to "ilike", "val" to "%$query%"),
            ),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/series/")
        val body = queryBody(
            table = "manhwa",
            select = "*",
            filters = listOf(mapOf("col" to "slug", "op" to "eq", "val" to slug)),
            limit = 1,
        )
        return apiQuery(body)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val raw = parseResponse(response)
        val result = json.decodeFromString<List<ManhwaItem>>(raw)
        val item = result.firstOrNull() ?: return SManga.create()
        return SManga.create().apply {
            url = "/series/${item.slug}"
            title = item.title_ar?.ifEmpty { item.title } ?: item.title ?: ""
            thumbnail_url = item.cover_url
            description = item.description_ar?.ifEmpty { item.description } ?: item.description
            status = when (item.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/series/")
        val body = queryBody(
            table = "manhwa",
            select = "id, slug",
            filters = listOf(mapOf("col" to "slug", "op" to "eq", "val" to slug)),
            limit = 1,
        )
        return apiQuery(body)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val raw = parseResponse(response)
        val mangaResult = json.decodeFromString<List<ManhwaItem>>(raw)
        val mangaId = mangaResult.firstOrNull()?.id ?: return emptyList()
        val slug = mangaResult.firstOrNull()?.slug ?: ""

        val body = queryBody(
            table = "chapters",
            select = "id, chapter_number, title, is_locked, created_at, manhwa_id",
            filters = listOf(
                mapOf("col" to "manhwa_id", "op" to "eq", "val" to mangaId),
                mapOf("col" to "is_locked", "op" to "eq", "val" to "false"),
            ),
            order = mapOf("column" to "chapter_number", "ascending" to false),
            limit = 500,
        )
        val request = apiQuery(body)
        val clientResponse = client.newCall(request).execute()
        val chaptersRaw = parseResponse(clientResponse)
        val chapters = json.decodeFromString<List<ChapterItem>>(chaptersRaw)

        return chapters.map { ch ->
            SChapter.create().apply {
                url = "/series/$slug/chapter/${ch.chapter_number?.toInt() ?: 0}"
                name = ch.title?.ifEmpty { null }
                    ?: "الفصل ${ch.chapter_number?.toInt() ?: 0}"
                chapter_number = ch.chapter_number?.toFloat() ?: 0f
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringAfter("/series/").substringBefore("/chapter")
        val chapterNum = chapter.url.substringAfterLast("/").toIntOrNull() ?: 0

        val body = queryBody(
            table = "manhwa",
            select = "id",
            filters = listOf(mapOf("col" to "slug", "op" to "eq", "val" to slug)),
            limit = 1,
        )
        val apiHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("X-Chapter-Url", chapter.url)
            .build()
        return POST("$baseUrl/api/query", apiHeaders, body.toRequestBody(jsonMediaType))
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.header("X-Chapter-Url") ?: return emptyList()
        val slug = chapterUrl.substringAfter("/series/").substringBefore("/chapter")
        val chapterNum = chapterUrl.substringAfterLast("/").toIntOrNull() ?: 0

        val raw = parseResponse(response)
        val mangaResult = json.decodeFromString<List<ManhwaItem>>(raw)
        val mangaId = mangaResult.firstOrNull()?.id ?: return emptyList()

        val body = queryBody(
            table = "chapters",
            select = "id",
            filters = listOf(
                mapOf("col" to "manhwa_id", "op" to "eq", "val" to mangaId),
                mapOf("col" to "chapter_number", "op" to "eq", "val" to chapterNum.toString()),
            ),
            limit = 1,
        )
        val chResponse = client.newCall(apiQuery(body)).execute()
        val chaptersRaw = parseResponse(chResponse)
        val chapters = json.decodeFromString<List<ChapterItem>>(chaptersRaw)
        val chapterId = chapters.firstOrNull()?.id ?: return emptyList()

        val body2 = queryBody(
            table = "chapter_pages",
            select = "*",
            filters = listOf(mapOf("col" to "chapter_id", "op" to "eq", "val" to chapterId)),
            order = mapOf("column" to "page_number", "ascending" to true),
        )
        val pagesResponse = client.newCall(apiQuery(body2)).execute()
        val pagesRaw = parseResponse(pagesResponse)
        val pages = json.decodeFromString<List<ChapterPage>>(pagesRaw)

        return pages.map { page ->
            Page(page.page_number ?: 0, imageUrl = page.image_url ?: "")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
