package eu.kanade.tachiyomi.extension.pt.yugenmangas

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.PREF_KEY_RANDOM_UA
import eu.kanade.tachiyomi.lib.randomua.RANDOM_UA_VALUES
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

class YugenMangas : HttpSource(), ConfigurableSource {

    override val name = "Yugen Mangás"

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!
    }

    private val defaultBaseUrl: String = "https://yugenmangasbr.yocat.xyz"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy {
        if (getPrefUAType() != UserAgentType.OFF || getPrefCustomUA().isNullOrBlank().not()) {
            return@getPreferencesLazy
        }
        edit().putString(PREF_KEY_RANDOM_UA, RANDOM_UA_VALUES.last()).apply()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    override val versionId = 2

    private val json: Json by injectLazy()

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/series?page=$page&order=desc&sort=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(initialSeries)")?.data()
            ?: throw Exception(warning)

        val json = POPULAR_MANGA_REGEX.find(script)?.groups?.get(1)?.value
            ?.replace(ESCAPE_QUOTATION_MARK_REGEX, "\"")
            ?: throw Exception("Erro ao analisar lista de mangás/manhwas")
        val dto = json.parseAs<LibraryWrapper>()
        return MangasPage(dto.mangas.map(MangaDetailsDto::toSManga), dto.hasNextPage())
    }

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/chapters?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.bg-card a[href*=series]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.attrImageSet()
                setUrlWithoutDomain(element.absUrl("href").substringBeforeLast("/"))
            }
        }.takeIf(List<SManga>::isNotEmpty) ?: throw Exception(warning)
        return MangasPage(mangas, document.selectFirst("a[aria-label='Próxima página']:not([aria-disabled='true'])") != null)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = json.encodeToString(SearchDto(query)).toRequestBody(JSON_MEDIA_TYPE)
        return POST("$baseUrl/api/search", headers, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<SearchMangaDto>().series.map(MangaDto::toSManga)
        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Details =======================================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("[property='og:description']")?.attr("content")
        thumbnail_url = document.selectFirst("img")?.attrImageSet()
        author = document.selectFirst("p:contains(Autor) ~ div")?.text()
        artist = document.selectFirst("p:contains(Artista) ~ div")?.text()
        genre = document.select("p:contains(Gêneros) ~ div div.inline-flex").joinToString { it.text() }
    }

    // ================================ Chapters =======================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val response = client.newCall(chapterListRequest(manga, page++)).execute()
            val chapterGroup = chapterListParse(response).also {
                chapters += it
            }
        } while (chapterGroup.isNotEmpty())

        return Observable.just(chapters)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .addQueryParameter("reverse", "true")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a.flex.bg-card[href*=series]").map { element ->
            SChapter.create().apply {
                name = element.selectFirst("p")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
    }

    // ================================ Pages =======================================}

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[alt^=página]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = URL_PREF_SUMMARY

            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "URL padrão:\n$defaultBaseUrl"

            setDefaultValue(defaultBaseUrl)

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    // ================================ Utils =======================================

    private fun Element?.attrImageSet(): String? {
        return this?.attr("srcset")?.split(SRCSET_DELIMITER_REGEX)
            ?.map(String::trim)?.last(String::isNotBlank)
            ?.let { "$baseUrl$it" }
    }

    private val warning = """
        Não foi possível localizar a lista de mangás/manhwas.
        Tente atualizar a URL acessando: Extensões > $name > Configurações.
        Isso talvez resolva o problema.
    """.trimIndent()

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL da fonte"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"
        private const val URL_PREF_SUMMARY = "Para uso temporário, se a extensão for atualizada, a alteração será perdida."
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val POPULAR_MANGA_REGEX = """(\{\\"initialSeries.+\"\})\]""".toRegex()
        private val ESCAPE_QUOTATION_MARK_REGEX = """\\"""".toRegex()
        private val SRCSET_DELIMITER_REGEX = """\d+w,?""".toRegex()
    }
}
