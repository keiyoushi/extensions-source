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
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashSet
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    HttpSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.client.newBuilder()
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

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        runBlocking {
            getPageListWithWebView(chapter)
        }
    }

    private suspend fun getPageListWithWebView(
        chapter: SChapter,
    ): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val ref = chapterUrl.fragment!!.parseAs<ChapterReferenceDto>()
        val chapterNumber = chapterUrl.pathSegments.last { it.isNotEmpty() }
        val readerUrl = chapterUrl.newBuilder().fragment(null).build().toString()
        val imageUrls = Collections.synchronizedSet(LinkedHashSet<String>())
        val bridgeName = (1..(10..20).random())
            .map { (('a'..'z') + ('A'..'Z')).random() }
            .joinToString("")
        val collectImageUrlsScript = collectImageUrlsScript(bridgeName)

        fun collect(rawUrl: String) {
            val imageUrl = rawUrl.toCdnImageUrl() ?: return
            if (!imageUrl.isChapterImage(ref.mangaId, chapterNumber)) return
            imageUrls.add(imageUrl)
        }

        try {
            return runWebView(timeout = WEBVIEW_TIMEOUT) {
                var previousCount = 0
                var stablePolls = 0

                javaScriptEnabled = true
                domStorageEnabled = true

                interceptRequest { request ->
                    collect(request.url.toString())
                    null
                }
                jsBridge(bridgeName) { payload ->
                    payload.parseAs<List<String>>().forEach(::collect)
                }
                onPageFinished {
                    evaluateJs(collectImageUrlsScript)
                }
                poll(1.seconds) {
                    evaluateJs(collectImageUrlsScript)
                    val currentCount = imageUrls.size
                    if (currentCount > 0 && currentCount == previousCount) {
                        stablePolls++
                    } else {
                        stablePolls = 0
                    }
                    previousCount = currentCount
                    if (stablePolls >= STABLE_POLLS) {
                        resolve(imageUrls.toPageList())
                    }
                }
                loadUrl(readerUrl)
            }
        } catch (error: WebViewTimeoutException) {
            if (imageUrls.isNotEmpty()) {
                return imageUrls.toPageList()
            }
            throw error
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

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
        private const val STABLE_POLLS = 3
        private val WEBVIEW_TIMEOUT = 90.seconds
        private const val CDN_HOST = "cdn.toonlivre.net"
        private const val PROXY_HOST = "slightly-free-mayfly.edgecompute.app"
        private val PAGE_NUMBER_REGEX = Regex("""_(\d+)\.[^.]+$""")

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

    private fun collectImageUrlsScript(bridgeName: String) =
        """
        (() => {
            const urls = new Set();
            document.querySelectorAll('img').forEach((image) => {
                [image.currentSrc, image.src, image.dataset.src].forEach((url) => {
                    if (url) urls.add(url);
                });
            });
            performance.getEntriesByType('resource').forEach((entry) => urls.add(entry.name));
            $bridgeName.post(JSON.stringify(Array.from(urls)));
        })();
        """.trimIndent()

    private fun String.toCdnImageUrl(): String? {
        val url = toHttpUrlOrNull() ?: return null
        val candidate = when (url.host) {
            CDN_HOST -> url
            PROXY_HOST -> url.queryParameter("url")?.toHttpUrlOrNull()
            else -> null
        } ?: return null

        return candidate.takeIf { it.isHttps && it.host == CDN_HOST }?.toString()
    }

    private fun String.isChapterImage(mangaId: String, chapterNumber: String): Boolean {
        val pathSegments = toHttpUrl().pathSegments
        return pathSegments.size >= 4 &&
            pathSegments[0] == "obras" &&
            pathSegments[1] == mangaId &&
            pathSegments[2] == chapterNumber &&
            pathSegments[3].isNotEmpty()
    }

    private fun Set<String>.toPageList(): List<Page> = synchronized(this) {
        val sortedUrls = sortedWith(
            compareBy<String>({ it.pageNumber() ?: Int.MAX_VALUE }, { it }),
        )
        sortedUrls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun String.pageNumber(): Int? = toHttpUrl().pathSegments.lastOrNull()
        ?.let(PAGE_NUMBER_REGEX::find)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}
