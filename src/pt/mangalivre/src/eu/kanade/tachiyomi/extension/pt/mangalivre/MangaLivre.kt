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
        if (!response.requiresTokenRetry()) {
            return response
        }

        response.close()
        for (candidate in scrapeStaticCandidates()) {
            if (candidate == token) continue
            val retry = chain.proceed(request.withClientHeader(candidate))
            if (!retry.requiresTokenRetry()) {
                cachedToken = candidate
                return retry
            }
            retry.close()
        }
        extractTokenViaWebView()?.let { webViewToken ->
            val retry = chain.proceed(request.withClientHeader(webViewToken))
            if (!retry.requiresTokenRetry()) {
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
        val pool = (decodeChunkedAtob(js) + decodeAlphabet(js) + decodeCharCodes(js) + decodeAtob(js) + decodeLiterals(js)).distinct()
        val names = pool.filter { NAME_REGEX.matches(it) && it.lowercase() !in STANDARD_HEADERS }.take(MAX_POOL)
        val values = pool.filter { VALUE_REGEX.matches(it) && it.any(Char::isDigit) }.take(MAX_POOL)
        names
            .flatMap { name -> values.mapNotNull { value -> if (name != value) ClientToken(name, value) else null } }
            .sortedByDescending { score(it.value) }
            .take(MAX_CANDIDATES)
    } catch (_: Exception) {
        emptyList()
    }

    private fun fetchBundle(): String {
        val documentHeaders = headers.newBuilder()
            .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "document")
            .set("Sec-Fetch-Mode", "navigate")
            .set("Sec-Fetch-Site", "none")
            .set("Upgrade-Insecure-Requests", "1")
            .build()
        val scriptHeaders = headers.newBuilder()
            .set("Accept", "*/*")
            .set("Sec-Fetch-Dest", "script")
            .build()
        val html = scrapeClient.newCall(GET("$baseUrl/", documentHeaders)).execute()
            .use { if (it.isSuccessful) it.body.string() else "" }
        val assets = ASSET_REGEX.findAll(html).map { it.value }.distinct().toList()
        return buildString {
            assets.take(MAX_ASSETS).forEach { path ->
                scrapeClient.newCall(GET("$baseUrl$path", scriptHeaders)).execute()
                    .use { if (it.isSuccessful) append(it.body.string()) }
            }
        }
    }

    private fun decodeChunkedAtob(js: String): List<String> = CHUNKED_ATOB_REGEX.findAll(js)
        .mapNotNull { match ->
            CHUNK_REGEX.findAll(match.groupValues[1])
                .joinToString("") { it.groupValues[1] }
                .decodeBase64()
                ?.utf8()
        }
        .toList()

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

    // Novo mecanismo do site: [indices].map(i => alfabeto[i]).join(""), com o alfabeto num literal.
    private fun decodeAlphabet(js: String): List<String> {
        val alphabets = ALPHABET_REGEX.findAll(js).map { it.groupValues[1] }.filter { '-' in it }.distinct().toList()
        if (alphabets.isEmpty()) return emptyList()
        return INDEX_REGEX.findAll(js).flatMap { match ->
            val indices = match.groupValues[1].split(",").mapNotNull { it.toIntOrNull() }
            alphabets.mapNotNull { alpha ->
                if (indices.isNotEmpty() && indices.all { it < alpha.length }) {
                    indices.map { alpha[it] }.joinToString("")
                } else {
                    null
                }
            }
        }.toList()
    }

    private fun decodeLiterals(js: String): List<String> = LITERAL_REGEX.findAll(js).map { it.groupValues[1] }.toList()

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

    private fun Response.requiresTokenRetry(): Boolean = (code == 403 && isOfficialAppError()) ||
        (isRedirect && request.url.resolve(header("Location").orEmpty())?.host != baseUrlHost)

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
        private val DEFAULT_TOKEN = ClientToken("x-tly-omega", "y00-decoy-w")
        private val STANDARD_HEADERS = setOf("x-csrf-token", "x-requested-with", "x-toonlivre-authenticated-user")
        private val ASSET_REGEX = Regex("/assets/[\\w-]+\\.js")
        private val NAME_REGEX = Regex("(x|app)-[a-z]{2,}(-[a-z]{2,})*")
        private val VALUE_REGEX = Regex("[a-z0-9]{2,5}-[a-z0-9]{2,14}(-[a-z])?")
        private val ALPHABET_REGEX = Regex("\"([a-z0-9][a-z0-9-]{24,45})\"")
        private val INDEX_REGEX = Regex("\\[(\\d{1,2}(?:,\\d{1,2}){3,60})\\]\\.map\\(\\w+=>[\\w\$]{1,3}\\[\\w+\\]\\)")
        private val LITERAL_REGEX = Regex("\"([a-z0-9][a-z0-9-]{3,40})\"")
        private val CHARCODE_REGEX = Regex("\\[([\\d,]{5,240})\\][^\\[]{0,60}?fromCharCode\\([a-z]+(?:([-+*^])(\\d{1,4}))?\\)")
        private val ATOB_REGEX = Regex("atob\\(\"([A-Za-z0-9+/=]{1,80})\"\\)")
        private val CHUNKED_ATOB_REGEX = Regex("atob\\(\\[((?:\"[A-Za-z0-9+/=]+\",?)+)]")
        private val CHUNK_REGEX = Regex("\"([A-Za-z0-9+/=]+)\"")
    }
}
