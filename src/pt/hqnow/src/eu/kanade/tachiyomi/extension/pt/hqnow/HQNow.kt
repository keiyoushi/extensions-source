package eu.kanade.tachiyomi.extension.pt.hqnow

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

class HQNow : HttpSource() {

    override val name = "HQ Now!"

    override val baseUrl = "https://www.hq-now.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    private fun genericComicBookFromObject(comicBook: HqNowComicBookDto): SManga =
        SManga.create().apply {
            title = comicBook.name
            url = "/hq/${comicBook.id}/${comicBook.name.toSlug()}"
            thumbnail_url = comicBook.cover
        }

    override fun popularMangaRequest(page: Int): Request {
        val query = buildQuery {
            """
                query getHqsByFilters(
                    %orderByViews: Boolean,
                    %limit: Int,
                    %publisherId: Int,
                    %loadCovers: Boolean
                ) {
                    getHqsByFilters(
                        orderByViews: %orderByViews,
                        limit: %limit,
                        publisherId: %publisherId,
                        loadCovers: %loadCovers
                    ) {
                        id
                        name
                        editoraId
                        status
                        publisherName
                        hqCover
                        synopsis
                        updatedAt
                    }
                }
            """.trimIndent()
        }

        return queryRequest(
            query = query,
            operationName = "getHqsByFilters",
            variables = buildJsonObject {
                put("orderByViews", true)
                put("loadCovers", true)
                put("limit", 300)
            },
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getHqsByFilters"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val query = buildQuery {
            """
                query getRecentlyUpdatedHqs {
                    getRecentlyUpdatedHqs {
                        name
                        hqCover
                        synopsis
                        id
                        updatedAt
                        updatedChapters
                    }
                }
            """.trimIndent()
        }

        return queryRequest(query = query, operationName = "getRecentlyUpdatedHqs")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getRecentlyUpdatedHqs"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryStr = buildQuery {
            """
                query getHqsByName(%name: String!) {
                    getHqsByName(name: %name) {
                        id
                        name
                        editoraId
                        status
                        publisherName
                        impressionsCount
                    }
                }
            """.trimIndent()
        }

        return queryRequest(
            query = queryStr,
            operationName = "getHqsByName",
            variables = buildJsonObject {
                put("name", query)
            },
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getHqsByName"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val comicBookId = manga.url.substringAfter("/hq/").substringBefore("/")

        val query = buildQuery {
            """
                query getHqsById(%id: Int!) {
                    getHqsById(id: %id) {
                        id
                        name
                        synopsis
                        editoraId
                        status
                        publisherName
                        hqCover
                        impressionsCount
                        capitulos {
                            name
                            id
                            number
                        }
                    }
                }
            """.trimIndent()
        }

        return queryRequest(
            query = query,
            operationName = "getHqsById",
            variables = buildJsonObject {
                put("id", comicBookId.toInt())
            },
        )
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val result = json.parseToJsonElement(response.body.string()).jsonObject
        val comicBook = result["data"]!!.jsonObject["getHqsById"]!!.jsonArray[0].jsonObject
            .let { json.decodeFromJsonElement<HqNowComicBookDto>(it) }

        title = comicBook.name
        thumbnail_url = comicBook.cover
        description = comicBook.synopsis.orEmpty()
        author = comicBook.publisherName.orEmpty()
        status = comicBook.status.orEmpty().toStatus()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.parseToJsonElement(response.body.string()).jsonObject
        val comicBook = result["data"]!!.jsonObject["getHqsById"]!!.jsonArray[0].jsonObject
            .let { json.decodeFromJsonElement<HqNowComicBookDto>(it) }

        return comicBook.chapters
            .map { chapter -> chapterFromObject(chapter, comicBook) }
            .reversed()
    }

    private fun chapterFromObject(chapter: HqNowChapterDto, comicBook: HqNowComicBookDto): SChapter =
        SChapter.create().apply {
            name = "#" + chapter.number +
                (if (chapter.name.isNotEmpty()) " - " + chapter.name else "")
            url = "/hq-reader/${comicBook.id}/${comicBook.name.toSlug()}" +
                "/chapter/${chapter.id}/page/1"
        }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/chapter/").substringBefore("/")

        val query = buildQuery {
            """
                query getChapterById(%chapterId: Int!) {
                    getChapterById(chapterId: %chapterId) {
                        name
                        number
                        oneshot
                        pictures {
                            pictureUrl
                        }
                    }
                }
            """.trimIndent()
        }

        return queryRequest(
            query = query,
            operationName = "getChapterById",
            variables = buildJsonObject {
                put("chapterId", chapterId.toInt())
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.parseToJsonElement(response.body.string()).jsonObject

        val chapterDto = result["data"]!!.jsonObject["getChapterById"]!!
            .let { json.decodeFromJsonElement<HqNowChapterDto>(it) }

        return chapterDto.pictures.mapIndexed { i, page ->
            Page(i, baseUrl, page.pictureUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun buildQuery(queryAction: () -> String) = queryAction().replace("%", "$")

    private fun queryRequest(query: String, operationName: String, variables: JsonObject? = null): Request {
        val payload = buildJsonObject {
            put("operationName", operationName)
            put("query", query)
            variables?.let { put("variables", it) }
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    private fun String.toSlug(): String {
        return Normalizer
            .normalize(this, Normalizer.Form.NFD)
            .replace("[^\\p{ASCII}]".toRegex(), "")
            .replace("[^a-zA-Z0-9\\s]+".toRegex(), "").trim()
            .replace("\\s+".toRegex(), "-")
            .lowercase(Locale("pt", "BR"))
    }

    private fun String.toStatus(): Int = when (this) {
        "Concluído" -> SManga.COMPLETED
        "Em Andamento" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val STATIC_URL = "https://static.hq-now.com/"
        private const val GRAPHQL_URL = "https://admin.hq-now.com/graphql"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
