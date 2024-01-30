package eu.kanade.tachiyomi.extension.pt.taiyo

import eu.kanade.tachiyomi.extension.pt.taiyo.dto.AdditionalInfoDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.MediaChapterDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.ResponseDto
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

    override val baseUrl = "https://www.taiyo.moe"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .rateLimitHost(IMG_CDN.toHttpUrl(), 2)
        .build()

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = "main > div.flex > div.overflow-hidden div.flex > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.selectFirst("div.overflow-hidden > img")?.getImageUrl()
        title = element.selectFirst("p")!!.text()
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "main div.grow div.flex:has(div.grow)"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("a.line-clamp-1")!!) {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.selectFirst("img")?.getImageUrl()?.replace("&w=128", "&w=256")
    }

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
        val jsonObj = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    put("title", query)
                }
            }
        }

        val requestBody = json.encodeToString(jsonObj).toRequestBody(MEDIA_TYPE)

        return POST("$baseUrl/api/trpc/medias.search?batch=1", headers, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val obj = response.parseAs<List<ResponseDto<List<SearchResultDto>>>>().first()
        val mangas = obj.data.map { item ->
            SManga.create().apply {
                url = "/media/${item.id}"
                title = item.title
                thumbnail_url = item.coverId?.let {
                    "$baseUrl/_next/image?url=$IMG_CDN/${item.id}/covers/$it.jpg&w=256&q=75"
                }
            }
        }
        return MangasPage(mangas, false)
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
        val apiUrl = "$baseUrl/api/trpc/mediaChapters.getByMediaId?batch=1".toHttpUrl()
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

                val res = client.newCall(GET(pageUrl, headers)).execute()
                val parsed = res.parseAs<List<ResponseDto<ChapterListDto>>>().first().data
                addAll(
                    parsed.chapters.map {
                        SChapter.create().apply {
                            chapter_number = it.number
                            name = it.title ?: "Capítulo ${it.number}".replace(".0", "")
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

        private const val IMG_CDN = "https://cdn.taiyo.moe/medias"

        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH)
        }
    }
}
