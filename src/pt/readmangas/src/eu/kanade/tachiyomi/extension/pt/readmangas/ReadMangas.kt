package eu.kanade.tachiyomi.extension.pt.readmangas

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ReadMangas() : HttpSource() {

    override val name = "Read Mangas"

    override val baseUrl = "https://app.loobyt.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .readTimeout(2, TimeUnit.MINUTES)
        .rateLimit(2)
        .build()

    override val versionId = 2

    // =========================== Popular ================================

    private var popularNextCursorPage = ""

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            popularNextCursorPage = ""
        }

        val input = buildJsonObject {
            put(
                "0",
                buildJsonObject {
                    put(
                        "json",
                        buildJsonObject {
                            put("direction", "forward")
                            if (popularNextCursorPage.isNotBlank()) {
                                put("cursor", popularNextCursorPage)
                            }
                        },
                    )
                },
            )
        }

        val url = "$baseUrl/api/deprecated/manga.getAllManga?batch=1".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val (mangaPage, nextCursor) = mangasPageParse(response)
        popularNextCursorPage = nextCursor
        return mangaPage
    }

    // =========================== Latest ===================================

    private var latestNextCursorPage = ""

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            latestNextCursorPage = Date().let { latestUpdateDateFormat.format(it) }
        }

        val input = buildJsonObject {
            put(
                "0",
                buildJsonObject {
                    put(
                        "json",
                        buildJsonObject {
                            put("direction", "forward")
                            put("limit", 20)
                            put("cursor", latestNextCursorPage)
                        },
                    )
                },
            )
        }

        val url = "$baseUrl/api/deprecated/discover.updated".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val (mangaPage, nextCursor) = mangasPageParse(response)
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
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img.w-full")?.absUrl("src")
            genre = document.select("div > label + div > div").joinToString { it.text() }

            description = document.select("script").map { it.data() }
                .firstOrNull { MANGA_DETAILS_DESCRIPTION_REGEX.containsMatchIn(it) }
                ?.let {
                    MANGA_DETAILS_DESCRIPTION_REGEX.find(it)?.groups?.get("description")?.value
                }

            document.selectFirst("div.flex > div.inline-flex.items-center:last-child")?.text()?.let {
                status = it.toStatus()
            }
        }
    }

    // =========================== Chapter =================================

    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val id = manga.url.substringAfterLast("#")
        val input = buildJsonObject {
            put(
                "0",
                buildJsonObject {
                    put(
                        "json",
                        buildJsonObject {
                            put("id", id)
                            put("page", page)
                            put("limit", 50)
                            put("sort", "desc")
                            put("search", "")
                        },
                    )
                },
            )
        }

        val url = "$baseUrl/api/deprecated/chapter.publicAllChapters".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()

        val apiHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl${manga.url.substringBeforeLast("#")}")
            .set("Content-Type", "application/json")
            .set("Cache-Control", "no-cache")
            .build()

        return GET(url, apiHeaders)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val response = tryFetchChapterPage(manga, page++)
            val dto = response
                .parseAs<List<WrapperResult<ChapterListDto>>>()
                .firstNotNullOf { it.result }
                .data.json
            chapters += chapterListParse(dto.chapters)
        } while (dto.hasNext())

        return Observable.just(chapters)
    }

    private val attempts = 3

    private fun tryFetchChapterPage(manga: SManga, page: Int): Response {
        repeat(attempts) { index ->
            try { return client.newCall(this.chapterListRequest(manga, page)).execute() } catch (e: Exception) { /* do nothing */ }
        }
        throw Exception("Não foi possivel obter os capitulos da página: $page")
    }

    private fun chapterListParse(chapters: List<ChapterDto>): List<SChapter> {
        return chapters.map {
            SChapter.create().apply {
                name = it.title
                chapter_number = it.number.toFloat()
                date_upload = it.createdAt.toDate()
                url = "/readme/${it.id}"
            }
        }
    }

    // =========================== Pages ===================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scripts = document.select("script").joinToString("\n") { it.data() }
        val pages = IMAGE_URL_REGEX.findAll(scripts).mapIndexed { index, match ->
            Page(index, imageUrl = match.groups["imageUrl"]!!.value)
        }.toList()

        return pages
    }

    override fun imageUrlParse(response: Response) = ""

    // =========================== Utilities ===============================

    private fun mangasPageParse(response: Response): Pair<MangasPage, String> {
        val dto = response.parseAs<List<WrapperResult<MangaListDto>>>().first()
        val data = dto.result?.data?.json ?: return MangasPage(emptyList(), false) to ""

        val mangas = data.mangas.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.thumbnailUrl
                author = it.author
                status = it.status.toStatus()
                url = "/title/${it.slug}#${it.id}"
            }
        }
        return MangasPage(mangas, data.nextCursor != null) to (data.nextCursor ?: "")
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0L }

    private fun String.toStatus() = when (lowercase()) {
        "ongoing" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    @SuppressLint("SimpleDateFormat")
    companion object {
        val MANGA_DETAILS_DESCRIPTION_REGEX = """description":(?<description>"[^"]+)""".toRegex()
        val IMAGE_URL_REGEX = """url\\":\\"(?<imageUrl>[^(\\")]+)""".toRegex()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        val latestUpdateDateFormat = SimpleDateFormat(
            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Coordinated Universal Time)'",
            Locale.ENGLISH,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
