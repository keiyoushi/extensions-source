package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Verdinha : HttpSource(), ConfigurableSource {

    override val name = "Verdinha"

    override val baseUrl = "https://verdinha.wtf"

    private val apiUrl = "https://api.verdinha.wtf"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // "Sussy Scan" --> "Verdinha"
    override val id = 6963507464339951166

    private val preferences by getPreferencesLazy()

    private val json: Json = Injekt.get<Json>()

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .addInterceptor { chain ->
                val request = chain.request()
                val token = getToken()
                if (token.isEmpty()) {
                    throw IOException("Faça o login nas configurações")
                }
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                val response = chain.proceed(newRequest)
                if (response.code == 403) {
                    response.close()
                    throw IOException("Vip não ativo")
                }
                response
            }
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json, text/plain, */*")
        .add("scan-id", "1")

    // ========================= Popular ====================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("tipo", "visualizacoes_geral")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("gen_id", getGenId())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MangaListDto>()
        val mangas = dto.obras.map { it.toSManga(apiUrl) }
        val hasNext = dto.pagina < dto.totalPaginas
        return MangasPage(mangas, hasNext)
    }

    // ========================= Latest =====================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/atualizacoes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("gen_id", getGenId())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ========================= Search =====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$apiUrl/obras/search".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("todos_generos", "1")
            .addQueryParameter("orderDirection", "DESC")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("obr_nome", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GeneroFilter -> filter.selected?.let { urlBuilder.addQueryParameter(filter.param, it) }
                is FormatoFilter -> filter.selected?.let { urlBuilder.addQueryParameter(filter.param, it) }
                is StatusFilter -> filter.selected?.let { urlBuilder.addQueryParameter(filter.param, it) }
                is SortFilter -> filter.selected?.let { urlBuilder.addQueryParameter(filter.param, it) }
                is TagFilter -> {
                    val selected = filter.selected
                    if (selected.isNotEmpty()) {
                        urlBuilder.addQueryParameter("tag_ids", selected.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ========================= Details ====================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/obra/")
        return GET("$apiUrl/obras/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDetailsDto>()
        return dto.toSManga(apiUrl)
    }

    // ========================= Chapters ===================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<MangaDetailsDto>()

        return dto.capitulos.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                chapter_number = chapter.number
                url = "/capitulo/${chapter.id}"
                date_upload = chapter.createdDate?.let { parseDate(it) }
                    ?: chapter.releaseDate?.let { parseDate(it) }
                    ?: 0L
            }
        }.reversed()
    }

    private fun parseDate(dateStr: String): Long {
        return dateFormat.tryParse(dateStr)
    }

    // ========================= Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.removePrefix("/capitulo/")
        return GET("$apiUrl/capitulos/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PagesDto>()
        val scanId = dto.obra?.scanId ?: 1
        val obraId = dto.obraId
        val chapterNumber = dto.chapterNumber

        return dto.pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.path != null && page.src != null && !page.src.startsWith("/") && !page.src.contains("/") ->
                    "$apiUrl/cdn${page.path}/${page.src}"
                page.path != null && page.path.contains(".") ->
                    "$apiUrl/${page.path}"
                page.src != null && page.src.startsWith("/") ->
                    "$apiUrl/cdn/wp-content/uploads/WP-manga/data${page.src}"
                page.src != null && page.src.contains("/") ->
                    "$apiUrl/cdn/wp-content/uploads/WP-manga/data/${page.src}"
                page.src != null ->
                    "$apiUrl/cdn/scans/$scanId/obras/$obraId/capitulos/$chapterNumber/${page.src}"
                else -> throw Exception("No image URL found")
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters ====================================

    override fun getFilterList() = getFilters()

    // ========================= Auth =======================================

    private fun getToken(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }

        val token = preferences.getString(PREF_TOKEN, "") ?: ""
        if (token.isNotEmpty()) return token

        return login(email, password)
    }

    private fun login(email: String, password: String): String {
        return try {
            val payload = AuthRequestDto(login = email, senha = password, userType = "usuario")
            val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$apiUrl/auth/login")
                .post(body)
                .header("Referer", "$baseUrl/")
                .header("Origin", baseUrl)
                .header("Accept", "application/json, text/plain, */*")
                .header("scan-id", "1")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val authResponse = response.parseAs<AuthResponseDto>()
                preferences.edit().putString(PREF_TOKEN, authResponse.accessToken).apply()
                authResponse.accessToken
            } else {
                response.close()
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) return

        Thread {
            val token = login(email, password)
            val message = if (token.isNotEmpty()) {
                "Login realizado com sucesso"
            } else {
                "Falha no login. Verifique suas credenciais."
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(Injekt.get<Application>(), message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ========================= Preferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nesta seção serão usados somente para realizar o login na fonte."
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte."

        EditTextPreference(screen.context).apply {
            key = PREF_EMAIL
            title = "E-mail"
            summary = "E-mail de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu e-mail"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().remove(PREF_TOKEN).apply()
                val password = preferences.getString(PREF_PASSWORD, "") ?: ""
                checkLogin(newValue as String, password)
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().remove(PREF_TOKEN).apply()
                val email = preferences.getString(PREF_EMAIL, "") ?: ""
                checkLogin(email, newValue as String)
                true
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_GEN_ID
            title = "Gênero padrão"
            summary = "%s"
            entries = GENRE_LIST.map { it.first }.toTypedArray()
            entryValues = GENRE_LIST.map { it.second }.toTypedArray()
            setDefaultValue("1")
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_EMAIL = "pref_email"
        private const val PREF_PASSWORD = "pref_password"
        private const val PREF_TOKEN = "pref_token"
        private const val PREF_GEN_ID = "pref_gen_id"

        private val GENRE_LIST = listOf(
            Pair("Livres", "1"),
            Pair("Shoujo / Romances", "4"),
            Pair("Hentais", "5"),
            Pair("Novel", "6"),
            Pair("Yaoi", "7"),
            Pair("Mangás", "8"),
        )

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun getGenId(): String {
        return preferences.getString(PREF_GEN_ID, "1") ?: "1"
    }
}
