package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class BlackoutComics :
    HttpSource(),
    ConfigurableSource {

    override val name = "Blackout Comics"

    override val baseUrl = "https://blackoutcomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var loginState = LoginState.UNCHECKED

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::ageGateInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("DNT", "1")
        .add("Sec-GPC", "1")
        .add("Upgrade-Insecure-Requests", "1")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".ranking-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text().trim()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/atualizados-recente?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text().trim()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        val hasNext = doc.select(".pagerx__link[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNext)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/comics".toHttpUrl().newBuilder()
                .addQueryParameter("src", query)
                .addQueryParameter("format", "json")
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/comics".toHttpUrl().newBuilder()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

        if (!status.isNullOrEmpty()) url.addQueryParameter("status", status)
        if (!genre.isNullOrEmpty()) url.addQueryParameter("gen", genre)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = if (response.request.url.queryParameter("format") == "json") {
        val searchResponse = response.parseAs<SearchResponse>()
        val mangas = searchResponse.items.map { item ->
            SManga.create().apply {
                title = item.name
                url = "/comics/${item.id}"
                thumbnail_url = item.imgUrl ?: ("$baseUrl/" + item.imgPr)
            }
        }
        MangasPage(mangas, false)
    } else {
        val doc = response.asJsoup()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text().trim()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.select(".project-title").text().trim()
            thumbnail_url = doc.select(".project-cover").attr("abs:src")
            author = doc.select(".quick-info-item:has(.fa-pen-nib) strong").text().trim()
            artist = doc.select(".quick-info-item:has(.fa-palette) strong").text().trim()
            description = doc.select(".project-description").text().trim()
            genre = doc.select(".project-genres .genre-tag").joinToString { it.text().trim() }

            val statusText = doc.select(".status-pill").text().lowercase()
            status = when {
                statusText.contains("lançamento") -> SManga.ONGOING
                statusText.contains("completo") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val mangaUrl = response.request.url.encodedPath

        return doc.select("#tab-capitulos-list .normal_ep").map { el ->
            SChapter.create().apply {
                val linkElement = el.selectFirst("a[href]")
                val num = el.select(".num").text().trim()

                if (linkElement != null) {
                    setUrlWithoutDomain(linkElement.attr("abs:href"))
                } else {
                    url = "$mangaUrl/ler/capitulo-$num"
                }

                var chapterName = "Capítulo $num"
                val title = el.select(".cell-title strong.line-3").text().trim()
                if (title.isNotEmpty()) {
                    chapterName += " - $title"
                }
                name = chapterName

                date_upload = dateFormat.tryParse(el.select(".cell-num .text-muted").text().trim())
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        ensureLoggedIn()
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val scriptMatch = Regex("""S\s*=\s*(\[.*?\])""").find(html)
        if (scriptMatch == null) {
            if (html.contains("showLoginModal()")) {
                throw Exception("Faça login nas configurações da extensão para ler os capítulos.")
            }
            throw Exception("Nenhuma página encontrada ou estrutura do site foi alterada.")
        }

        val jsonString = scriptMatch.groupValues[1]
        val urls = jsonString.parseAs<List<String>>()

        return urls.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Referer") // Images will 403 Forbidden/Fail if the Referer is present.
        .header("Accept", "image/*")
        .build()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
    )

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val emailPref = EditTextPreference(screen.context).apply {
            key = PREF_EMAIL_KEY
            title = "Email de Login"
            summary = "Email para acessar capítulos restritos"
            dialogTitle = "Email"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                loginState = LoginState.UNCHECKED
                true
            }
        }
        val passPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD_KEY
            title = "Senha"
            summary = "Senha da sua conta"
            dialogTitle = "Senha"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                loginState = LoginState.UNCHECKED
                true
            }
        }
        screen.addPreference(emailPref)
        screen.addPreference(passPref)
    }

    // ============================== Utilities =============================
    private fun ageGateInterceptor(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        if (url.host == baseUrl.toHttpUrl().host) {
            val cookies = client.cookieJar.loadForRequest(url)
            if (cookies.none { it.name == "age_gate_consent" }) {
                val ageCookie = Cookie.Builder()
                    .name("age_gate_consent")
                    .value("{\"consentAt\":1777661090431,\"expiresAt\":1778265890431}")
                    .domain(url.host)
                    .path("/")
                    .build()

                val popCookie = Cookie.Builder()
                    .name("_popprepop")
                    .value("1")
                    .domain(url.host)
                    .path("/")
                    .build()

                client.cookieJar.saveFromResponse(url, listOf(ageCookie, popCookie))
            }
        }
        return chain.proceed(original)
    }

    private fun ensureLoggedIn() {
        val email = preferences.getString(PREF_EMAIL_KEY, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD_KEY, "") ?: ""

        if (email.isBlank() || password.isBlank()) {
            throw Exception("Por favor, insira suas credenciais (Email e Senha) nas configurações da extensão para ler os capítulos.")
        }

        if (loginState == LoginState.LOGGED_IN) return

        synchronized(this) {
            if (loginState == LoginState.LOGGED_IN) return

            val initRes = client.newCall(GET(baseUrl, headers)).execute()
            val initDoc = initRes.asJsoup()
            val csrfToken = initDoc.select("meta[name=csrf-token]").attr("content")

            if (csrfToken.isEmpty()) {
                throw Exception("Não foi possível encontrar o token de sessão CSRF.")
            }

            val formBody = FormBody.Builder()
                .add("_token", csrfToken)
                .add("USE_EMAIL", email)
                .add("password", password)
                .build()

            val loginHeaders = headersBuilder()
                .add("X-CSRF-TOKEN", csrfToken)
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Origin", baseUrl)
                .add("Referer", "$baseUrl/")
                .build()

            val loginRes = client.newCall(POST("$baseUrl/entrar", loginHeaders, formBody)).execute()
            val loginBody = loginRes.body.string()

            if (loginBody.contains("sucesso", true) || loginRes.isSuccessful) {
                loginState = LoginState.LOGGED_IN
            } else {
                loginState = LoginState.FAILED
                throw Exception("Falha no login. Verifique suas credenciais nas configurações.")
            }
        }
    }

    private enum class LoginState { UNCHECKED, LOGGED_IN, FAILED }

    companion object {
        private const val PREF_EMAIL_KEY = "pref_email"
        private const val PREF_PASSWORD_KEY = "pref_password"

        private val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.ROOT)
    }
}
