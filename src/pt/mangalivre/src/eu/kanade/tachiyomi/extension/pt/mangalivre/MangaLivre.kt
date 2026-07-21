package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.webkit.WebSettings
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    HttpSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest: Boolean = true

    private val decryptor = MangaLivreDecryptor(baseUrl, network.client, headers)

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ReadingGateInterceptor(baseUrl, headers["User-Agent"], network.client, decryptor))
        .rateLimit(2, 1.seconds) { it.host == baseUrlHost }
        .build()

    private val apiUrl: String = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    private val webViewUserAgent: String? by lazy {
        runCatching { WebSettings.getDefaultUserAgent(applicationContext) }
            .getOrNull()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        webViewUserAgent?.takeIf { it.isNotBlank() }?.let { set("User-Agent", it) }
        add("Accept", "*/*")
        add("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
        add("Referer", "$baseUrl/")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
    }

    // ============================== Popular =======================================

    private val popularFilter = FilterList(
        listOf(
            OrderByFilter(options = listOf("" to SORT_POPULAR)),
            OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
        ),
    )

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest =======================================

    private val latestFilter = FilterList(
        listOf(
            OrderByFilter(options = listOf("" to SORT_UPDATED)),
            OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
        ),
    )

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    url.addQueryParameter("sortBy", filter.selected())
                }
                is OrderDirectionFilter -> {
                    url.addQueryParameter("sortOrder", filter.selected())
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = WrapperDto.fromJson(response.parseJson())
        val mangas = dto.mangas.map { it.toSManga(useAlternativeTitle) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga-by-slug/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = MangaDto.fromJson(response.parseJson()).toSManga(useAlternativeTitle)

    // ============================== Chapters =======================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = MangaDto.fromJson(response.parseJson()).toSChapterList()

    // ============================== Pages =======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val readerPath = chapter.url.substringBeforeLast("#")
        val ref = ChapterReferenceDto.fromJson(chapter.url.substringAfterLast("#"))
        return GET("$apiUrl/mangas/${ref.mangaId}/chapters/${ref.chapterId}", headers)
            .newBuilder()
            .tag(ReadingGateInterceptor.ReaderPath::class.java, ReadingGateInterceptor.ReaderPath(readerPath))
            .build()
    }

    override fun pageListParse(response: Response): List<Page> = PageDto.fromJson(response.parseJson()).toPageList()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters =======================================

    override fun getFilterList(): FilterList = FilterList(
        listOf(
            OrderByFilter(
                "Ordem",
                listOf(
                    "Mais Visualizados" to SORT_POPULAR,
                    "Lançamentos" to SORT_RELEASE,
                    "Última Atualização" to SORT_UPDATED,
                    "Melhor Avaliação" to SORT_RATING,
                    "A-Z" to SORT_TITLE,
                ),
            ),
            Filter.Separator(),
            OrderDirectionFilter(
                "Direção",
                listOf(
                    "↑ Decrescente" to DIRECTION_DESC,
                    "↓ Crescente" to DIRECTION_ASC,
                ),
            ),
        ),
    )

    val useAlternativeTitle: Boolean get() =
        preferences.getBoolean(ALTERNATIVE_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_TITLE_PREF
            title = "Titulo alternativo"
            summary = buildString {
                append("Use titulos alternativos como principal quando disponivel.")
                append(" Essa opção não tem efeito sobre obras já adicionadas na sua biblioteca")
            }
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Utilities =======================================

    private fun Response.parseJson(): JsonObject {
        val responseBody = body.string().trimStart()
        if (responseBody.isEmpty() || responseBody.startsWith("<")) {
            close()
            throw IOException(NON_JSON_MESSAGE)
        }
        return runCatching { Json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { throw IOException(NON_JSON_MESSAGE, it) }
    }

    companion object {
        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val NON_JSON_MESSAGE =
            "Resposta não-JSON (Cloudflare ou header desatualizado). Abra a fonte na WebView do app e tente de novo."

        private const val SORT_POPULAR = "popular"
        private const val SORT_RELEASE = "release"
        private const val SORT_UPDATED = "updated"
        private const val SORT_RATING = "rating"
        private const val SORT_TITLE = "title"
        private const val DIRECTION_DESC = "desc"
        private const val DIRECTION_ASC = "asc"
    }
}
