package eu.kanade.tachiyomi.extension.pt.mangalivre

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
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

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ReadingGateInterceptor(baseUrl, headers["User-Agent"]))
        .rateLimit(2, 1.seconds) { it.host == baseUrlHost }
        .build()

    private val apiUrl: String = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "*/*")
        .add("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "same-origin")

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
        val dto = response.parseJson<WrapperDto>()
        val mangas = dto.mangas.map { it.toSManga(useAlternativeTitle) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga-by-slug/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseJson<MangaDto>().toSManga(useAlternativeTitle)

    // ============================== Chapters =======================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseJson<MangaDto>().toSChapterList()

    // ============================== Pages =======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val readerPath = chapter.url.substringBeforeLast("#")
        val ref = chapter.url.substringAfterLast("#").parseAs<ChapterReferenceDto>()
        return GET("$apiUrl/mangas/${ref.mangaId}/chapters/${ref.chapterId}", headers)
            .newBuilder()
            .tag(ReadingGateInterceptor.ReaderPath::class.java, ReadingGateInterceptor.ReaderPath(readerPath))
            .build()
    }

    override fun pageListParse(response: Response): List<Page> = response.parseJson<PageDto>().toPageList()

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

    private inline fun <reified T> Response.parseJson(): T {
        val peek = peekBody(MAX_PEEK).string().trimStart()
        if (peek.isEmpty() || peek.startsWith("<")) {
            close()
            throw IOException(NON_JSON_MESSAGE)
        }
        return parseAs<T>()
    }

    companion object {
        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val MAX_PEEK = 1024L
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
