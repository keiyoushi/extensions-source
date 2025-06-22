package eu.kanade.tachiyomi.multisrc.greenshit

import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class GreenShit(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val scanId: Long = 1,
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    protected open val apiUrl = "https://api.sussytoons.wtf"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageLocation)
        .build()

    open val targetAudience: TargetAudience = TargetAudience.All

    open val contentOrigin: ContentOrigin = ContentOrigin.Web

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", scanId.toString())

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) =
        when (contentOrigin) {
            ContentOrigin.Mobile -> GET("$apiUrl/obras/top5", headers)
            else -> GET(baseUrl, headers)
        }

    override fun popularMangaParse(response: Response): MangasPage =
        when (contentOrigin) {
            ContentOrigin.Mobile -> popularMangaParseMobile(response)
            else -> popularMangaParseWeb(response)
        }

    private fun popularMangaParseMobile(response: Response): MangasPage {
        val mangas = response.parseAs<ResultDto<List<MangaDto>>>().toSMangaList()
        return MangasPage(mangas, hasNextPage = false)
    }

    private fun popularMangaParseWeb(response: Response): MangasPage {
        val json = response.parseScriptToJson().let(POPULAR_JSON_REGEX::find)
            ?.groups?.get(1)?.value
            ?: return MangasPage(emptyList(), false)
        val mangas = json.parseAs<ResultDto<List<MangaDto>>>().toSMangaList()
        return MangasPage(mangas, false)
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameterIf(targetAudience != TargetAudience.All, "gen_id", targetAudience.toString())
            .build()
        return GET(url, headers)
    }

    private fun HttpUrl.Builder.addQueryParameterIf(predicate: Boolean, name: String, value: String): HttpUrl.Builder {
        if (predicate) addQueryParameter(name, value)
        return this
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        val mangas = dto.toSMangaList()
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("obr_nome", query)
            .addQueryParameter("limite", "8")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("todos_generos", "true")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Details ==================================
    override fun getMangaUrl(manga: SManga) = when (contentOrigin) {
        ContentOrigin.Mobile -> "$baseUrl${manga.url}"
        else -> super.getMangaUrl(manga)
    }

    override fun mangaDetailsRequest(manga: SManga): Request =
        when (contentOrigin) {
            ContentOrigin.Mobile -> mangaDetailsRequestMobile(manga)
            else -> super.mangaDetailsRequest(manga)
        }

    private fun mangaDetailsRequestMobile(manga: SManga): Request {
        val pathSegment = manga.url.substringBeforeLast("/").replace("obra", "obras")
        return GET("$apiUrl$pathSegment", headers)
    }

    override fun mangaDetailsParse(response: Response) =
        when (contentOrigin) {
            ContentOrigin.Mobile -> response.parseAs<ResultDto<MangaDto>>().results.toSManga()
            else -> mangaDetailsParseWeb(response)
        }

    private fun mangaDetailsParseWeb(response: Response): SManga {
        val json = response.parseScriptToJson().let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(1)?.value
            ?: throw IOException("Details do mangá não foi encontrado")
        return json.parseAs<ResultDto<MangaDto>>().results.toSManga()
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = when (contentOrigin) {
        ContentOrigin.Mobile -> "$baseUrl${chapter.url}"
        else -> super.getChapterUrl(chapter)
    }

    override fun chapterListRequest(manga: SManga) =
        when (contentOrigin) {
            ContentOrigin.Mobile -> mangaDetailsRequest(manga)
            else -> super.chapterListRequest(manga)
        }

    override fun chapterListParse(response: Response): List<SChapter> =
        when (contentOrigin) {
            ContentOrigin.Mobile -> chapterListParseMobile(response)
            else -> chapterListParseWeb(response)
        }.distinctBy(SChapter::url)

    private fun chapterListParseMobile(response: Response): List<SChapter> =
        response.parseAs<ResultDto<WrapperChapterDto>>().toSChapterList()

    private fun chapterListParseWeb(response: Response): List<SChapter> {
        val json = response.parseScriptToJson().let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(1)?.value
            ?: return emptyList()
        return json.parseAs<ResultDto<WrapperChapterDto>>().toSChapterList()
    }

    // ============================= Pages ====================================

    private val pageUrlSelector = "img.chakra-image"

    override fun pageListRequest(chapter: SChapter): Request =
        when (contentOrigin) {
            ContentOrigin.Mobile -> pageListRequestMobile(chapter)
            else -> super.pageListRequest(chapter)
        }

    private fun pageListRequestMobile(chapter: SChapter): Request {
        val pathSegment = chapter.url.replace("capitulo", "capitulo-app-token")
        val newHeaders = headers.newBuilder()
            .set("x-client-hash", generateToken(scanId, SECRET_KEY))
            .set("authorization", "Bearer $token")
            .build()
        return GET("$apiUrl$pathSegment", newHeaders)
    }

    private fun generateToken(scanId: Long, secretKey: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val expiration = timestamp + 3600

        val payload = buildJsonObject {
            put("scan_id", scanId)
            put("timestamp", timestamp)
            put("exp", expiration)
        }.toJsonString()

        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")
        hmac.init(secretKeySpec)
        val signatureBytes = hmac.doFinal(payload.toByteArray())
        val signature = signatureBytes.joinToString("") { "%02x".format(it) }

        return Base64.encodeToString("$payload.$signature".toByteArray(), Base64.NO_WRAP)
    }

    override fun pageListParse(response: Response): List<Page> =
        when (contentOrigin) {
            ContentOrigin.Mobile -> pageListParseMobile(response)
            else -> pageListParseWeb(response)
        }

    private fun pageListParseMobile(response: Response): List<Page> =
        response.parseAs<ResultDto<ChapterPageDto>>().toPageList()

    private fun pageListParseWeb(response: Response): List<Page> {
        val document = response.asJsoup()

        pageListParse(document).takeIf(List<Page>::isNotEmpty)?.let { return it }

        val dto = extractScriptData(document)
            .let(::extractJsonContent)
            .let(::parseJsonToChapterPageDto)
        return dto.toPageList()
    }
    private fun pageListParse(document: Document): List<Page> {
        return document.select(pageUrlSelector).mapIndexed { index, element ->
            Page(index, document.location(), element.absUrl("src"))
        }
    }
    private fun extractScriptData(document: Document): String {
        return document.select("script").map(Element::data)
            .firstOrNull(pageRegex::containsMatchIn)
            ?: throw Exception("Failed to load pages: Script data not found")
    }

    private fun extractJsonContent(scriptData: String): String {
        return pageRegex.find(scriptData)
            ?.groups?.get(1)?.value
            ?.let { "\"$it\"".parseAs<String>() }
            ?: throw Exception("Failed to extract JSON from script")
    }

    private fun parseJsonToChapterPageDto(jsonContent: String): ResultDto<ChapterPageDto> {
        return try {
            jsonContent.parseAs<ResultDto<ChapterPageDto>>()
        } catch (e: Exception) {
            throw Exception("Failed to load pages: ${e.message}")
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Login ========================================

    private val credential: Credential by lazy {
        Credential(
            email = preferences.getString(USERNAME_PREF, "") as String,
            password = preferences.getString(PASSWORD_PREF, "") as String,
        )
    }

    private fun Token.save(): Token {
        return this.also {
            preferences.edit()
                .putString(TOKEN_PREF, it.toJsonString())
                .apply()
        }
    }

    private var _cache: Token? = null
    private val token: Token
        get() {
            if (_cache != null && _cache!!.isValid()) {
                return _cache!!
            }

            val tokenValue = preferences.getString(TOKEN_PREF, Token().toJsonString())?.parseAs<Token>()
            if (tokenValue != null && tokenValue.isValid()) {
                return tokenValue.also { _cache = it }
            }

            return credential.takeIf(Credential::isNotEmpty)?.let(::doLogin)?.let { response ->
                if (response.isSuccessful.not()) {
                    Token.empty().save()
                    throw IOException("Falha ao realizar o login")
                }
                val tokenDto = response.parseAs<ResultDto<TokenDto>>().results
                Token(tokenDto.value).also {
                    _cache = it.save()
                }
            } ?: throw IOException("Adicione suas credenciais em Extensões > $name > Configurações")
        }

    val loginClient = network.cloudflareClient

    fun doLogin(credential: Credential): Response {
        val payload = buildJsonObject {
            put("usr_email", credential.email)
            put("usr_senha", credential.password)
        }.toJsonString().toRequestBody("application/json".toMediaType())
        return loginClient.newCall(POST("$apiUrl/me/login", headers, payload)).execute()
    }

    // ============================= Interceptors =================================

    private fun imageLocation(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        response.close()

        val url = request.url.newBuilder()
            .dropPathSegment(4)
            .build()

        val newRequest = request.newBuilder()
            .url(url)
            .build()
        return chain.proceed(newRequest)
    }

    // ============================= Settings ====================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (contentOrigin != ContentOrigin.Mobile) {
            return
        }

        val warning = "⚠️ Os dados inseridos nessa seção serão usados somente para realizar o login na fonte"
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte"

        EditTextPreference(screen.context).apply {
            key = USERNAME_PREF
            title = "📧 Email"
            summary = "Email de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu email"))
                append("\n$warning")
            }

            setDefaultValue("")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "🔑 Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    // ============================= Utilities ====================================

    private fun Response.parseScriptToJson(): String {
        val document = asJsoup()
        val script = document.select("script")
            .map(Element::data)
            .filter(String::isNotEmpty)
            .joinToString("\n")

        return QuickJs.create().use {
            it.evaluate(
                """
                globalThis.self = globalThis;
                $script
                self.__next_f.map(it => it[it.length - 1]).join('')
                """.trimIndent(),
            ) as String
        }
    }

    private fun HttpUrl.Builder.dropPathSegment(count: Int): HttpUrl.Builder {
        repeat(count) {
            removePathSegment(0)
        }
        return this
    }

    enum class TargetAudience(val value: Int) {
        All(1),
        Shoujo(4),
        Yaoi(7),
        ;

        override fun toString() = value.toString()
    }

    enum class ContentOrigin {
        Mobile,
        Web,
    }

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"

        val pageRegex = """capituloInicial.{3}(.*?)(\}\]\})""".toRegex()
        val POPULAR_JSON_REGEX = """(?:"dataTop":)(\{.+totalPaginas":\d+\})(?:.+"dataF)""".toRegex()
        val DETAILS_CHAPTER_REGEX = """\{"obra":(\{.+"\}{3})""".toRegex()

        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"

        private const val TOKEN_PREF = "greenShitToken"
        private const val USERNAME_PREF = "usernamePref"
        private const val PASSWORD_PREF = "passwordPref"

        private const val SECRET_KEY = "sua_chave_secreta_aqui_32_caracteres"
    }
}
