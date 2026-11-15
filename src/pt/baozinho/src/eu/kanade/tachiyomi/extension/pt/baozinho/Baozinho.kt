package eu.kanade.tachiyomi.extension.pt.baozinho

import android.content.Context
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class SessaoRejeitadaException : IOException("sessão rejeitada pelo site")

private class CredenciaisAusentesException : IOException("e-mail ou senha não preenchidos")

private class CredenciaisRecusadasException : IOException("e-mail ou senha recusados pelo site")

private class VerificacaoFalhouException : IOException("não foi possível passar a verificação do site")

@Source
abstract class Baozinho :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://api.baozinho.com"

    private val preferences by getPreferencesLazy()

    private val hidePaid get() = preferences.getBoolean(HIDE_PAID_KEY, false)

    // The refresh token rotates on every renewal; the access token is short-lived and kept in memory only.
    private val authLock = Mutex()
    private var accessToken: String? = null
    private var accessExpiresAt = 0L
    private val hasSession get() = preferences.getString(REFRESH_KEY, null) != null
    private val temCredenciais get() = !preferences.getString(EMAIL_KEY, null).isNullOrBlank() && !preferences.getString(PASSWORD_KEY, null).isNullOrEmpty()
    private var jaTentouLoginAutomatico = false
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/obras/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/obras/${chapter.url}"

    override suspend fun getPopularManga(page: Int) = fetchObras(obrasUrl(apiUrl, page, POPULAR))

    override suspend fun getLatestUpdates(page: Int) = fetchObras(obrasUrl(apiUrl, page, "atualizadas"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = obrasUrl(
            apiUrl,
            page,
            orderBy = filters.firstInstanceOrNull<SortFilter>()?.value ?: POPULAR,
            query = query,
            status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
            formato = filters.firstInstanceOrNull<FormatoFilter>()?.value.orEmpty(),
            tagIds = filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty().filter { it.state }.map { it.id },
        )
        return fetchObras(url)
    }

    private suspend fun fetchObras(url: HttpUrl) = client.get(url, Fetch.XHR).parseAs<ObrasDto>().toMangasPage()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val tags = async { client.get("$apiUrl/tags", Fetch.XHR).parseAs<List<TagDto>>() }
        val formatos = async { client.get("$apiUrl/formatos", Fetch.XHR).parseAs<List<FormatoDto>>() }
        FiltrosDto(tags.await(), formatos.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filtros = data?.parseAs<FiltrosDto>()
        val filters = mutableListOf<Filter<*>>(SortFilter(), StatusFilter())
        if (filtros == null) {
            filters += Filter.Header("Formato e gêneros carregam em segundo plano: reabra os filtros em instantes")
        } else {
            filters += FormatoFilter(filtros.formatos)
            filters += TagFilter(filtros.tagsComObras())
        }
        return FilterList(filters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.takeIf { it.firstOrNull() == "obras" }?.getOrNull(1)
        return slug?.takeIf { it.isNotEmpty() }?.let { fetchObra(it).toSManga() }
    }

    private suspend fun fetchObra(slug: String) = client.get("$apiUrl/obras/slug/$slug", Fetch.XHR).parseAs<ObraDto>()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The chapter list needs the work's numeric id, which only the details endpoint returns.
        val obra = fetchObra(manga.url)
        val newChapters = if (fetchChapters) {
            coroutineScope {
                val lista = async { client.get("$apiUrl/capitulos/obra/${obra.id}", Fetch.XHR).parseAs<List<CapituloDto>>() }
                val liberados = async { fetchDesbloqueados(obra.id) }
                val desbloqueados = liberados.await()
                lista.await().visiveis(hidePaid, desbloqueados).map { it.toSChapter(manga.url, desbloqueados.orEmpty()) }
            }
        } else {
            chapters
        }
        return SMangaUpdate(obra.toSManga(), newChapters)
    }

    // A paid chapter the account has not unlocked comes without "paginas": an empty list makes the app show its own error.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.memo["id"]?.stringOrNull ?: return emptyList()
        return getJsonComSessao<CapituloPaginasDto>("$apiUrl/capitulos/$id", Fetch.XHR.from(getChapterUrl(chapter))).toPages()
    }

    // null = logged in but the unlocked list could not be fetched; with no session nothing is unlocked.
    private suspend fun fetchDesbloqueados(obraId: Int): Set<Int>? {
        if (!hasSession && !temCredenciais) return emptySet()
        return try {
            getJsonComSessao<List<DesbloqueadoDto>>("$apiUrl/capitulos/obra/$obraId/desbloqueados", Fetch.XHR).map { it.capId }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // The user's state must not come from the client's 10-minute response cache.
    private suspend inline fun <reified T> getJsonComSessao(url: String, fetch: Fetch): T {
        var token = tokenDeAcesso()
        for (tentativa in 0..1) {
            val cabecalhos = token?.let { headers.newBuilder().set("Authorization", "Bearer $it").build() } ?: headers
            val response = client.get(url, cabecalhos, fetch, CacheControl.FORCE_NETWORK, ensureSuccess = false)
            if (response.code == 401 && token != null && tentativa == 0) {
                response.close()
                token = tokenDeAcesso(forcar = true)
                continue
            }
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code}")
            }
            return response.parseAs<T>()
        }
        error("inalcançável")
    }

    private suspend fun tokenDeAcesso(forcar: Boolean = false): String? = authLock.withLock {
        if (!forcar && System.currentTimeMillis() < accessExpiresAt) return@withLock accessToken
        val renovado = preferences.getString(REFRESH_KEY, null)?.let {
            try {
                salvarSessao(renovar(it)).accessToken
            } catch (_: SessaoRejeitadaException) {
                encerrarSessao()
                null
            }
        }
        renovado ?: loginAutomatico()
    }

    // With no session (or a rejected one), use the saved credentials, at most once per app launch. Runs under authLock.
    private suspend fun loginAutomatico(): String? {
        if (!deveLogarSozinho(temCredenciais, jaTentouLoginAutomatico)) return null
        jaTentouLoginAutomatico = true
        return try {
            loginComCredenciais().accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // The login needs a Cloudflare Turnstile token; the site's own widget runs in a WebView on the device.
    private suspend fun loginComCredenciais(): TokensDto {
        val email = preferences.getString(EMAIL_KEY, null).orEmpty().trim()
        val senha = preferences.getString(PASSWORD_KEY, null).orEmpty()
        if (email.isEmpty() || senha.isEmpty()) throw CredenciaisAusentesException()
        val turnstile = obterTokenTurnstile()
        val response = client.post("$apiUrl/auth/login", Fetch.XHR, LoginRequest(email, senha, turnstile).toJsonRequestBody(), ensureSuccess = false)
        if (response.isSuccessful) return salvarSessao(response.parseAs<TokensDto>())
        val code = response.code
        val corpo = response.use { it.body.string() }
        throw when (classificarFalhaLogin(code, corpo)) {
            FalhaLogin.CREDENCIAIS -> CredenciaisRecusadasException()
            FalhaLogin.VERIFICACAO -> VerificacaoFalhouException()
            FalhaLogin.OUTRA -> IOException("HTTP $code ao entrar")
        }
    }

    private suspend fun obterTokenTurnstile(): String = try {
        runWebView<String>(timeout = 30.seconds) {
            poll(500.milliseconds) {
                evaluateJs("window.__tk || ''") { valor ->
                    valor.parseAs<String?>()?.takeIf { it.isNotEmpty() }?.let { resolve(it) }
                }
            }
            loadData(baseUrl, turnstileHtml(TURNSTILE_SITE_KEY))
        }
    } catch (_: WebViewTimeoutException) {
        throw VerificacaoFalhouException()
    }

    private suspend fun renovar(refreshToken: String): TokensDto {
        val response = client.post("$apiUrl/auth/refresh", Fetch.XHR, RefreshRequest(refreshToken).toJsonRequestBody(), ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs<TokensDto>()
        val code = response.code
        val corpo = response.use { it.body.string() }
        throw if (sessaoRejeitada(code, corpo)) SessaoRejeitadaException() else IOException("HTTP $code ao renovar a sessão")
    }

    // commit(), not apply(): the rotated token must be on disk before any other renewal.
    private fun salvarSessao(tokens: TokensDto): TokensDto {
        preferences.edit().putString(REFRESH_KEY, tokens.refreshToken).commit()
        accessToken = tokens.accessToken
        accessExpiresAt = System.currentTimeMillis() + ACCESS_TTL_MS
        return tokens
    }

    private fun encerrarSessao() {
        preferences.edit().remove(REFRESH_KEY).commit()
        accessToken = null
        accessExpiresAt = 0
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        val hidePaidPref = SwitchPreferenceCompat(context).apply {
            key = HIDE_PAID_KEY
            title = "Ocultar capítulos pagos"
            summary = "Esconde da lista os capítulos com 🔒 que a sua conta não liberou. Vale a partir da próxima atualização da obra."
            setDefaultValue(false)
        }

        // Action menu: only specific Preference subclasses are available to extensions, so a list acts as a button.
        val sessionPref = ListPreference(context).apply {
            key = SESSION_ACTION_KEY
            title = "Sessão do site"
            summary = statusSessao()
            entries = arrayOf("Entrar com e-mail e senha", "Importar sessão do site", "Sair (apagar a sessão guardada)")
            entryValues = arrayOf(ACTION_LOGIN, ACTION_IMPORT, ACTION_LOGOUT)
            setOnPreferenceChangeListener { pref, action ->
                when (action) {
                    ACTION_LOGIN -> entrarComCredenciais(context, pref)
                    ACTION_IMPORT -> importarSessao(context, pref)
                    ACTION_LOGOUT -> {
                        encerrarSessao()
                        pref.summary = statusSessao()
                        toast(context, "Sessão removida da extensão.")
                    }
                }
                false
            }
        }

        val emailPref = EditTextPreference(context).apply {
            key = EMAIL_KEY
            title = "E-mail"
            summary = preferences.getString(EMAIL_KEY, null)?.takeIf { it.isNotBlank() } ?: "Não definido"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
            setOnPreferenceChangeListener { pref, valor ->
                pref.summary = (valor as String).takeIf { it.isNotBlank() } ?: "Não definido"
                sessionPref.summary = statusSessao()
                true
            }
        }

        // Saving the password with an e-mail already set logs in right away; the new value is stored by the time the coroutine runs.
        val passwordPref = EditTextPreference(context).apply {
            key = PASSWORD_KEY
            title = "Senha"
            summary = resumoSenha(preferences.getString(PASSWORD_KEY, null).orEmpty())
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            setOnPreferenceChangeListener { pref, valor ->
                pref.summary = resumoSenha(valor as String)
                if (valor.isNotEmpty() && !preferences.getString(EMAIL_KEY, null).isNullOrBlank()) {
                    entrarComCredenciais(context, sessionPref)
                }
                true
            }
        }

        listOf(hidePaidPref, emailPref, passwordPref, sessionPref).forEach(screen::addPreference)
    }

    private fun statusSessao() = when {
        hasSession -> "Conectado. Toque para entrar de novo, importar outra sessão ou sair."
        temCredenciais -> "Não conectado. Toque e escolha Entrar com e-mail e senha."
        else -> "Não conectado. Preencha o e-mail e a senha acima. (Alternativa: entre no site pelo WebView, feche-o e escolha Importar sessão.)"
    }

    private fun entrarComCredenciais(context: Context, pref: Preference) {
        uiScope.launch {
            toast(context, "Entrando…")
            val mensagem = try {
                val tokens = authLock.withLock { loginComCredenciais() }
                "Conectado${nomeDaConta(tokens.accessToken)?.let { " como $it" }.orEmpty()}."
            } catch (e: CancellationException) {
                throw e
            } catch (_: CredenciaisAusentesException) {
                "Preencha o e-mail e a senha primeiro."
            } catch (_: CredenciaisRecusadasException) {
                "O site recusou o e-mail ou a senha."
            } catch (_: VerificacaoFalhouException) {
                "Não consegui passar a verificação do site. Use Importar sessão do site."
            } catch (e: Exception) {
                "Falha ao entrar (${e.javaClass.simpleName}). Verifique a conexão e tente de novo."
            }
            pref.summary = statusSessao()
            toast(context, mensagem)
        }
    }

    private suspend fun nomeDaConta(accessToken: String): String? = try {
        val cabecalhos = headers.newBuilder().set("Authorization", "Bearer $accessToken").build()
        client.get("$apiUrl/auth/me", cabecalhos, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<UsuarioDto>().nome
    } catch (_: IOException) {
        null
    }

    // Order: read R1 from the WebView, renew with R1, store R2, and only then clear the WebView copy so the site cannot reuse R1.
    private fun importarSessao(context: Context, pref: Preference) {
        uiScope.launch {
            toast(context, "Importando sessão…")
            val mensagem = try {
                val r1 = getLocalStorage(baseUrl, SITE_REFRESH_KEY)
                if (r1.isNullOrBlank()) {
                    "Nenhuma sessão encontrada no WebView. Entre no site pelo WebView, feche-o e tente de novo."
                } else {
                    val tokens = authLock.withLock { salvarSessao(renovar(r1)) }
                    limparSessaoDoWebView()
                    "Conectado${nomeDaConta(tokens.accessToken)?.let { " como $it" }.orEmpty()}."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessaoRejeitadaException) {
                "O site recusou a sessão importada. Entre de novo no WebView e tente outra vez."
            } catch (e: Exception) {
                "Falha ao importar (${e.javaClass.simpleName}). Verifique a conexão e tente de novo."
            }
            pref.summary = statusSessao()
            toast(context, mensagem)
        }
    }

    private suspend fun limparSessaoDoWebView() {
        runCatching {
            runWebView<Unit>(timeout = 10.seconds) {
                onPageFinished {
                    evaluateJs("localStorage.removeItem('$SITE_ACCESS_KEY');localStorage.removeItem('$SITE_REFRESH_KEY')") { resolve(Unit) }
                }
                loadData(baseUrl, """<link rel="icon" href="data:,">""")
            }
        }
    }

    private fun toast(context: Context, texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

    private companion object {
        const val POPULAR = "populares_total"
        const val HIDE_PAID_KEY = "hide_paid_chapters"
        const val REFRESH_KEY = "session_refresh_token"
        const val EMAIL_KEY = "login_email"
        const val PASSWORD_KEY = "login_password"
        const val SESSION_ACTION_KEY = "session_action"
        const val ACTION_LOGIN = "login"
        const val ACTION_IMPORT = "import"
        const val ACTION_LOGOUT = "logout"
        const val SITE_ACCESS_KEY = "bz_access"
        const val SITE_REFRESH_KEY = "bz_refresh"
        const val ACCESS_TTL_MS = 10 * 60 * 1000L

        // Public key of the site's Turnstile widget (it appears in the login page's JS).
        const val TURNSTILE_SITE_KEY = "0x4AAAAAADtmuUfMFzDWFmF8"
    }
}
