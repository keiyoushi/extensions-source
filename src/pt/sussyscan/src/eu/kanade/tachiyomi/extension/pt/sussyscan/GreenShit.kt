package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException

abstract class GreenShit(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val scanId: Long = 1,
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val apiUrlV2 = "https://api2.sussytoons.wtf"

    // JSON decoder that ignores unknown fields
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageLocationInterceptor)
        .build()

    open val targetAudience: TargetAudience = TargetAudience.All
    open val contentOrigin: ContentOrigin = ContentOrigin.Web

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", scanId.toString())

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) = when (contentOrigin) {
        ContentOrigin.Mobile -> buildMobileRequest(page, "obras/recentes", 18)
        else -> GET(baseUrl, headers)
    }

    override fun popularMangaParse(response: Response) = when (contentOrigin) {
        ContentOrigin.Mobile -> parseAsListResponse(response)
        else -> parseWebListResponse(response, POPULAR_JSON_REGEX)
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int) =
        buildMobileRequest(page, "obras/atualizacoes", 24)

    override fun latestUpdatesParse(response: Response) =
        parseAsListResponse(response, hasNextPage = true)

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrlV2/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("todos_generos", "1")
            .addQueryParameterIfNotEmpty("obr_nome", query)

        var orderBy = "ultima_atualizacao"
        var orderDirection = "DESC"

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> orderBy = filter.selected
                is OrderDirectionFilter -> orderDirection = filter.selected
                is GenreFilter -> url.addQueryParameterIfNotEmpty("gen_id", filter.selected)
                is FormatFilter -> url.addQueryParameterIfNotEmpty("formt_id", filter.selected)
                is StatusFilter -> url.addQueryParameterIfNotEmpty("stt_id", filter.selected)
                is TagsFilter -> filter.state.filter { it.state }.forEach { tag ->
                    url.addQueryParameter("tags[]", tag.id)
                }
                else -> {}
            }
        }

        return GET(
            url.addQueryParameter("orderBy", orderBy)
                .addQueryParameter("orderDirection", orderDirection)
                .build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response) =
        parseAsListResponse(response, hasNextPage = true)

    override fun getFilterList() = FilterList(
        Filter.Header("Ordenar por"),
        OrderByFilter(),
        OrderDirectionFilter(),
        Filter.Separator(),
        Filter.Header("Filtros"),
        GenreFilter(),
        FormatFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Tags (selecione múltiplas)"),
        TagsFilter(TAGS_LIST),
    )

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = when (contentOrigin) {
        ContentOrigin.Mobile -> "$baseUrl${manga.url}"
        else -> super.getMangaUrl(manga)
    }

    override fun mangaDetailsRequest(manga: SManga) = when (contentOrigin) {
        ContentOrigin.Mobile -> {
            val slug = manga.url.substringAfterLast("/")
            GET("$apiUrlV2/obras/$slug", headers)
        }
        else -> super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(response: Response) = when (contentOrigin) {
        ContentOrigin.Mobile -> response.parseAsLenient<MangaDto>().toSManga()
        else -> parseWebDetails(response)
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = when (contentOrigin) {
        ContentOrigin.Mobile -> "$baseUrl${chapter.url}"
        else -> super.getChapterUrl(chapter)
    }

    override fun chapterListRequest(manga: SManga) = when (contentOrigin) {
        ContentOrigin.Mobile -> mangaDetailsRequest(manga)
        else -> super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response) = when (contentOrigin) {
        ContentOrigin.Mobile -> response.parseAs<MangaDto>().toSChapterList()
        else -> parseWebChapters(response)
    }.distinctBy(SChapter::url)

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = when (contentOrigin) {
        ContentOrigin.Mobile -> {
            val pathSegment = chapter.url.replace("capitulo", "capitulos")
            val authHeaders = headers.newBuilder()
                .set("authorization", "Bearer $token")
                .build()
            GET("$apiUrlV2$pathSegment", authHeaders)
        }
        else -> super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> = when (contentOrigin) {
        ContentOrigin.Mobile -> response.parseAs<ChapterPageDtoV2>().toPageList()
        else -> parseWebPages(response)
    }

    override fun imageUrlParse(response: Response) = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Auth =====================================

    private val credential by lazy {
        Credential(
            email = preferences.getString(USERNAME_PREF, "") ?: "",
            password = preferences.getString(PASSWORD_PREF, "") ?: "",
        )
    }

    private var tokenCache: Token? = null

    private val token: Token
        get() {
            tokenCache?.let { if (it.isValid()) return it }

            val savedToken = preferences.getString(TOKEN_PREF, null)
                ?.parseAs<Token>()
                ?.takeIf { it.isValid() }

            if (savedToken != null) {
                tokenCache = savedToken
                return savedToken
            }

            if (!credential.isNotEmpty()) {
                throw IOException("Adicione suas credenciais em Extensões > $name > Configurações")
            }

            return performLogin().also {
                preferences.edit().putString(TOKEN_PREF, it.toJsonString()).apply()
                tokenCache = it
            }
        }

    private fun performLogin(): Token {
        val payload = buildJsonObject {
            put("usr_email", credential.email)
            put("usr_senha", credential.password)
        }.toJsonString().toRequestBody("application/json".toMediaType())

        val response = network.cloudflareClient.newCall(
            POST("https://api.sussytoons.wtf/me/login", headers, payload),
        ).execute()

        if (!response.isSuccessful) {
            preferences.edit().putString(TOKEN_PREF, Token.empty().toJsonString()).apply()
            throw IOException("Falha ao realizar o login")
        }

        val tokenDto = response.parseAs<ResultDto<TokenDto>>().results
        return Token(tokenDto.value)
    }

    // ============================= Settings =================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (contentOrigin != ContentOrigin.Mobile) return

        EditTextPreference(screen.context).apply {
            key = USERNAME_PREF
            title = "📧 Email"
            summary = "Email de acesso"
            dialogMessage = "Insira seu email para prosseguir com o acesso aos recursos disponíveis na fonte\n\n" +
                "⚠️ Os dados inseridos serão usados somente para realizar o login na fonte"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie o aplicativo para aplicar as alterações", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "🔑 Senha"
            summary = "Senha de acesso"
            dialogMessage = "Insira sua senha para prosseguir com o acesso aos recursos disponíveis na fonte\n\n" +
                "⚠️ Os dados inseridos serão usados somente para realizar o login na fonte"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie o aplicativo para aplicar as alterações", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    // ============================= Helpers ==================================

    private fun buildMobileRequest(page: Int, endpoint: String, limit: Int): Request {
        val url = "$apiUrlV2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", limit.toString())
            .addQueryParameterIf(targetAudience != TargetAudience.All, "gen_id", targetAudience.toString())
            .build()
        return GET(url, headers)
    }

    private fun parseAsListResponse(response: Response, hasNextPage: Boolean = false): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), hasNextPage && dto.hasNextPage())
    }

    private fun parseWebListResponse(response: Response, regex: Regex): MangasPage {
        val json = response.parseScriptToJson()
            .let(regex::find)
            ?.groups?.get(1)?.value
            ?: return MangasPage(emptyList(), false)

        return MangasPage(json.parseAs<ResultDto<List<MangaDto>>>().toSMangaList(), false)
    }

    private fun parseWebDetails(response: Response): SManga {
        val json = response.parseScriptToJson()
            .let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(1)?.value
            ?: throw IOException("Details do mangá não foi encontrado")

        return json.parseAs<ResultDto<MangaDto>>().results.toSManga()
    }

    private fun parseWebChapters(response: Response): List<SChapter> {
        val json = response.parseScriptToJson()
            .let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(1)?.value
            ?: return emptyList()

        return json.parseAs<ResultDto<WrapperChapterDto>>().toSChapterList()
    }

    private fun parseWebPages(response: Response): List<Page> {
        val document = response.asJsoup()

        // Try direct image selector first
        document.select("img.chakra-image").takeIf { it.isNotEmpty() }?.let { elements ->
            return elements.mapIndexed { index, element ->
                Page(index, document.location(), element.absUrl("src"))
            }
        }

        // Fallback to script parsing
        val scriptData = document.select("script")
            .map(Element::data)
            .firstOrNull { PAGE_REGEX.containsMatchIn(it) }
            ?: throw IOException("Failed to load pages: Script data not found")

        val jsonContent = PAGE_REGEX.find(scriptData)
            ?.groups?.get(1)?.value
            ?.let { "\"$it\"".parseAs<String>() }
            ?: throw IOException("Failed to extract JSON from script")

        return try {
            jsonContent.parseAs<ResultDto<ChapterPageDto>>().toPageList()
        } catch (e: Exception) {
            throw IOException("Failed to load pages: ${e.message}")
        }
    }

    private fun Response.parseScriptToJson(): String {
        val script = asJsoup().select("script")
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

    private fun imageLocationInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful) return response

        response.close()

        // Retry without some path segments
        val newUrl = request.url.newBuilder()
            .apply { repeat(4) { removePathSegment(0) } }
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    // Custom parseAs that ignores unknown keys
    private inline fun <reified T> Response.parseAsLenient(): T {
        return json.decodeFromString(serializer(), body.string())
    }

    private inline fun <reified T> String.parseAsLenient(): T {
        return json.decodeFromString(serializer(), this)
    }

    // ============================= Constants ================================

    enum class TargetAudience(val value: Int) {
        All(1), Shoujo(4), Yaoi(7);
        override fun toString() = value.toString()
    }

    enum class ContentOrigin { Mobile, Web }

    companion object {
        private val PAGE_REGEX = """capituloInicial.{3}(.*?)(\}\]\})""".toRegex()
        private val POPULAR_JSON_REGEX = """(?:"dataTop":)(\{.+totalPaginas":\d+\})(?:.+"dataF)""".toRegex()
        private val DETAILS_CHAPTER_REGEX = """\{"obra":(\{.+"\}{3})""".toRegex()

        private const val TOKEN_PREF = "greenShitToken"
        private const val USERNAME_PREF = "usernamePref"
        private const val PASSWORD_PREF = "passwordPref"
    }
}
