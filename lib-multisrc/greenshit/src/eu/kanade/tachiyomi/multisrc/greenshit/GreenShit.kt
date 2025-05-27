package eu.kanade.tachiyomi.multisrc.greenshit

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

abstract class GreenShit(
    override val name: String,
    val url: String,
    override val lang: String,
    val scanId: Long = 1,
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val isCi = System.getenv("CI") == "true"

    private val preferences: SharedPreferences = getPreferences()

    protected var apiUrl: String
        get() = preferences.getString(API_BASE_URL_PREF, defaultApiUrl)!!
        private set(value) = preferences.edit().putString(API_BASE_URL_PREF, value).apply()

    private var restoreDefaultEnable: Boolean
        get() = preferences.getBoolean(DEFAULT_PREF, false)
        set(value) = preferences.edit().putBoolean(DEFAULT_PREF, value).apply()

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!
    }

    private val defaultBaseUrl: String = url
    private val defaultApiUrl: String = "https://api.sussytoons.wtf"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageLocation)
        .build()

    init {
        if (restoreDefaultEnable) {
            restoreDefaultEnable = false
            preferences.edit().putString(DEFAULT_BASE_URL_PREF, null).apply()
            preferences.edit().putString(API_DEFAULT_BASE_URL_PREF, null).apply()
        }

        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
        preferences.getString(API_DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultApiUrl) {
                preferences.edit()
                    .putString(API_BASE_URL_PREF, defaultApiUrl)
                    .putString(API_DEFAULT_BASE_URL_PREF, defaultApiUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", scanId.toString())

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
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
            .addQueryParameter("gen_id", "4")
            .build()
        return GET(url, headers)
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

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.parseScriptToJson().let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(0)?.value
            ?: throw IOException("Details do mangá não foi encontrado")
        return json.parseAs<ResultDto<MangaDto>>().results.toSManga()
    }

    // ============================= Chapters =================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseScriptToJson().let(DETAILS_CHAPTER_REGEX::find)
            ?.groups?.get(0)?.value
            ?: return emptyList()
        return json.parseAs<ResultDto<WrapperChapterDto>>().toSChapterList()
    }

    // ============================= Pages ====================================

    private val pageUrlSelector = "img.chakra-image"

    override fun pageListParse(response: Response): List<Page> {
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
        val fields = listOf(
            EditTextPreference(screen.context).apply {
                key = BASE_URL_PREF
                title = BASE_URL_PREF_TITLE
                summary = URL_PREF_SUMMARY

                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "URL padrão:\n$defaultBaseUrl"

                setDefaultValue(defaultBaseUrl)
            },
            EditTextPreference(screen.context).apply {
                key = API_BASE_URL_PREF
                title = API_BASE_URL_PREF_TITLE
                summary = buildString {
                    append("Se não souber como verificar a URL da API, ")
                    append("busque suporte no Discord do repositório de extensões.")
                    appendLine(URL_PREF_SUMMARY)
                    append("\n⚠ A fonte não oferece suporte para essa extensão.")
                }

                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "URL da API padrão:\n$defaultApiUrl"

                setDefaultValue(defaultApiUrl)
            },

            SwitchPreferenceCompat(screen.context).apply {
                key = DEFAULT_PREF
                title = "Redefinir configurações"
                summary = buildString {
                    append("Habilite para redefinir as configurações padrões no próximo reinicialização da aplicação.")
                    appendLine("Você pode limpar os dados da extensão em Configurações > Avançado:")
                    appendLine("\t - Limpar os cookies")
                    appendLine("\t - Limpar os dados da WebView")
                    appendLine("\t - Limpar o banco de dados (Procure a '$name' e remova os dados)")
                }
                setDefaultValue(false)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                    true
                }
            },
        )

        fields.forEach(screen::addPreference)
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

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"

        val pageRegex = """capituloInicial.{3}(.*?)(\}\]\})""".toRegex()
        val POPULAR_JSON_REGEX = """(?:"dataTop":)(\{.+totalPaginas":\d+\})(?:.+"dataF)""".toRegex()
        val DETAILS_CHAPTER_REGEX = """(\{\"resultado.+"\}{3})""".toRegex()

        private const val URL_PREF_SUMMARY = "Para uso temporário, se a extensão for atualizada, a alteração será perdida."

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL da fonte"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"

        private const val API_BASE_URL_PREF = "overrideApiUrl"
        private const val API_BASE_URL_PREF_TITLE = "Editar URL da API da fonte"
        private const val API_DEFAULT_BASE_URL_PREF = "defaultApiUrl"

        private const val DEFAULT_PREF = "defaultPref"
    }
}
