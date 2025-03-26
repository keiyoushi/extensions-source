package eu.kanade.tachiyomi.extension.pt.readmangas

import android.annotation.SuppressLint
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

class ReadMangas() : HttpSource() {

    override val name = "Read Mangas"

    override val baseUrl = "https://mangalivre.one"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .build()

    override val versionId = 2

    // =========================== Popular ================================

    private var popularNextCursorPage = ""

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/projects"
        if (page == 1) {
            return GET(url, headers)
        }

        val payload = buildJsonArray {
            add(
                buildJsonObject {
                    put("cursor", popularNextCursorPage)
                },
            )
        }

        // https://mangalivre.one/_next/static/chunks/app/%5Blocale%5D/(website)/title/%5Boid%5D/page-b71e9a5f301ac90e.js

        val newHeaders = headers.newBuilder()
//            .set("Next-Router-State-Tree", """["",{"children":[["locale","pt","d"],{"children":["(website)",{"children":["projects",{"children":["__PAGE__",{},"/projects","refresh"]}]}]}]},null,null,true]""")
            .set("Next-Action", "7f00452c28ff68edd78a5b28fac17278fc95b2f9b6")
            .set("Referer", url)
            .build()

        return POST(url, newHeaders, payload.toRequestBody())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val (mangaPage, nextCursor) = response.mangasPageParse<PopularResultDto>()
        popularNextCursorPage = nextCursor
        return mangaPage
    }

    // =========================== Latest ===================================

    private var latestNextCursorPage = ""

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/updates"
        if (page == 1) {
            return GET(url, headers)
        }
        val payload = buildJsonArray {
            add(
                buildJsonObject {
                    put("limit", 20)
                    put("cursor", latestNextCursorPage)
                },
            )
        }

        val newHeaders = headers.newBuilder()
//            .set("Next-Router-State-Tree", """["",{"children":[["locale","pt","d"],{"children":["(website)",{"children":["projects",{"children":["__PAGE__",{},"/projects","refresh"]}]}]}]},null,null,true]""")
            .set("Next-Action", "7f00452c28ff68edd78a5b28fac17278fc95b2f9b6")
            .set("Referer", url)
            .build()

        return POST(url, newHeaders, payload.toRequestBody())
    }

    private fun JsonElement.toRequestBody() = toString().toRequestBody(APPLICATION_JSON)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val (mangaPage, nextCursor) = response.mangasPageParse<LatestResultDto>()
        latestNextCursorPage = nextCursor
        return mangaPage
    }

    // =========================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/deprecated/discover.search?batch=1"
        val payload = buildJsonObject {
            put(
                "0",
                buildJsonObject {
                    put(
                        "json",
                        buildJsonObject {
                            put("name", query)
                        },
                    )
                },
            )
        }.toString().toRequestBody("application/json".toMediaType())

        return POST(url, headers, payload)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // =========================== Details =================================

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.parseScriptToJson()!!
        return with(json.parseAs<MangaDetailsDto>()) {
            SManga.create().apply {
                title = details.title
                thumbnail_url = details.thumbnailUrl
                description = details.description
                genre = details.genres.joinToString { it.name }
                status = details.status.toStatus()
                url = "/title/$slug#${details.id}"
            }
        }
    }

    // =========================== Chapter =================================

    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    private fun chapterListRequest(manga: SManga, page: Int, chapterToken: String): Request {
        val id = manga.url.substringAfterLast("#")
        val input = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", id)
                    put("page", page)
                    put("limit", 50)
                    put("sort", "desc")
                    put("search", "\$undefined")
                },
            )
        }

        val newHeaders = headers.newBuilder()
            .set("Next-Action", chapterToken)
            .build()

        val payload = input.toString().toRequestBody(APPLICATION_JSON)

        return POST("$baseUrl/title/$id", newHeaders, payload)
    }

    private fun findChapterToken(manga: SManga): String {
        var response = client.newCall(super.chapterListRequest(manga)).execute()
        val document = response.asJsoup()

        val scriptUlr = document.selectFirst("""script[src*="%5Boid%5D/page"]""")
            ?.absUrl("src")
            ?: throw Exception("Token não encontrado")

        response = client.newCall(GET(scriptUlr, headers)).execute()

        val nextAction: String =
            CHAPTER_TOKEN_REGEX.find(response.body.string())?.groups?.get(1)?.value
                ?: throw Exception("Não foi possivel obter token")
        return nextAction
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapterToken = findChapterToken(manga)
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val response = tryFetchChapterPage(manga, page++, chapterToken)
            val json = CHAPTERS_REGEX.find(response.body.string())?.groups?.get(0)?.value
            val dto = json!!.parseAs<ChapterListDto>()
            chapters += chapterListParse(dto.chapters)
        } while (dto.hasNext())

        return Observable.just(chapters)
    }

    private val attempts = 2

    private fun tryFetchChapterPage(manga: SManga, page: Int, chapterToken: String): Response {
        repeat(attempts) { index ->
            try { return client.newCall(this.chapterListRequest(manga, page, chapterToken)).execute() } catch (e: Exception) { /* do nothing */ }
        }
        throw Exception("Não foi possivel obter os capitulos da página: $page")
    }

    private fun chapterListParse(chapters: List<ChapterDto>): List<SChapter> {
        return chapters.map {
            SChapter.create().apply {
                name = it.title
                chapter_number = it.number.toFloat()
                date_upload = dateFormat.tryParse(it.createdAt)
                url = "/readme/${it.id}"
            }
        }
    }

    // =========================== Pages ===================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scripts = document.select("script").joinToString("\n") { it.data() }
        val pages = IMAGE_URL_REGEX.findAll(scripts).mapIndexed { index, match ->
            Page(index, imageUrl = match.groups[1]!!.value)
        }.toList()

        return pages
    }

    override fun imageUrlParse(response: Response) = ""

    // =========================== Utilities ===============================

    private inline fun <reified T : Dto> Response.mangasPageParse(): Pair<MangasPage, String> {
        val json = if (request.method.equals("get", ignoreCase = true)) {
            parseScriptToJson()
        } else {
            JSON_REGEX.find(body.string())?.groups?.get(0)?.value
        }

        if (json.isNullOrBlank()) {
            return MangasPage(emptyList(), false) to ""
        }

        val dto = json.parseAs<T>()

        val mangas = dto.mangas.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.thumbnailUrl
                author = it.author
                status = it.status.toStatus()
                url = "/title/${it.slug}#${it.id}"
            }
        }
        return MangasPage(mangas, dto.hasNextPage || dto.nextCursor.isNotBlank()) to dto.nextCursor
    }

    private fun String.toStatus() = when (lowercase()) {
        "ongoing" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun Response.parseScriptToJson(): String? {
        val quickJs = QuickJs.create()
        val document = asJsoup()
        val script = document.select("script")
            .map(Element::data)
            .filter {
                it.contains("self.__next_f", ignoreCase = true)
            }
            .joinToString("\n")

        val content = quickJs.use {
            it.evaluate(
                """
                globalThis.self = globalThis;
                $script
                self.__next_f.map(it => it[it.length - 1]).join('')
                """.trimIndent(),
            ) as String
        }

        return JSON_REGEX.find(content)?.groups?.get(0)?.value
    }

    @SuppressLint("SimpleDateFormat")
    companion object {
        val IMAGE_URL_REGEX = """url\\":\\"([^(\\")]+)""".toRegex()
        val POPULAR_REGEX = """\{"cursor".+?\}{2}""".toRegex()
        val LATEST_REGEX = """\{"items".+?hasNextPage[^,]+""".toRegex()
        val DETAILS_REGEX = """\{"oId".+\}{3}""".toRegex()
        val CHAPTERS_REGEX = """\{"count".+totalPages.+\}""".toRegex()
        val CHAPTER_TOKEN_REGEX = """\("([^\)]+)",[^"]+"getChapters""".toRegex()
        val JSON_REGEX = listOf(
            POPULAR_REGEX,
            LATEST_REGEX,
            DETAILS_REGEX,
        ).joinToString("|").toRegex()

        val dateFormat = SimpleDateFormat("'\$D'yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        val APPLICATION_JSON = "application/json".toMediaType()
    }
}
