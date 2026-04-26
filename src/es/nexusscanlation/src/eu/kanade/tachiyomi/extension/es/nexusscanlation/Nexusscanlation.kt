package eu.kanade.tachiyomi.extension.es.nexusscanlation

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class Nexusscanlation : HttpSource() {

    override val name = "NexusScanlation"
    override val baseUrl = "https://nexusscanlation.com"
    override val lang = "es"
    override val supportsLatest = true

    private val apiBaseUrl = "https://api.nexusscanlation.com/api/v1"
    private val apiHost = "https://api.nexusscanlation.com".toHttpUrl()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    private val json by injectLazy<Json>()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiHost, 1, 3) // API: max 1 request per 3 seconds
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            // For API requests, sync headers
            if (url.startsWith(apiBaseUrl)) {
                val newRequest = request.newBuilder()

                newRequest.header("Accept", "application/json, text/plain, */*")
                newRequest.header("Accept-Language", "es-419,es;q=0.9,es-ES;q=0.8")
                newRequest.header("Origin", baseUrl)
                newRequest.header("Referer", "$baseUrl/")

                newRequest.header("sec-fetch-dest", "empty")
                newRequest.header("sec-fetch-mode", "cors")
                newRequest.header("sec-fetch-site", "same-site")

                val cookies = getCookiesForDomain("https://api.nexusscanlation.com")
                if (cookies.isNotBlank()) {
                    newRequest.header("Cookie", cookies)
                }

                val response = chain.proceed(newRequest.build())

                if (response.code == 429) {
                    response.close()
                    throw IOException("Demasiadas peticiones. Espera unos segundos e intenta de nuevo.")
                }

                if (response.code == 403) {
                    val bodySnippet = response.peekBody(500).string()
                    response.close()
                    if (bodySnippet.contains("cloudflare", ignoreCase = true) || bodySnippet.contains("just a moment", ignoreCase = true)) {
                        throw IOException("Cloudflare bloqueó la IP. Abre WebView para resolver captcha.")
                    } else {
                        val msg = Regex(""""message"\s*:\s*"([^"]+)"""").find(bodySnippet)?.groupValues?.get(1)
                            ?: "Bloqueado por WAF (Cliente no permitido)"
                        throw IOException("API: $msg")
                    }
                }

                return@addInterceptor response
            }

            chain.proceed(request)
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept-Language", "es-419,es;q=0.9,es-ES;q=0.8")

    // ======================= Cookie Helpers =======================
    private fun getCookiesForDomain(url: String): String = try {
        val apiCookies = CookieManager.getInstance()?.getCookie(url) ?: ""
        val mainCookies = CookieManager.getInstance()?.getCookie(baseUrl) ?: ""

        val cookieMap = mutableMapOf<String, String>()
        parseCookieString(mainCookies, cookieMap)
        parseCookieString(apiCookies, cookieMap)
        cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    } catch (_: Exception) {
        ""
    }

    private fun parseCookieString(cookies: String, into: MutableMap<String, String>) {
        if (cookies.isBlank()) return
        cookies.split(";").forEach { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                into[parts[0].trim()] = parts[1].trim()
            }
        }
    }

    // ======================= Manga URLs ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesSlug, chapterSlug) = chapter.url.split('/', limit = 2)
        return "$baseUrl/series/$seriesSlug/chapter/$chapterSlug"
    }

    // ======================= Popular ======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("orden", "popular")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val root = response.parseAs<CatalogResponseDto>()
        return MangasPage(root.data.orEmpty().mapNotNull(::catalogToManga), root.meta?.hasNext ?: false)
    }

    // ======================= Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("orden", "nuevo")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ======================= Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = apiBaseUrl.toHttpUrl().newBuilder()

        if (query.isBlank()) {
            urlBuilder.addPathSegment("catalog")
        } else {
            urlBuilder
                .addPathSegment("catalog")
                .addPathSegment("search")
                .addQueryParameter("q", query)
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ======================= Details ======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val root = response.parseAs<SeriesPayloadDto>()
        return seriesToManga(root.serie)
    }

    // ======================= Chapters =====================================

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<SeriesPayloadDto>()
        val seriesSlug = payload.serie.slug

        return payload.capitulos.orEmpty()
            .map { chapterToModel(seriesSlug, it) }
            .toList()
    }

    // ======================= Pages ========================================

    override fun pageListRequest(chapter: SChapter): Request {
        val (seriesSlug, chapterSlug) = chapter.url.split('/', limit = 2)

        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(seriesSlug)
            .addPathSegment("capitulos")
            .addPathSegment(chapterSlug)
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.peekBody(Long.MAX_VALUE).string()

        // The API may return pages in two formats:
        // Wrapped:  { "data": { "paginas": [...], "requiere_registro": ... } }
        // Direct:   { "paginas": [...], "requiere_registro": ... }
        val chapterPagesDto = try {
            json.decodeFromString<ChapterPagesWrapperDto>(body).data
        } catch (_: Exception) {
            null
        } ?: try {
            json.decodeFromString<ChapterPagesDto>(body)
        } catch (_: Exception) {
            null
        } ?: throw IOException("Error al decodificar la respuesta del servidor.")

        if (chapterPagesDto.esPremium || chapterPagesDto.locked) {
            throw IOException("Capítulo Premium. No disponible.")
        }

        val pageList = chapterPagesDto.paginas

        if (pageList.isNullOrEmpty()) {
            throw IOException("No se encontraron páginas para este capítulo.")
        }

        return pageList
            .filter { !it.bloqueada && it.url.isNotBlank() }
            .mapIndexed { index, page -> Page(index, imageUrl = page.url) }
            .also {
                if (it.isEmpty()) {
                    throw IOException("Todas las páginas de este capítulo están bloqueadas.")
                }
            }
    }

    // ======================= Helpers =======================================

    private fun catalogToManga(item: CatalogEntryDto): SManga? {
        if (item.slug.isBlank() || item.titulo.isBlank()) return null
        return SManga.create().apply {
            url = item.slug
            title = item.titulo
            thumbnail_url = resolveCoverUrl(item.portadaUrl, item.id)
        }
    }

    private fun chapterToModel(seriesSlug: String, chapter: ChapterEntryDto): SChapter {
        val chapterNumber = chapter.numero.toString().removeSuffix(".0")

        var chapterName = chapter.titulo?.takeIf { it.isNotBlank() } ?: "Capítulo $chapterNumber"
        if (chapter.esPremium) {
            chapterName = "🔒 $chapterName"
        }

        return SChapter.create().apply {
            url = "$seriesSlug/${chapter.slug}"
            name = chapterName
            chapter_number = chapter.numero
            date_upload = dateFormat.tryParse(chapter.publishedAt)
        }
    }

    private fun seriesToManga(series: SeriesDto): SManga = SManga.create().apply {
        title = series.titulo
        thumbnail_url = resolveCoverUrl(series.portadaUrl, series.id)
        description = series.descripcion
        genre = series.generos
            ?.mapNotNull { it.nombre.takeIf { name -> name.isNotBlank() } }
            ?.joinToString()

        status = when (series.estado.lowercase(Locale.ROOT)) {
            "en_emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val credits = series.autores.orEmpty().mapNotNull { credit ->
            credit.nombre.takeIf { it.isNotBlank() }?.trim()?.let { it to credit.rol?.lowercase(Locale.ROOT) }
        }

        author = credits
            .filter { (_, role) -> role != "artista" }
            .map { (name) -> name }
            .distinct()
            .joinToString()
            .ifBlank { null }

        artist = credits
            .filter { (_, role) -> role == "artista" }
            .map { (name) -> name }
            .distinct()
            .joinToString()
            .ifBlank { null }
    }

    private fun resolveCoverUrl(rawUrl: String?, seriesId: String?): String? {
        // Use CDN url to prevent DDoS autobans from their WAF
        if (!seriesId.isNullOrBlank()) {
            return "https://cdn.nexusscanlation.com/series/$seriesId/portada.jpg"
        }
        return rawUrl.takeIf { !it.isNullOrBlank() }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
