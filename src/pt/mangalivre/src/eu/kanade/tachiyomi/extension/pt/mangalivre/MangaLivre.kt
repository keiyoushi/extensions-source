package eu.kanade.tachiyomi.extension.pt.mangalivre

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    HttpSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::clientHeaderInterceptor)
        .rateLimit(2, 1.seconds) { it.host == baseUrlHost }
        .build()

    private val scrapeClient: OkHttpClient by lazy {
        network.client.newBuilder()
            .followRedirects(false)
            .build()
    }

    private val apiUrl: String = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "*/*")
        .add("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "same-origin")

    // ============================== Popular =======================================

    private val popularFilter = FilterList(
        listOf(
            OrderByFilter(options = listOf("" to "popular")),
            OrderDirectionFilter(options = listOf("" to "desc")),
        ),
    )

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest =======================================

    private val latestFilter = FilterList(
        listOf(
            OrderByFilter(options = listOf("" to "updated")),
            OrderDirectionFilter(options = listOf("" to "desc")),
        ),
    )

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    url.addQueryParameter("sortBy", filter.selected())
                }
                is OrderDirectionFilter -> {
                    url.addQueryParameter("sortOrder", filter.selected())
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseJson<WrapperDto>()
        val mangas = dto.mangas.map { it.toSManga(useAlternativeTitle) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga-by-slug/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseJson<MangaDto>().toSManga(useAlternativeTitle)

    // ============================== Chapters =======================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseJson<MangaDto>().toSChapterList()

    // ============================== Pages =======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val dto = chapter.url.substringAfterLast("#").parseAs<ChapterReferenceDto>()
        return GET("$apiUrl/mangas/${dto.mangaId}/chapters/${dto.chapterId}", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseJson<PageDto>().toPageList()

    override fun imageUrlParse(response: Response): String = ""

    // ============================== Filters =======================================

    override fun getFilterList(): FilterList = FilterList(
        listOf(
            OrderByFilter(
                "Ordem",
                listOf(
                    "Mais Visualizados" to "popular",
                    "Lançamentos" to "release",
                    "Última Atualização" to "updated",
                    "Melhor Avaliação" to "rating",
                    "A-Z" to "title",
                ),
            ),
            Filter.Separator(),
            OrderDirectionFilter(
                "Direção",
                listOf(
                    "↑ Decrescente" to "desc",
                    "↓ Crescente" to "asc",
                ),
            ),
        ),
    )

    val useAlternativeTitle: Boolean get() =
        preferences.getBoolean(ALTERNATIVE_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_TITLE_PREF
            title = "Titulo alternativo"
            summary = buildString {
                append("Use titulos alternativos como principal quando disponivel.")
                append(" Essa opção não tem efeito sobre obras já adicionadas na sua bibilioteca")
            }
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Helper =======================================

    private inline fun <reified T> Response.parseJson(): T {
        val peek = peekBody(MAX_PEEK).string().trimStart()
        if (peek.isEmpty() || peek.startsWith("<")) {
            close()
            throw IOException(NON_JSON_MESSAGE)
        }
        return parseAs<T>()
    }

    @Volatile
    private var cachedToken: ClientToken? = null

    /**
     * O gate de "aplicativo oficial" (endpoints de leitura) exige um header de cliente que o
     * front-end injeta no bundle, rotacionado e reofuscado todo dia (literal -> atob -> char codes
     * `[..].map(String.fromCharCode)`). Em vez de perseguir cada encoding, decodificamos os
     * strings do bundle (char codes + base64), montamos pares "nome/valor" com forma de header e
     * deixamos o 403 "aplicativo oficial" ser o oráculo: testamos os candidatos até um dar 200.
     * O WebView (TokenExtractor) fica como último recurso, pois pode não existir no port iOS.
     */
    private fun clientHeaderInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }

        val token = currentToken()
        val response = chain.proceed(request.withClientHeader(token))
        if (response.code != 403 || !response.isOfficialAppError()) {
            return response
        }

        response.close()
        for (candidate in scrapeStaticCandidates()) {
            if (candidate == token) continue
            val retry = chain.proceed(request.withClientHeader(candidate))
            if (retry.code != 403 || !retry.isOfficialAppError()) {
                cachedToken = candidate
                return retry
            }
            retry.close()
        }
        extractTokenViaWebView()?.let { webViewToken ->
            val retry = chain.proceed(request.withClientHeader(webViewToken))
            if (retry.code != 403 || !retry.isOfficialAppError()) {
                cachedToken = webViewToken
                return retry
            }
            retry.close()
        }
        return chain.proceed(request.withClientHeader(token))
    }

    private fun Request.withClientHeader(token: ClientToken): Request = newBuilder().header(token.header, token.value).build()

    private fun currentToken(): ClientToken = cachedToken ?: synchronized(this) {
        cachedToken ?: (scrapeStaticCandidates().firstOrNull() ?: DEFAULT_TOKEN).also { cachedToken = it }
    }

    private fun scrapeStaticCandidates(): List<ClientToken> = try {
        val js = fetchBundle()
        val names = (decodeCharCodes(js) + decodeAtob(js))
            .filter(::isHeaderCandidate)
            .distinct()
            .take(MAX_POOL)
        names
            .flatMap { name -> names.mapNotNull { value -> if (name != value) ClientToken(name, value) else null } }
            .sortedByDescending { score(it.value) }
            .take(MAX_CANDIDATES)
    } catch (_: Exception) {
        emptyList()
    }

    private fun fetchBundle(): String {
        val html = scrapeClient.newCall(GET("$baseUrl/", headers)).execute()
            .use { if (it.isSuccessful) it.body.string() else "" }
        val assets = ASSET_REGEX.findAll(html).map { it.value }.distinct().toList()
        return buildString {
            assets.take(MAX_ASSETS).forEach { path ->
                scrapeClient.newCall(GET("$baseUrl$path", headers)).execute()
                    .use { if (it.isSuccessful) append(it.body.string()) }
            }
        }
    }

    private fun decodeCharCodes(js: String): List<String> = CHARCODE_REGEX.findAll(js)
        .mapNotNull { match ->
            val op = match.groupValues[2]
            val k = match.groupValues[3].toIntOrNull() ?: 0
            val codes = match.groupValues[1].split(",").mapNotNull { it.toIntOrNull() }.map { n ->
                when (op) {
                    "-" -> n - k
                    "+" -> n + k
                    "*" -> n * k
                    "^" -> n xor k
                    else -> n
                }
            }
            if (codes.isNotEmpty() && codes.all { it in 32..126 }) {
                codes.map { it.toChar() }.joinToString("")
            } else {
                null
            }
        }
        .toList()

    private fun decodeAtob(js: String): List<String> = ATOB_REGEX.findAll(js)
        .mapNotNull { it.groupValues[1].decodeBase64()?.utf8() }
        .toList()

    private fun isHeaderCandidate(s: String): Boolean = s.matches(HEADER_NAME_REGEX) && '-' in s && s.lowercase() !in STANDARD_HEADERS

    private fun score(value: String): Int = (if (value.any { it.isDigit() }) 200 else 0) + (MAX_VALUE_LEN - value.length).coerceAtLeast(0)

    private fun extractTokenViaWebView(): ClientToken? = try {
        TokenExtractor.extract(baseUrl, headers["User-Agent"])
            ?.let { ClientToken(it.header, it.value) }
    } catch (_: Exception) {
        null
    }

    private fun Response.isOfficialAppError(): Boolean = try {
        peekBody(MAX_PEEK).string().contains("aplicativo oficial", ignoreCase = true)
    } catch (_: Exception) {
        false
    }

    private data class ClientToken(val header: String, val value: String)

    companion object {
        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val MAX_PEEK = 1024L
        private const val MAX_ASSETS = 8
        private const val MAX_POOL = 12
        private const val MAX_CANDIDATES = 16
        private const val MAX_VALUE_LEN = 40
        private const val NON_JSON_MESSAGE =
            "Resposta não-JSON (Cloudflare ou header desatualizado). Abra a fonte na WebView do app e tente de novo."
        private val DEFAULT_TOKEN = ClientToken("x-tly-nexus", "c77-block-q")
        private val STANDARD_HEADERS = setOf("content-type", "accept", "accept-language", "authorization", "x-csrf-token")
        private val HEADER_NAME_REGEX = Regex("[A-Za-z][\\w.-]{1,40}")
        private val ASSET_REGEX = Regex("/assets/[\\w-]+\\.js")
        private val CHARCODE_REGEX = Regex("\\[([\\d,]{5,240})\\][^\\[]{0,60}?fromCharCode\\([a-z]+(?:([-+*^])(\\d{1,4}))?\\)")
        private val ATOB_REGEX = Regex("atob\\(\"([A-Za-z0-9+/=]{1,80})\"\\)")
    }
}
