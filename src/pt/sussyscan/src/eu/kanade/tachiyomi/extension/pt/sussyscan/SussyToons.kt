package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
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
    private val defaultApiUrl: String = "https://api-dev.sussytoons.site"

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

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/obras/top5", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.filterNot { it.slug.isNullOrBlank() }.map { it.toSManga() }
        return MangasPage(mangas, false) // There's a pagination bug
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.filterNot { it.slug.isNullOrBlank() }.map { it.toSManga() }
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "8")
            .addQueryParameter("obr_nome", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addPathSegment(manga.id)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<WrapperDto<MangaDto>>().results.toSManga()

    private val SManga.id: String get() {
        val mangaUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments(url)
            .build()
        return mangaUrl.pathSegments[2]
    }

    // ============================= Chapters =================================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<WrapperDto<WrapperChapterDto>>().results.chapters.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                setUrlWithoutDomain("$baseUrl/capitulo/${it.id}")
                date_upload = it.updateAt.toDate()
            }
        }.sortedBy(SChapter::chapter_number).reversed()
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
            jsonContent.parseAs<WrapperDto<ChapterPageDto>>().results
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

        val url = request.url.toString()
        if (url.contains(CDN_URL, ignoreCase = true)) {
            response.close()

            val newRequest = request.newBuilder()
                .url(url.replace(CDN_URL, OLDI_URL, ignoreCase = true))
                .build()

            return chain.proceed(newRequest)
        }
        return response
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

    private fun MangaDto.toSManga(): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = thumbnail?.let {
                when {
                    it.startsWith("http") -> thumbnail
                    else -> "$OLDI_URL/scans/$scanId/obras/${this@toSManga.id}/$thumbnail"
                }
            }
            initialized = true
            val mangaUrl = "$baseUrl/obra".toHttpUrl().newBuilder()
                .addPathSegment(this@toSManga.id.toString())
                .addPathSegment(this@toSManga.slug!!)
                .build()
            setUrlWithoutDomain(mangaUrl.toString())
        }

        description?.let { Jsoup.parseBodyFragment(it).let { sManga.description = it.text() } }
        sManga.status = status.toStatus()

        return sManga
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        return json.decodeFromStream(body.byteStream())
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0L }

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
        const val OLDI_URL = "https://oldi.sussytoons.site"

        val pageRegex = """capituloInicial.{3}(.*?)(\}\]\})""".toRegex()

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
