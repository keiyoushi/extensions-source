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
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.api.get
import java.text.Normalizer

class MediocreToons :
    HttpSource(),
    ConfigurableSource {
    override val name = "Mediocre Toons"
    override val baseUrl = "https://mediocrescan.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val apiUrl = "https://back.mediocrescan.com"
    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        const val CDN_URL = "https://cdn.mediocrescan.com"
        private const val POPULAR_FORMATOS = "1,3,4,5,8,9,13"
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
    }

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(::authIntercept)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("x-app-key", "toons-mediocre-app")
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Authentication ===============================
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
            loginAndGetToken(email, password)
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
                .url("$apiUrl/auth/login")
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
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("limite", "24")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("temCapitulo", "true")
            .addQueryParameter("formato", POPULAR_FORMATOS)
            .addQueryParameter("ordenarPor", "view_geral")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false

        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/adicionados-recentes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("formato", "5")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<MediocreListDto<List<MediocreMangaSimpleDto>>>()
        val mangas = dto.data.map { it.toSManga() }
        val hasNext = dto.pagination?.hasNextPage ?: false

        return MangasPage(mangas, hasNextPage = hasNext)
    }

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("limite", "24")
            .addQueryParameter("pagina", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("string", query)
        }

        var formatoSelecionado = ""
        filters.forEach { filter ->
            when (filter) {
                is FormatoFilter -> formatoSelecionado = filter.selected
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

        val formato = if (formatoSelecionado.isNotEmpty()) formatoSelecionado else POPULAR_FORMATOS
        url.addQueryParameter("formato", formato)

        return GET(url.build(), headers)
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

    private class FormatoFilter :
        UriSelectFilter(
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

    private class StatusFilter :
        UriSelectFilter(
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

    private class SortFilter :
        UriSelectFilter(
            "Ordenar Por",
            arrayOf(
                Pair("Mais Recentes", "criada_em_desc"),
                Pair("Mais Populares", "view_geral"),
                Pair("A-Z", "nome"),
            ),
            defaultValue = 0,
        )

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
        return "$baseUrl/obra/$id"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/obra/")
        val url = "$apiUrl/obras/$id"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MediocreMangaDetailsDto>()
        return dto.toSManga()
    }

    // ============================== Chapters ===============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfter("/obra/")

        return Observable.fromCallable {
            val response = client.newCall(GET("$apiUrl/obras/$id", headers)).execute()
            val dto = response.parseAs<MediocreMangaDetailsDto>()
            val chapters = dto.chapters.map { it.toSChapter() }
            chapters.sortedByDescending { it.chapter_number }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "$apiUrl/capitulos/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val json = JSONObject(response.peekBody(Long.MAX_VALUE).string())

        val capUuid = json.optString("cap_uuid", "")
        val obraId = json.optJSONObject("obra")?.optInt("id", 0) ?: 0
        val capNum = json.optInt("cap_num", 0)

        if (capUuid.isEmpty() || obraId == 0) {
            return emptyList()
        }

        return fetchPagesFromCdn(obraId, capNum, capUuid)
    }

    private fun fetchPagesFromCdn(obraId: Int, capNum: Int, capUuid: String): List<Page> {
        return try {
            val pagesUrl = "$CDN_URL/obras/$obraId/capitulos/$capNum/$capUuid.json"

            val request = Request.Builder()
                .url(pagesUrl)
                .header("Referer", baseUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            val pagesArray = JSONArray(body)

            val pages = mutableListOf<Page>()
            for (i in 0 until pagesArray.length()) {
                val pageObj = pagesArray.getJSONObject(i)
                val pageUrl = pageObj.optString("url")
                if (pageUrl.isNotEmpty()) {
                    val fullUrl = "$CDN_URL/$pageUrl"
                    pages.add(Page(i, imageUrl = fullUrl))
                }
            }

            pages
        } catch (e: Exception) {
            emptyList()
        }
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
}

private fun String.toSlug(): String {
    val noDiacritics = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val slug = noDiacritics.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (slug.isEmpty()) this.hashCode().toString() else slug
}
