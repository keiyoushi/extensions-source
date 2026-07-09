package eu.kanade.tachiyomi.extension.pt.erosect

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EroSect :
    HttpSource(),
    ConfigurableSource {

    private val apiUrl = "$baseUrl/api"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val tokenProvider by lazy {
        AuthTokenProvider(
            preferences = preferences,
            client = network.client,
            apiUrl = apiUrl,
            loginHeaders = loginHeaders().build(),
        )
    }

    override val client = network.client.newBuilder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/obras/top10/views?periodo=total", headers)

    override fun popularMangaParse(response: Response): MangasPage = response.use {
        val data = it.parseAs<PopularResponse>()
        MangasPage(data.toSMangaList(), false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/capitulos/recentes?pagina=$page&limite=12", latestHeaders().build())

    override fun latestUpdatesParse(response: Response): MangasPage = paginatedMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$apiUrl/obras?pagina=$page&limite=20&busca=$query", headers)

    override fun searchMangaParse(response: Response) = paginatedMangaParse(response)

    private fun paginatedMangaParse(response: Response): MangasPage = response.use {
        val data = it.parseAs<PaginatedResponse>()
        MangasPage(data.toSMangaList(), data.hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/obras/$id", headers)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga = response.use {
        it.parseAs<ObraDetailResponse>().toSManga()
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/obras/$id/capitulos", headers)
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun chapterListParse(response: Response): List<SChapter> = response.use {
        val data = it.parseAs<CapitulosResponse>()
        data.toSChapterList(dateFormat)
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val urlParts = chapter.url.split("/")
        val obraId = urlParts[2]
        val numero = urlParts[4]
        return GET("$apiUrl/obras/$obraId/capitulos/$numero", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.use {
        val payload = it.parseAs<JsonObject>()
        if (Decrypt.isEncryptedPayload(payload)) {
            Decrypt.chapterPayload(payload, tokenProvider.requireToken())
        } else {
            payload
        }.parseAs<PageListResponse>().toPageList(baseUrl)
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: throw IOException("URL da imagem ausente.")
        return GET(imageUrl, imageHeaders(page.url).build())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun imageHeaders(referer: String) = headersBuilder()
        .set("Accept", "*/*")
        .set("Referer", referer)
        .set("Pragma", "no-cache")
        .set("Cache-Control", "no-cache")

    private fun latestHeaders() = headersBuilder()
        .set("Referer", "$baseUrl/lancamentos")

    private fun loginHeaders() = headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Content-Type", "application/json")
        .set("Referer", "$baseUrl/login")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "Os dados inseridos nesta secao serao usados somente para realizar o login na fonte."
        val message = "Insira %s para prosseguir com o acesso aos recursos disponiveis na fonte."

        EditTextPreference(screen.context).apply {
            key = AuthTokenProvider.EMAIL_PREF
            title = "E-mail"
            summary = "E-mail de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu e-mail"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                tokenProvider.clear()
                val password = preferences.getString(AuthTokenProvider.PASSWORD_PREF, "").orEmpty()
                tokenProvider.checkLogin(newValue as String, password)
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = AuthTokenProvider.PASSWORD_PREF
            title = "Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                tokenProvider.clear()
                val email = preferences.getString(AuthTokenProvider.EMAIL_PREF, "").orEmpty()
                tokenProvider.checkLogin(email, newValue as String)
                true
            }
        }.let(screen::addPreference)
    }
}
