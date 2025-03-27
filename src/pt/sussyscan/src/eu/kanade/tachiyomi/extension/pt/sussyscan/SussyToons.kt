package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.annotation.SuppressLint
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
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class SussyToons : HttpSource(), ConfigurableSource {

    override val name = "Sussy Toons"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val id = 6963507464339951166

    // Moved from Madara
    override val versionId = 2

    private val json: Json by injectLazy()

    private val isCi = System.getenv("CI") == "true"

    private val preferences: SharedPreferences = getPreferences()

    private var apiUrl: String
        get() = preferences.getString(API_BASE_URL_PREF, defaultApiUrl)!!
        set(value) = preferences.edit().putString(API_BASE_URL_PREF, value).apply()

    private var restoreDefaultEnable: Boolean
        get() = preferences.getBoolean(DEFAULT_PREF, false)
        set(value) = preferences.edit().putBoolean(DEFAULT_PREF, value).apply()

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!
    }

    private val defaultBaseUrl: String = "https://www.sussytoons.wtf"
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
        .set("scan-id", "1") // Required header for requests

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.parseScriptToJson()
            ?: return MangasPage(emptyList(), false)
        val mangas = json.parseAs<WrapperDto>().popular?.toSMangaList()
            ?: emptyList()
        return MangasPage(mangas, false)
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/atualizacoes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val json = response.parseScriptToJson()
            ?: return MangasPage(emptyList(), false)
        val dto = json.parseAs<WrapperDto>()
        val mangas = dto.latest.toSMangaList()
        return MangasPage(mangas, dto.latest.hasNextPage())
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
        val json = response.parseScriptToJson()
            ?: throw IOException("Details do mangá não foi encontrado")
        return json.parseAs<ResultDto<MangaDto>>().results.toSManga()
    }

    // ============================= Chapters =================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseScriptToJson() ?: return emptyList()
        return json.parseAs<ResultDto<WrapperChapterDto>>().results.chapters.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                setUrlWithoutDomain("$baseUrl/capitulo/${it.id}")
                date_upload = dateFormat.tryParse(it.updateAt)
            }
        }.sortedByDescending(SChapter::chapter_number)
    }

    // ============================= Pages ====================================

    private val pageUrlSelector = "img.chakra-image"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        pageListParse(document).takeIf(List<Page>::isNotEmpty)?.let { return it }

        val dto = extractScriptData(document)
            .let(::extractJsonContent)
            .let(::parseJsonToChapterPageDto)

        return dto.pages.mapIndexed { index, image ->
            val imageUrl = when {
                image.isWordPressContent() -> {
                    CDN_URL.toHttpUrl().newBuilder()
                        .addPathSegments("wp-content/uploads/WP-manga/data")
                        .addPathSegments(image.src.toPathSegment())
                        .build()
                }
                else -> {
                    "$CDN_URL/scans/${dto.manga.scanId}/obras/${dto.manga.id}/capitulos/${dto.chapterNumber}/${image.src}"
                        .toHttpUrl()
                }
            }
            Page(index, imageUrl = imageUrl.toString())
        }
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
            ?.let { json.decodeFromString<String>("\"$it\"") }
            ?: throw Exception("Failed to extract JSON from script")
    }

    private fun parseJsonToChapterPageDto(jsonContent: String): ChapterPageDto {
        return try {
            jsonContent.parseAs<ResultDto<ChapterPageDto>>().results
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

    private fun Response.parseScriptToJson(): String? {
        val document = asJsoup()
        val script = document.select("script")
            .map(Element::data)
            .filter(String::isNotEmpty)
            .joinToString("\n")

        val content = QuickJs.create().use {
            it.evaluate(
                """
                globalThis.self = globalThis;
                $script
                self.__next_f.map(it => it[it.length - 1]).join('')
                """.trimIndent(),
            ) as String
        }

        return PAGE_JSON_REGEX.find(content)?.groups?.get(0)?.value
    }

    private fun HttpUrl.Builder.dropPathSegment(count: Int): HttpUrl.Builder {
        repeat(count) {
            removePathSegment(0)
        }
        return this
    }

    /**
     * Normalizes path segments:
     * Ex: [ "/a/b/", "/a/b", "a/b/", "a/b" ]
     * Result: "a/b"
     */
    private fun String.toPathSegment() = this.trim().split("/")
        .filter(String::isNotEmpty)
        .joinToString("/")

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"

        val pageRegex = """capituloInicial.{3}(.*?)(\}\]\})""".toRegex()
        val POPULAR_JSON_REGEX = """\{\"dataFeatured.+totalPaginas":\d+\}{2}""".toRegex()
        val LATEST_JSON_REGEX = """\{\"atualizacoesInicial.+\}\}""".toRegex()
        val DETAILS_CHAPTER_REGEX = """\{\"resultado.+"\}{3}""".toRegex()
        val PAGE_JSON_REGEX = """$POPULAR_JSON_REGEX|$LATEST_JSON_REGEX|$DETAILS_CHAPTER_REGEX""".toRegex()

        private const val URL_PREF_SUMMARY = "Para uso temporário, se a extensão for atualizada, a alteração será perdida."

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL da fonte"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"

        private const val API_BASE_URL_PREF = "overrideApiUrl"
        private const val API_BASE_URL_PREF_TITLE = "Editar URL da API da fonte"
        private const val API_DEFAULT_BASE_URL_PREF = "defaultApiUrl"

        private const val DEFAULT_PREF = "defaultPref"

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
