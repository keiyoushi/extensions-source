package eu.kanade.tachiyomi.extension.pt.taiyo

import eu.kanade.tachiyomi.extension.pt.taiyo.dto.AdditionalInfoDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.MediaChapterDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.SearchResultDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Taiyo : ParsedHttpSource() {

    override val name = "Taiyō"

    override val baseUrl = "https://taiyo.moe"

    override val lang = "pt-BR"

    // The source doesn't show the title on the home page
    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .rateLimitHost(IMG_CDN.toHttpUrl(), 2)
        .build()

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    var bearerToken = ""

    override fun popularMangaRequest(page: Int): Request {
        if (bearerToken.isBlank()) {
            getBearerToken()
        }
        return searchMangaRequest(page, "", FilterList())
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = null

    private fun getBearerToken() {
        val scriptUrl = client.newCall(GET(baseUrl, headers))
            .execute().asJsoup()
            .selectFirst("script[src*=ee07d8437723d9f5]")
            ?.attr("src") ?: throw Exception("Não foi possivel localizar o token")

        val script = client.newCall(GET("$baseUrl$scriptUrl", headers))
            .execute().body.string()

        bearerToken = TOKEN_REGEX.find(script)?.groups?.get("token")?.value
            ?: throw Exception("Não foi possivel extrair o token")
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = null

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/media/$id"))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response.asJsoup())
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 21

        val jsonObj = buildJsonObject {
            put(
                "queries",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("indexUid", "medias")
                            put("q", query)
                            put("filter", buildJsonArray { add("deletedAt IS NULL") })
                            put("limit", limit)
                            put("offset", limit * (page - 1))
                        },
                    )
                },
            )
        }

        val requestBody = json.encodeToString(jsonObj).toRequestBody(MEDIA_TYPE)

        val apiHeaders = headers.newBuilder()
            .set("Authorization", "Bearer $bearerToken")
            .build()

        return POST("https://meilisearch.${baseUrl.substringAfterLast("/")}/multi-search", apiHeaders, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val obj = response.parseAs<SearchResultDto>()
        val mangas = obj.mangas.map { item ->
            SManga.create().apply {
                url = "/media/${item.id}"
                title = item.titles.firstOrNull { it.language.contains("en") }?.title
                    ?: item.titles.maxByOrNull { it.priority }!!.title

                thumbnail_url = item.coverId?.let {
                    "$baseUrl/_next/image?url=$IMG_CDN/${item.id}/covers/$it.jpg&w=256&q=75"
                }
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun searchMangaSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        thumbnail_url = document.selectFirst("section:has(h2) img")?.getImageUrl()
        title = document.selectFirst("p.media-title")!!.text()

        val additionalDataObj = document.parseJsonFromDocument<AdditionalInfoDto> {
            substringBefore(",\\\"trackers\\\"") + "}"
        }

        genre = additionalDataObj?.genres?.joinToString { it.portugueseName }
        status = when (additionalDataObj?.status.orEmpty()) {
            "FINISHED" -> SManga.COMPLETED
            "RELEASING" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        description = buildString {
            val synopsis = document.selectFirst("section > div.flex + div p")?.text()
                ?: additionalDataObj?.synopsis
            synopsis?.also { append("$it\n\n") }

            additionalDataObj?.titles?.takeIf { it.isNotEmpty() }?.run {
                append("Títulos alternativos:")
                forEach {
                    val languageName = Locale(it.language.substringBefore("_")).displayLanguage
                    append("\n\t$languageName: ${it.title}")
                }
            }
        }
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfter("/media/").trimEnd('/')
        var page = 1
        val apiUrl = "$baseUrl/api/trpc/chapters.getByMediaId?batch=1".toHttpUrl()
        val chapters = buildList {
            do {
                val input = buildJsonObject {
                    putJsonObject("0") {
                        putJsonObject("json") {
                            put("mediaId", id)
                            put("page", page)
                            put("perPage", 50)
                        }
                    }
                }

                page++

                val pageUrl = apiUrl.newBuilder()
                    .addQueryParameter("input", json.encodeToString(input))
                    .build()

                val chapters = client.newCall(GET(pageUrl, headers)).execute().let {
                    CHAPTER_REGEX.find(it.body.string())?.groups?.get("chapters")?.value
                }

                val parsed = json.decodeFromString<ChapterListDto>(chapters!!)

                addAll(
                    parsed.chapters.map {
                        SChapter.create().apply {
                            chapter_number = it.number
                            name = it.title?.takeIf(String::isNotBlank) ?: "Capítulo ${it.number}".replace(".0", "")
                            url = "/chapter/${it.id}/1"
                            date_upload = it.createdAt.orEmpty().toDate()
                        }
                    },
                )
            } while (page <= parsed.totalPages)
        }

        return Observable.just(chapters.sortedByDescending { it.chapter_number })
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val chapterObj = document.parseJsonFromDocument<MediaChapterDto>("mediaChapter") {
            substringBefore(",\\\"chapters\\\"") + "}}"
        }!!

        val base = "$IMG_CDN/${chapterObj.media.id}/chapters/${chapterObj.id}"

        return chapterObj.pages.mapIndexed { index, item ->
            Page(index, imageUrl = "$base/${item.id}.jpg")
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private fun Element.getImageUrl() = absUrl("srcset").substringBefore(" ")

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    private inline fun <reified T> Document.parseJsonFromDocument(
        itemName: String = "media",
        crossinline transformer: String.() -> String,
    ): T? {
        return runCatching {
            val script = selectFirst("script:containsData($itemName\\\\\":):containsData(\\\"6:\\[)")!!.data()
            val obj = script.substringAfter(",{\\\"$itemName\\\":")
                .run(transformer)
                .replace("\\", "")
            json.decodeFromString<T>(obj)
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val CHAPTER_REGEX = """(?<chapters>\{"chapters".+"totalPages":\d+\})""".toRegex()
        val TOKEN_REGEX = """NEXT_PUBLIC_MEILISEARCH_PUBLIC_KEY:(\s+)?"(?<token>[^"]+)""".toRegex()

        private const val IMG_CDN = "https://cdn.taiyo.moe/medias"

        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
    }
}
