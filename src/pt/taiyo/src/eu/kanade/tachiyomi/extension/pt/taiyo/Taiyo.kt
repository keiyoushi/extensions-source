package eu.kanade.tachiyomi.extension.pt.taiyo

import android.content.SharedPreferences
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.AdditionalInfoDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.ChapterListInputDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.MediaChapterDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.SearchQueryDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.SearchRequestDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.SearchResultDto
import eu.kanade.tachiyomi.extension.pt.taiyo.dto.TrpcInputDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Taiyo : KeiSource() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override val supportsLatest = false

    private val preferences: SharedPreferences = getPreferences()

    private var bearerToken: String = preferences.getString(BEARER_TOKEN_PREF, "").toString()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authorizationInterceptor)
        .rateLimit(2) { it.host == baseUrlHost || it.host == IMG_CDN_HOST }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) {
            return null
        }
        val id = url.pathSegments.getOrNull(1) ?: return null

        return parseMangaDetails(client.get("$baseUrl/media/$id").asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val limit = 21

        val requestBody = SearchRequestDto(
            listOf(
                SearchQueryDto(
                    indexUid = "medias",
                    q = query,
                    filter = listOf("deletedAt IS NULL"),
                    limit = limit,
                    offset = limit * (page - 1),
                ),
            ),
        ).toJsonRequestBody()

        val obj = client.post(
            "https://meilisearch.${baseUrl.substringAfterLast("/")}/multi-search",
            getApiHeaders(),
            requestBody,
        ).parseAs<SearchResultDto>()

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

    private fun getApiHeaders() = headers.newBuilder()
        .set("Authorization", "Bearer $bearerToken")
        .build()

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) {
            async { parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup()) }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
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

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val id = manga.url.substringAfter("/media/").trimEnd('/')
        var page = 1
        val apiUrl = "$baseUrl/api/trpc/chapters.getByMediaId?batch=1".toHttpUrl()
        val chapters = buildList {
            do {
                val input = mapOf("0" to TrpcInputDto(ChapterListInputDto(id, page, 50)))

                page++

                val pageUrl = apiUrl.newBuilder()
                    .addQueryParameter("input", input.toJsonString())
                    .build()

                val parsed = client.get(pageUrl).parseAs<ChapterListDto> {
                    CHAPTER_REGEX.find(it)!!.groupValues[1]
                }

                addAll(
                    parsed.chapters.map {
                        SChapter.create().apply {
                            chapter_number = it.number
                            name = it.title?.takeIf(String::isNotBlank)
                                ?: "Capítulo ${it.number.toString().removeSuffix(".0")}"
                            url = "/chapter/${it.id}/1"
                            date_upload = Instant.tryParse(it.createdAt)
                        }
                    },
                )
            } while (page <= parsed.totalPages)
        }

        return chapters.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterObj = document.parseJsonFromDocument<MediaChapterDto>("mediaChapter") {
            substringBefore(",\\\"chapters\\\"") + "}}"
        }!!

        val base = "$IMG_CDN/${chapterObj.media.id}/chapters/${chapterObj.id}"

        return chapterObj.pages.mapIndexed { index, item ->
            Page(index, imageUrl = "$base/${item.id}.jpg")
        }
    }

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
        val CHAPTER_REGEX = """(\{"chapters".+"totalPages":\d+\})""".toRegex()
        val TOKEN_REGEX = """NEXT_PUBLIC_MEILISEARCH_PUBLIC_KEY:(\s+)?"([^"]+)""".toRegex()
        const val BEARER_TOKEN_PREF = "TAIYO_BEARER_TOKEN"

        private const val IMG_CDN = "https://cdn.taiyo.moe/medias"
        private val IMG_CDN_HOST = IMG_CDN.toHttpUrl().host
    }
}
