package eu.kanade.tachiyomi.extension.pt.taiyo

import android.content.SharedPreferences
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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Taiyo : HttpSource() {

    override val name = "Taiyō"

    override val baseUrl = "https://taiyo.moe"

    override val lang = "pt-BR"

    override val supportsLatest = false

    private val preferences: SharedPreferences = getPreferences()

    private var bearerToken: String = preferences.getString(BEARER_TOKEN_PREF, "").toString()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .rateLimitHost(IMG_CDN.toHttpUrl(), 2)
        .addInterceptor(::authorizationInterceptor)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_SEARCH)) {
        val id = query.removePrefix(PREFIX_SEARCH)
        client.newCall(GET("$baseUrl/media/$id"))
            .asObservableSuccess()
            .map(::searchMangaByIdParse)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 21

        val requestBody = buildJsonObject {
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
        }.toJsonRequestBody()

        return POST("https://meilisearch.${baseUrl.substringAfterLast("/")}/multi-search", getApiHeaders(), requestBody)
    }

    private fun getApiHeaders() = headers.newBuilder()
        .set("Authorization", "Bearer $bearerToken")
        .build()

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

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
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

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

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
                    .addQueryParameter("input", jsonInstance.encodeToString(input))
                    .build()

                val chaptersJson = client.newCall(GET(pageUrl, headers)).execute().let {
                    CHAPTER_REGEX.find(it.body.string())?.groups?.get(1)?.value
                }

                val parsed = chaptersJson!!.parseAs<ChapterListDto>()

                addAll(
                    parsed.chapters.map {
                        SChapter.create().apply {
                            chapter_number = it.number
                            name = it.title?.takeIf(String::isNotBlank)
                                ?: "Capítulo ${it.number.toString().removeSuffix(".0")}"
                            url = "/chapter/${it.id}/1"
                            date_upload = DATE_FORMATTER.tryParse(it.createdAt)
                        }
                    },
                )
            } while (page <= parsed.totalPages)
        }

        return Observable.just(chapters.sortedByDescending { it.chapter_number })
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterObj = document.parseJsonFromDocument<MediaChapterDto>("mediaChapter") {
            substringBefore(",\\\"chapters\\\"") + "}}"
        }!!

        val base = "$IMG_CDN/${chapterObj.media.id}/chapters/${chapterObj.id}"

        return chapterObj.pages.mapIndexed { index, item ->
            Page(index, imageUrl = "$base/${item.id}.jpg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun Element.getImageUrl() = absUrl("srcset").substringBefore(" ")

    private inline fun <reified T> Document.parseJsonFromDocument(
        itemName: String = "media",
        crossinline transformer: String.() -> String,
    ): T? = runCatching {
        val script = selectFirst("script:containsData($itemName\\\\\":):containsData(\\\"6:\\[)")!!.data()
        val obj = script.substringAfter(",{\\\"$itemName\\\":")
            .run(transformer)
            .replace("\\", "")
        obj.parseAs<T>()
    }.onFailure { it.printStackTrace() }.getOrNull()

    // ============================= Authorization ===========================

    private fun authorizationInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        return when (response.code) {
            in arrayOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN) -> updateTokenAndContinueRequest(request, chain)
            else -> response
        }
    }

    private fun updateTokenAndContinueRequest(request: Request, chain: Interceptor.Chain): Response {
        bearerToken = getToken()
        val req = request.newBuilder()
            .headers(getApiHeaders())
            .build()
        return chain.proceed(req)
    }

    private fun getToken(): String = fetchBearerToken().also {
        preferences.edit()
            .putString(BEARER_TOKEN_PREF, it)
            .apply()
    }

    private fun fetchBearerToken(): String {
        val scripts = client.newCall(GET(baseUrl, headers))
            .execute().asJsoup()
            .select("script[src*=next]:not([nomodule]):not([src*=app])")

        val script = getScriptContainingToken(scripts)
            ?: throw Exception("Não foi possivel localizar o token")

        return TOKEN_REGEX.find(script)?.groups?.get(2)?.value
            ?: throw Exception("Não foi possivel extrair o token")
    }

    private fun getScriptContainingToken(scripts: Elements): String? {
        val elements = scripts.toList().reversed()
        for (element in elements) {
            val scriptUrl = element.attr("src")
            val script = client.newCall(GET("$baseUrl$scriptUrl", headers))
                .execute().body.string()
            if (TOKEN_REGEX.containsMatchIn(script)) {
                return script
            }
        }
        return null
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val CHAPTER_REGEX = """(\{"chapters".+"totalPages":\d+\})""".toRegex()
        val TOKEN_REGEX = """NEXT_PUBLIC_MEILISEARCH_PUBLIC_KEY:(\s+)?"([^"]+)""".toRegex()
        const val BEARER_TOKEN_PREF = "TAIYO_BEARER_TOKEN"

        private const val IMG_CDN = "https://cdn.taiyo.moe/medias"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
