package eu.kanade.tachiyomi.multisrc.greenshit

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.jvm.Synchronized

abstract class GreenShit :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    abstract val apiUrl: String
    abstract val cdnApiUrl: String
    abstract val cdnUrl: String
    abstract val scanId: String

    protected open val emailPreferenceKey = "email"
    protected open val passwordPreferenceKey = "password"

    protected open val rateLimitPerSecond = 2
    protected open val defaultGenreId = "1"
    protected open val limitPerPage = "26"

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(rateLimitPerSecond)
            .addInterceptor(::authIntercept)
            .build()
    }

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)

        val token = getValidToken() ?: return chain.proceed(request)
        val response = chain.proceed(request.withAuth(token))

        if (response.code != 401) return response
        response.close()
        val newToken = clearTokenAndRefresh()
        return chain.proceed(request.withAuth(newToken.orEmpty()))
    }

    private fun Request.withAuth(token: String): Request = if (token.isNotEmpty()) {
        newBuilder().header("Authorization", "Bearer $token").build()
    } else {
        this
    }

    @Synchronized
    private fun clearTokenAndRefresh(): String? {
        cachedToken = null
        tokenExpiryTime = 0L
        return getValidToken()
    }

    @Synchronized
    private fun getValidToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime) {
            return cachedToken
        }
        return fetchNewToken()
    }

    @Synchronized
    private fun fetchNewToken(): String? {
        return try {
            val email = preferences.getString(emailPreferenceKey, "")
            val password = preferences.getString(passwordPreferenceKey, "")
            if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
                return null
            }
            return loginAndGetToken(email, password)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    protected open fun loginAndGetToken(email: String, password: String): String? {
        try {
            val body = GreenShitLoginRequestDto(
                login = email.trim(),
                senha = password,
                tipoUsuario = "usuario",
            ).toJsonString().toRequestBody("application/json".toMediaType())

            val headers = headersBuilder().set("Accept", "application/json").build()

            val response = network.cloudflareClient.newCall(
                POST("$apiUrl/auth/login", headers, body),
            ).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val auth = response.parseAs<GreenShitLoginResponseDto>()
            val token = auth.accessToken
            val expiresIn = auth.expiresIn * 1000
            cachedToken = token
            tokenExpiryTime = System.currentTimeMillis() + expiresIn
            return token
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .set("scan-id", scanId)

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("tipo", "visualizacoes_geral")
            .addQueryParameter("limite", limitPerPage)
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("gen_id", defaultGenreId)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingList = response.parseAs<GreenShitListDto<List<GreenShitMangaDto>>>()
        val mangas = rankingList.obras.map { rankingDto -> rankingDto.toSManga(cdnApiUrl) }
        return MangasPage(mangas, hasNextPage = rankingList.hasNextPage)
    }

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/atualizacoes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", limitPerPage)
            .addQueryParameter("gen_id", defaultGenreId)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<GreenShitListDto<List<GreenShitMangaDto>>>()
        val mangas = dto.obras.map { it.toSManga(cdnApiUrl) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage)
    }

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras/search".toHttpUrl().newBuilder()
            .addQueryParameter("limite", limitPerPage)
            .addQueryParameter("pagina", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("obr_nome", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GeneroFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("gen_id", filter.selected)
                    } else {
                        url.addQueryParameter("todos_generos", "1")
                    }
                }

                is FormatoFilter -> url.addQueryParameterIfNotEmpty("formt_id", filter.selected)

                is StatusFilter -> url.addQueryParameterIfNotEmpty("stt_id", filter.selected)

                is SortFilter -> url.addQueryParameterIfNotEmpty("orderBy", getSortFilterOptions()[filter.state].second)

                is TagsFilter -> filter.state.filter { it.state }.forEach { tag ->
                    url.addQueryParameter("tag_ids", tag.value)
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<GreenShitListDto<List<GreenShitMangaDto>>>()
        val mangas = dto.obras.map { it.toSManga(cdnApiUrl) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage)
    }

    // ============================== Filters ================================
    override fun getFilterList() = FilterList(
        GeneroFilter(getGeneroFilterOptions()),
        FormatoFilter(getFormatoFilterOptions()),
        StatusFilter(getStatusFilterOptions()),
        TagsFilter(getTagsFilterOptions()),
        SortFilter(getSortFilterOptions(), getSortFilterDefaultValue()),
    )

    /** Opções do filtro Gênero; sobrescreva para customizar na extensão. */
    protected open fun getGeneroFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Hentais", "5"),
        Pair("Livres", "1"),
        Pair("Mangás", "8"),
        Pair("Novel", "6"),
        Pair("Shoujo / Romances", "4"),
        Pair("Yaoi", "7"),
    )

    /** Opções do filtro Formato; sobrescreva para customizar na extensão. */
    protected open fun getFormatoFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Mangá", "3"),
        Pair("Manhua", "2"),
        Pair("Manhwa", "1"),
        Pair("Novel", "4"),
    )

    /** Opções do filtro Status; sobrescreva para customizar na extensão. */
    protected open fun getStatusFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Cancelado", "4"),
        Pair("Concluído", "2"),
        Pair("Em Andamento", "1"),
        Pair("Hiato", "3"),
    )

    /** Opções do filtro Ordenar; sobrescreva para customizar na extensão. */
    protected open fun getSortFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Última atualização", "ultima_atualizacao"),
        Pair("Lançamentos", "criacao"),
        Pair("Mais Visualizadas", "visualizacoes_geral"),
        Pair("Melhor avaliação", "rating"),
        Pair("A-Z", "nome"),
    )

    /** Índice padrão do filtro Ordenar; sobrescreva para customizar na extensão. */
    protected open fun getSortFilterDefaultValue(): Int = 0

    /** Lista de tags do filtro; sobrescreva para customizar na extensão. */
    protected open fun getTagsFilterOptions(): List<TagCheckBox> = listOf(
        TagCheckBox("Ação", "1"),
        TagCheckBox("Aventura", "2"),
        TagCheckBox("Comédia", "3"),
        TagCheckBox("Drama", "4"),
        TagCheckBox("Fantasia", "5"),
        TagCheckBox("Terror", "6"),
        TagCheckBox("Mistério", "7"),
        TagCheckBox("Romance", "8"),
        TagCheckBox("Sci-Fi", "9"),
        TagCheckBox("Slice of Life", "10"),
        TagCheckBox("Esportes", "11"),
        TagCheckBox("Thriller", "12"),
        TagCheckBox("Sobrenatural", "13"),
        TagCheckBox("Histórico", "14"),
        TagCheckBox("Mecha", "15"),
        TagCheckBox("Psicológico", "16"),
        TagCheckBox("Seinen", "17"),
        TagCheckBox("Shoujo", "18"),
        TagCheckBox("Shounen", "19"),
        TagCheckBox("Josei", "20"),
        TagCheckBox("Isekai", "21"),
        TagCheckBox("Artes Marciais", "22"),
        TagCheckBox("Gore", "23"),
        TagCheckBox("Yuri", "24"),
        TagCheckBox("Yaoi", "25"),
        TagCheckBox("Escolar", "26"),
        TagCheckBox("Animais", "27"),
        TagCheckBox("Apocalipse", "28"),
        TagCheckBox("Adulto", "29"),
        TagCheckBox("Boys", "30"),
        TagCheckBox("Bullying", "31"),
        TagCheckBox("Construção", "32"),
        TagCheckBox("Crime", "33"),
        TagCheckBox("Culinária", "34"),
        TagCheckBox("Demônios", "35"),
        TagCheckBox("Ecchi", "36"),
        TagCheckBox("Esporte", "37"),
        TagCheckBox("Estratégia", "38"),
        TagCheckBox("Família", "39"),
        TagCheckBox("Fatos Reais", "40"),
        TagCheckBox("Fazenda", "41"),
        TagCheckBox("Ficção Científica", "42"),
        TagCheckBox("Guerra", "43"),
        TagCheckBox("Hárem", "44"),
        TagCheckBox("Horror", "45"),
        TagCheckBox("Jogo", "46"),
        TagCheckBox("Linha do Tempo", "47"),
        TagCheckBox("Luta", "48"),
        TagCheckBox("Máfia", "49"),
        TagCheckBox("Magia", "50"),
        TagCheckBox("Monstros", "51"),
        TagCheckBox("Murim", "52"),
        TagCheckBox("Musculação", "53"),
        TagCheckBox("Necromante", "54"),
        TagCheckBox("Overpower", "55"),
        TagCheckBox("Pets", "56"),
        TagCheckBox("Realidade Virtual", "57"),
        TagCheckBox("Reencarnação", "58"),
        TagCheckBox("Regressão", "59"),
        TagCheckBox("Religião", "60"),
        TagCheckBox("Sistema", "61"),
        TagCheckBox("Super Poderes", "62"),
        TagCheckBox("Suspense", "63"),
        TagCheckBox("Tela de Sistema", "64"),
        TagCheckBox("Tragédias", "65"),
        TagCheckBox("Vida Escolar", "66"),
        TagCheckBox("Vingança", "67"),
        TagCheckBox("Violência", "68"),
        TagCheckBox("Volta no Tempo", "69"),
    )

    protected open class GeneroFilter(options: Array<Pair<String, String>>) : UriSelectFilter("Gênero", options)

    protected open class FormatoFilter(options: Array<Pair<String, String>>) : UriSelectFilter("Formato", options)

    protected open class StatusFilter(options: Array<Pair<String, String>>) : UriSelectFilter("Status", options)

    protected open class TagsFilter(tags: List<TagCheckBox>) : Filter.Group<TagCheckBox>("Tags", tags)

    protected open class SortFilter(
        options: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : UriSelectFilter("Ordenar Por", options, defaultValue)

    protected class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    protected open class UriSelectFilter(
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
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/obras/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<GreenShitMangaDto>()
        return dto.toSManga(cdnApiUrl, isDetails = true)
    }

    // ============================== Chapters ================================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<GreenShitMangaDto>()
        return manga.chapters
            .map { it.toSChapter() }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/capitulo/")
        return GET("$apiUrl/capitulos/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<GreenShitChapterDetailDto>()
        return dto.toPageList(cdnUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ===============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = emailPreferenceKey
            title = "Email"
            summary = "Email para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = passwordPreferenceKey
            title = "Senha"
            summary = "Senha para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    fun HttpUrl.Builder.addQueryParameterIfNotEmpty(name: String, value: String?): HttpUrl.Builder {
        if (!value.isNullOrEmpty()) {
            addQueryParameter(name, value)
        }
        return this
    }
}
