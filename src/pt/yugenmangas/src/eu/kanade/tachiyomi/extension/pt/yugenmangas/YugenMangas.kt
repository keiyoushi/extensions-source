package eu.kanade.tachiyomi.extension.pt.yugenmangas

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.PREF_KEY_RANDOM_UA
import keiyoushi.lib.randomua.RANDOM_UA_VALUES
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.addRandomUAPreferenceToScreen
import keiyoushi.lib.randomua.getPrefCustomUA
import keiyoushi.lib.randomua.getPrefUAType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class YugenMangas :
    HttpSource(),
    ConfigurableSource {

    override val name = "Yugen Mangás"

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!
    }

    private val defaultBaseUrl: String = "https://yugenmangasbr.dxtg.online"

    private val apiUrl: String = "https://api.yugenweb.com"

    private val imageBaseUrl: String get() = "$baseUrl/_next/image?url=$apiUrl/media"

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
        .rateLimitHost(apiUrl.toHttpUrl(), 2)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    override val versionId = 2

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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca?page=$page&sort_order=desc&sort_by=total_views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.getJsonBody().parseAs<LibraryWrapper<MangaDto>>()
        return MangasPage(dto.mangas.map { it.toSManga(imageBaseUrl) }, dto.hasNextPage())
    }

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/capitulos?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.getJsonBody().parseAs<LibraryWrapper<LatestUpdateDto>>()
        return MangasPage(dto.mangas.map { it.toSManga(imageBaseUrl) }, dto.hasNextPage())
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/v2/library/series".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("per_page", "10")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<Library<MangaDto>>()
        return MangasPage(dto.series.map { it.toSManga(imageBaseUrl) }, dto.hasNextPage())
    }

    // ================================ Details =======================================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("[property='og:description']")?.attr("content")
        thumbnail_url = document.selectFirst("img")?.attrImageSet()
        author = document.selectFirst("p:contains(Autor) + p")?.text()
        artist = document.selectFirst("p:contains(Artista) + p")?.text()
        genre = document.select("div:has(h1) + div + div [data-slot='badge']").joinToString { it.text() }
        document.selectFirst("div:has(h1) + div [data-slot='badge']:last-child")?.let {
            status = when (it.text().lowercase()) {
                "em lançamento" -> SManga.ONGOING
                "finalizada" -> SManga.COMPLETED
                "em hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
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
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("""div.md\:hidden a[href*=reader]""")
            .distinctBy { it.absUrl("href") }
            .map { element ->
                SChapter.create().apply {
                    name = element.selectFirst("p")!!.text()
                    element.selectFirst("span:has( > .lucide-calendar)")?.let {
                        date_upload = parseRelativeDate(it.text())
                    }
                    setUrlWithoutDomain(element.absUrl("href"))
                }
            }
    }

    // ================================ Pages =======================================}

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.getJsonBody().parseAs<List<PageDto>>()
        return pages.sortedBy(PageDto::number).mapIndexed { index, src ->
            Page(index, imageUrl = "$apiUrl/media/${src.path}")
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

    private fun Element?.attrImageSet(): String? = this?.attr("srcset")?.split(SRCSET_DELIMITER_REGEX)
        ?.map(String::trim)?.last(String::isNotBlank)
        ?.let { "$baseUrl$it" }

    private fun Response.getJsonBody(): String {
        val document = asJsoup()
        val script = document.select("script").map(Element::data)
            .firstOrNull { script -> GET_JSON_BODY_REGEX.containsMatchIn(script) }
            ?: throw Exception(warning)
        val values = GET_JSON_BODY_REGEX.find(script)?.groupValues?.filter(String::isNotBlank)
        return values?.last()
            ?.let { "\"$it\"".parseAs<String>() }
            ?: throw Exception("Erro ao analisar os dados")
    }

    private fun parseRelativeDate(date: String): Long {
        val number = DATE_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("mês") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("dia") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hora") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            else -> 0
        }
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
        private val MANGA_REGEX = """(\{\\"initialData.+\"\}.+)(?:\]\}){2}\]""".toRegex()
        private val PAGES_REGEX = """pages\\":(\[.+\])\}\}""".toRegex()
        private val GET_JSON_BODY_REGEX = """$MANGA_REGEX|$PAGES_REGEX""".toRegex()
        private val DATE_REGEX = """\d+""".toRegex()
        private val SRCSET_DELIMITER_REGEX = """\d+w,?""".toRegex()
    }
}
