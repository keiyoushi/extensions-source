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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class SussyToons : HttpSource(), ConfigurableSource {

    override val name = "Sussy Toons"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val id = 6963507464339951166

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
        .set("scan-id", "1") // Default scan-id; overridden in pageListRequest

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("outro", "lancamentos")
            .addQueryParameter("limite", "18")
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("limite", "18")
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("obr_nome", query)
            .addQueryParameter("limite", "15")
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

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.split("/")[2] // Extrai obr_id de /obra/[id]/[slug]
        val url = "$apiUrl/obras/$mangaId".toHttpUrl()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<ResultDto<MangaDto>>()
        return dto.results.toSManga()
    }

    // ============================= Chapters =================================

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga) // Reutiliza a requisição de detalhes
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ResultDto<MangaDto>>()
        val chapters = dto.results.chapters
        if (chapters.isNullOrEmpty()) {
            println("Nenhum capítulo encontrado para a obra ${dto.results.name}")
            return emptyList()
        }
        return chapters.map {
            SChapter.create().apply {
                name = it.name
                chapter_number = it.chapterNumber ?: try {
                    it.name.replace("Capítulo ", "").toFloat()
                } catch (e: NumberFormatException) {
                    -1f // Fallback para capítulos inválidos
                }
                setUrlWithoutDomain("$baseUrl/capitulo/${it.id}")
                date_upload = dateFormat.tryParse(it.updateAt)
            }
        }.sortedByDescending(SChapter::chapter_number)
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.split("/").last() // Extrai cap_id de /capitulo/[id]
        val url = "$apiUrl/capitulo-app/$chapterId".toHttpUrl()
        // Ajustar scan-id dinamicamente será feito em pageListParse
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ResultDto<ChapterPageDto>>()
        val pages = dto.results.pages
        val manga = dto.results.manga
        val chapterNumber = dto.results.chapterNumber

        if (pages.isEmpty()) {
            println("Nenhuma página encontrada para o capítulo ${dto.results.cap_id}")
            return emptyList()
        }

        return pages.mapIndexed { index, page ->
            // Sanitizar src para lidar com espaços e caracteres especiais
            val sanitizedSrc = page.src.replace(" ", "%20").toPathSegment()
            val imageUrl = if (page.isWordPressContent()) {
                "$CDN_URL/wp-content/uploads/WP-manga/data/$sanitizedSrc"
            } else {
                "$CDN_URL/scans/${manga.scanId}/obras/${manga.id}/capitulos/$chapterNumber/$sanitizedSrc"
            }
            Page(index, imageUrl = imageUrl)
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

                dialogTitle = API_BASE_URL_PREF_TITLE
                dialogMessage = "URL da API padrão:\n$defaultApiUrl"

                setDefaultValue(defaultApiUrl)
            },
            SwitchPreferenceCompat(screen.context).apply {
                key = DEFAULT_PREF
                title = "Redefinir configurações"
                summary = buildString {
                    append("Habilite para redefinir as configurações padrões na próxima reinicialização da aplicação.")
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

    private fun String.toPathSegment() = this.trim().split("/")
        .filter(String::isNotEmpty)
        .joinToString("/")

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"

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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    }
}