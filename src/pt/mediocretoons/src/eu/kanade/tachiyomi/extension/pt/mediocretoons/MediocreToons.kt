package eu.kanade.tachiyomi.extension.pt.mediocretoons
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import uy.kohesive.injekt.api.get
import java.text.Normalizer

class MediocreToons : HttpSource(), ConfigurableSource {

    override val name = "Mediocre Toons"
    override val baseUrl = "https://mediocrescan.com"
    override val lang = "pt-BR"
    override val supportsLatest = true
    private val apiUrl by lazy {
        preferences.getString(API_URL_PREF, "https://api.mediocretoons.site")!!
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun customAllRequestsEnabled(): Boolean =
        preferences.getBoolean(PREF_CUSTOM_ALL_REQUESTS, false)

    /** Retorna a URL do request: usa preferência customizada se Custom ALL REQUEST estiver ativo, senão defaultPath. Substitui $apiUrl pelo valor da API. */
    private fun getRequestUrl(defaultPath: String, prefKey: String, vararg formatArgs: String): String {
        val path = if (customAllRequestsEnabled()) {
            preferences.getString(prefKey, defaultPath) ?: defaultPath
        } else {
            defaultPath
        }
        val withIds = if (formatArgs.isNotEmpty()) path.replace("%s", formatArgs[0]) else path
        val resolved = withIds.replace("\$apiUrl", apiUrl)
        return if (resolved.startsWith("http")) resolved else "$apiUrl/$resolved".replace(Regex("(?<!:)//+"), "/")
    }

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(::authIntercept)
        .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = getValidToken()
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(authenticatedRequest)

        if (response.code == 401) {
            response.body?.close()
            cachedToken = null
            tokenExpiryTime = 0L
            val newToken = getValidToken()

            return if (!newToken.isNullOrEmpty()) {
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                chain.proceed(retryRequest)
            } else {
                chain.proceed(originalRequest)
            }
        }
        return response
    }

    private fun getValidToken(): String? {
        val now = System.currentTimeMillis()

        if (cachedToken != null && now < tokenExpiryTime) {
            return cachedToken
        }

        return fetchNewToken()
    }

    private fun fetchNewToken(): String? {
        return try {
            val email = preferences.getString(EMAIL_PREF, "")
            val password = preferences.getString(PASSWORD_PREF, "")

            if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
                return null
            }

            return loginAndGetToken(email, password)
        } catch (e: Exception) {
            null
        }
    }

    private fun loginAndGetToken(email: String, password: String): String? {
        return try {
            val json = JSONObject()
                .put("email", email.trim())
                .put("senha", password)
                .toString()

            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(getRequestUrl("\$apiUrl/auth/login", PREF_URL_LOGIN))
                .post(body)
                .header("x-app-key", "toons-mediocre-app")
                .header("Accept", "application/json")
                .build()

            val response = network.cloudflareClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body.string()
                val jsonResponse = JSONObject(responseBody)

                val token = when {
                    jsonResponse.has("token") -> jsonResponse.getString("token")
                    jsonResponse.has("access_token") -> jsonResponse.getString("access_token")
                    else -> null
                }

                if (!token.isNullOrEmpty()) {
                    val expiresIn = jsonResponse.optLong("expiresIn", 3600) * 1000
                    cachedToken = token
                    tokenExpiryTime = System.currentTimeMillis() + expiresIn

                    return token
                } else {
                    return null
                }
            } else {
                val errorBody = response.body.string()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("x-app-key", "toons-mediocre-app")
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        val url = getRequestUrl("\$apiUrl/obras/ranking", PREF_URL_POPULAR).toHttpUrl().newBuilder()
            .addQueryParameter("ordenarPor", "view_geral")
            .addQueryParameter("limite", "100")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingList = response.parseAs<List<MediocreRankingDto>>()

        val mangas = rankingList.map { rankingDto ->
            SManga.create().apply {
                title = rankingDto.name
                thumbnail_url = rankingDto.image?.let { img ->
                    when {
                        img.startsWith("http") -> img
                        else -> "${MediocreToons.CDN_URL}/obras/${rankingDto.id}/$img"
                    }
                }
                url = "/obra/${rankingDto.id}"
                description = ""
                status = SManga.UNKNOWN
                initialized = false
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = getRequestUrl("\$apiUrl/obras", PREF_URL_LATEST).toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()

        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false

        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = getRequestUrl("\$apiUrl/obras", PREF_URL_SEARCH).toHttpUrl().newBuilder()
            .addQueryParameter("limite", "20")
            .addQueryParameter("pagina", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("string", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is FormatoFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("formato", filter.selected)
                    }
                }
                is StatusFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selected)
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("ordenarPor", filter.selected)
                }
                else -> {}
            }
        }

        val finalUrl = url.build()
        return GET(finalUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()

        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false

        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================== Filters ================================
    override fun getFilterList() = FilterList(
        FormatoFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private class FormatoFilter : UriSelectFilter(
        "Formato",
        arrayOf(
            Pair("Todos", ""),
            Pair("Novel", "3"),
            Pair("Shoujo", "4"),
            Pair("Comic", "5"),
            Pair("Yaoi", "8"),
            Pair("Yuri", "9"),
            Pair("Hentai", "10"),
        ),
    )

    private class StatusFilter : UriSelectFilter(
        "Status",
        arrayOf(
            Pair("Todos", ""),
            Pair("Ativo", "1"),
            Pair("Em Andamento", "2"),
            Pair("Cancelada", "3"),
            Pair("Concluído", "4"),
            Pair("Hiato", "6"),
        ),
    )

    private class TagsFilter : Filter.Group<TagCheckBox>(
        "Tags",
        listOf(
            TagCheckBox("Ação", "2"),
            TagCheckBox("Aventura", "3"),
            TagCheckBox("Fantasia", "4"),
            TagCheckBox("Romance", "5"),
            TagCheckBox("Comédia", "6"),
            TagCheckBox("Drama", "7"),
            TagCheckBox("Terror", "8"),
            TagCheckBox("Horror", "9"),
            TagCheckBox("Suspense", "10"),
            TagCheckBox("Histórico", "11"),
            TagCheckBox("Vida escolar", "12"),
            TagCheckBox("Sobrenatural", "13"),
            TagCheckBox("Militar", "14"),
            TagCheckBox("Shounen", "15"),
            TagCheckBox("Shoujo", "16"),
            TagCheckBox("Josei", "17"),
            TagCheckBox("One-shot", "18"),
            TagCheckBox("Isekai", "19"),
            TagCheckBox("Retorno", "20"),
            TagCheckBox("Reencarnação", "21"),
            TagCheckBox("Sistema", "22"),
            TagCheckBox("Cultivo", "23"),
            TagCheckBox("Artes Marciais", "24"),
            TagCheckBox("Dungeon", "25"),
            TagCheckBox("Tragédia", "26"),
            TagCheckBox("Psicológico", "27"),
            TagCheckBox("Culinaria", "28"),
            TagCheckBox("Magia", "29"),
            TagCheckBox("SuperPoder", "30"),
            TagCheckBox("Murim", "31"),
            TagCheckBox("Necromante", "32"),
            TagCheckBox("Apocalipse", "33"),
            TagCheckBox("Seinen", "34"),
            TagCheckBox("Luta", "35"),
            TagCheckBox("máfia", "36"),
            TagCheckBox("Monstros", "37"),
            TagCheckBox("Esportes", "38"),
            TagCheckBox("Demônios", "39"),
            TagCheckBox("Ficção Científica", "40"),
            TagCheckBox("Fatia da Vida/Slice of Life", "41"),
            TagCheckBox("Ecchi", "42"),
            TagCheckBox("Mistério", "43"),
            TagCheckBox("Harém", "44"),
            TagCheckBox("manhua", "45"),
            TagCheckBox("Jogo", "46"),
            TagCheckBox("Regressão", "47"),
            TagCheckBox("+18", "48"),
            TagCheckBox("Oneshot", "49"),
            TagCheckBox("Yuri", "50"),
            TagCheckBox("Crime", "51"),
            TagCheckBox("Policial", "52"),
            TagCheckBox("Viagem no Tempo", "53"),
            TagCheckBox("Moderno", "54"),
        ),
    )

    private class SortFilter : UriSelectFilter(
        "Ordenar Por",
        arrayOf(
            Pair("Mais Recentes", "criada_em_desc"),
            Pair("Mais Populares", "view_geral"),
            Pair("A-Z", "nome"),
        ),
        defaultValue = 0,
    )

    private class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    private open class UriSelectFilter(
        displayName: String,
        private val options: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
        defaultValue,
    ) {
        val selected get() = options[state].second
    }

    // ============================ Manga Details ============================
    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.substringAfter("/obra/").substringBefore('/')
        val slug = manga.title.toSlug()
        val finalUrl = "$baseUrl/obra/$id"
        return finalUrl
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/obra/").substringBefore('/')
        val url = getRequestUrl("\$apiUrl/obras/%s", PREF_URL_MANGA_DETAILS, id)
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MediocreMangaDto>()
        return dto.toSManga(isDetails = true)
    }

    // ============================== Chapters ===============================
    override fun getChapterUrl(chapter: SChapter): String {
        val finalUrl = "$baseUrl${chapter.url}"
        return finalUrl
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MediocreMangaDto>()

        val chapters = manga.chapters
            .map { it.toSChapter() }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }

        return chapters
    }

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = getRequestUrl("\$apiUrl/capitulos/%s", PREF_URL_CHAPTER, chapterId)
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<MediocreChapterDetailDto>()
        val pages = dto.toPageList()
        return pages
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================== Settings ===============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "Email"
            summary = "Email para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Senha"
            summary = "Senha para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = API_URL_PREF
            title = "URL da API"
            summary = "URL base da API para acesso aos recursos da fonte. O padrão é https://api.mediocretoons.site, mas, dependendo da disponibilidade, a API pode alternar entre api2, api3, etc."
            setDefaultValue("https://api.mediocretoons.site")
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_CUSTOM_ALL_REQUESTS
            title = "Custom ALL REQUEST"
            summary = "Habilitar para customizar a URL de cada request da API. Quando ativo, as opções abaixo ficam disponíveis."
            setDefaultValue(false)
        }.also(screen::addPreference)

        if (preferences.getBoolean(PREF_CUSTOM_ALL_REQUESTS, false)) {
            listOf(
                Triple(PREF_URL_LOGIN, "URL Login", "\$apiUrl/auth/login"),
                Triple(PREF_URL_POPULAR, "URL Popular (ranking)", "\$apiUrl/obras/ranking"),
                Triple(PREF_URL_LATEST, "URL Latest (recentes)", "\$apiUrl/obras"),
                Triple(PREF_URL_SEARCH, "URL Search (obras)", "\$apiUrl/obras"),
                Triple(PREF_URL_MANGA_DETAILS, "URL Detalhes da obra", "\$apiUrl/obras/%s"),
                Triple(PREF_URL_CHAPTER, "URL Capítulo (páginas)", "\$apiUrl/capitulos/%s"),
            ).forEach { (prefKey, title, defaultUrl) ->
                EditTextPreference(screen.context).apply {
                    key = prefKey
                    this.title = title
                    summary = "Padrão: $defaultUrl. Use \$apiUrl para a URL da API ou informe URL completa."
                    setDefaultValue(defaultUrl)
                }.also(screen::addPreference)
            }
        }
    }
    companion object {
        const val CDN_URL = "https://cdn.mediocretoons.site"
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
        private const val API_URL_PREF = "api_url"
        private const val PREF_CUSTOM_ALL_REQUESTS = "custom_all_requests"
        private const val PREF_URL_LOGIN = "custom_url_login"
        private const val PREF_URL_POPULAR = "custom_url_popular"
        private const val PREF_URL_LATEST = "custom_url_latest"
        private const val PREF_URL_SEARCH = "custom_url_search"
        private const val PREF_URL_MANGA_DETAILS = "custom_url_manga_details"
        private const val PREF_URL_CHAPTER = "custom_url_chapter"
    }
}

private fun String.toSlug(): String {
    val noDiacritics = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val slug = noDiacritics.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (slug.isEmpty()) this.hashCode().toString() else slug
}
