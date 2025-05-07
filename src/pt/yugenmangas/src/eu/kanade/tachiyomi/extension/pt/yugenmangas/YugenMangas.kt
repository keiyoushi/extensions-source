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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val defaultBaseUrl: String = "https://yugenmangasbr.nssec.xyz"

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
        val jsonContent = document.select("script")
            .map(Element::data)
            .firstOrNull(POPULAR_MANGA_REGEX::containsMatchIn)
            ?: throw Exception("Não foi possivel encontrar a lista de mangás/manhwas")

        val mangas = POPULAR_MANGA_REGEX.findAll(jsonContent)
            .mapNotNull { result ->
                result.groups.lastOrNull()?.value?.sanitizeJson()?.parseAs<JsonObject>()?.jsonObject
            }
            .map { element ->
                val manga = element["children"]?.jsonArray
                    ?.firstOrNull()?.jsonArray
                    ?.firstOrNull { it is JsonObject }?.jsonObject
                    ?.get("children")?.jsonArray
                    ?.firstOrNull { it is JsonObject }?.jsonObject

                SManga.create().apply {
                    title = manga!!.getValue("alt")
                    thumbnail_url = manga.getValue("src")
                    url = element.getValue("href")
                }
            }.toList()

        return MangasPage(mangas, jsonContent.hasNextPage(response))
    }

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/chapters?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val jsonContent = document.select("script")
            .map(Element::data)
            .firstOrNull(LATEST_UPDATE_REGEX::containsMatchIn)
            ?: throw Exception("Não foi possivel encontrar a lista de mangás/manhwas")

        val mangas = LATEST_UPDATE_REGEX.findAll(jsonContent)
            .mapNotNull { result ->
                result.groups.firstOrNull()?.value?.sanitizeJson()?.parseAs<JsonObject>()?.jsonObject
            }
            .map { element ->
                val jsonString = element.toString()
                SManga.create().apply {
                    this.title = jsonString.getFirstValueByKey("children")!!
                    thumbnail_url = jsonString.getFirstValueByKey("src")!!
                    url = element.getValue("href")
                }
            }.toList()

        return MangasPage(mangas, jsonContent.hasNextPage())
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

    override fun mangaDetailsParse(response: Response): SManga {
        return getJsonFromResponse(response).parseAs<ContainerDto>().series.toSManga()
    }

    // ================================ Chapters =======================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val response = client.newCall(chapterListRequest(manga, page++)).execute()
            val chapterContainer = getJsonFromResponse(response).parseAs<ContainerDto>()
            chapters += chapterContainer.toSChapterList()
        } while (chapterContainer.hasNext())

        return Observable.just(chapters)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .addQueryParameter("reverse", "true")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // ================================ Pages =======================================}

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select("script")
            .map(Element::data)
            .firstOrNull(PAGES_REGEX::containsMatchIn)
            ?: throw Exception("Páginas não encontradas")

        val jsonContent = PAGES_REGEX.find(script)?.groups?.get(1)?.value?.sanitizeJson()
            ?: throw Exception("Erro ao obter as páginas")

        return json.decodeFromString<List<String>>(jsonContent).mapIndexed { index, imageUrl ->
            Page(index, baseUrl, "$BASE_MEDIA/$imageUrl")
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

    private fun String.getFirstValueByKey(field: String) =
        """$field":"([^"]+)""".toRegex().find(this)?.groups?.get(1)?.value

    private fun String.hasNextPage(): Boolean =
        LATEST_PAGES_REGEX.findAll(this).lastOrNull()?.groups?.get(1)?.value?.toBoolean()?.not() ?: false

    private fun String.hasNextPage(response: Response): Boolean {
        val lastPage = POPULAR_PAGES_REGEX.findAll(this).mapNotNull {
            it.groups[1]?.value?.toInt()
        }.max() - 1

        return response.request.url.queryParameter("page")
            ?.toInt()?.let { it < lastPage } ?: false
    }

    private fun String.sanitizeJson() =
        this.replace("""\\{1}"""".toRegex(), "\"")
            .replace("""\\{2,}""".toRegex(), """\\""")
            .trimIndent()

    private fun JsonObject.getValue(key: String): String =
        this[key]!!.jsonPrimitive.content

    private fun getJsonFromResponse(response: Response): String {
        val document = response.asJsoup()

        val script = document.select("script")
            .map(Element::data)
            .firstOrNull(MANGA_DETAILS_REGEX::containsMatchIn)
            ?: throw Exception("Dados não encontrado")

        val jsonContent = MANGA_DETAILS_REGEX.find(script)
            ?.groups?.get(1)?.value
            ?: throw Exception("Erro ao obter JSON")

        return jsonContent.sanitizeJson()
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL da fonte"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"
        private const val URL_PREF_SUMMARY = "Para uso temporário, se a extensão for atualizada, a alteração será perdida."
        private const val BASE_MEDIA = "https://media.yugenweb.com"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val POPULAR_MANGA_REGEX = """\d+\\",(\{\\"href\\":\\"\/series\/.*?\]\]\})""".toRegex()
        private val LATEST_UPDATE_REGEX = """\{\\"href\\":\\"\/series\/\d+(.*?)\}\]\]\}\]\]\}\]\]\}""".toRegex()
        private val LATEST_PAGES_REGEX = """aria-disabled\\":([^,]+)""".toRegex()
        private val POPULAR_PAGES_REGEX = """series\?page=(\d+)""".toRegex()
        private val MANGA_DETAILS_REGEX = """(\{\\"series\\":.*?"\})\],\[""".toRegex()
        private val PAGES_REGEX = """images\\":(\[[^\]]+\])""".toRegex()
    }
}
