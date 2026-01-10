package eu.kanade.tachiyomi.extension.pt.mediocretoons

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
    private val apiUrl = "https://api.mediocretoons.site"

    private val preferences: SharedPreferences by getPreferencesLazy()

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
                .url("$apiUrl/usuarios/login")
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
        val url = "$apiUrl/obras/ranking".toHttpUrl().newBuilder()
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
        val url = "$apiUrl/obras/recentes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("formato", "5")
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
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
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
        val finalUrl = "$baseUrl/obra/$id/$slug"
        return finalUrl
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/obra/")
        val url = "$apiUrl/obras/$id"
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
        val url = "$apiUrl/capitulos/$chapterId"
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
    }

    companion object {
        const val CDN_URL = "https://cdn.mediocretoons.site"
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
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
