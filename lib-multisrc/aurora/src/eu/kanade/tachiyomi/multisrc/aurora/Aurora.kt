package eu.kanade.tachiyomi.multisrc.aurora

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Source
abstract class Aurora : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)
        .addCookie("mnx_adulto" to "1")

    override fun Headers.Builder.configureHeaders() = set("Alt-Used", baseUrl.toHttpUrl().host)

    override suspend fun getPopularManga(page: Int) = getMangasPage(client.get("$baseUrl/catalogo", Fetch.DOCUMENT))

    override suspend fun getLatestUpdates(page: Int) = getMangasPage(client.get("$baseUrl/novidades", Fetch.DOCUMENT))

    private fun getMangasPage(response: Response): MangasPage {
        val dto = response.extractNextJs<SeriesDto>()
        return MangasPage(dto?.toSMangaList() ?: emptyList(), false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(page).mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || segments.size < 2 || segments[1].isEmpty()) return null
        val mangaUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(segments[0])
            .addPathSegment(segments[1])
            .build()
        return client.get(mangaUrl, Fetch.DOCUMENT, ensureSuccess = false).use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw HttpException(response.code)
                else -> response.asJsoup().seriesDto()?.toSManga()?.apply { initialized = true }
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = entryURL(manga.memo)

    override fun getChapterUrl(chapter: SChapter) = "${entryURL(chapter.memo)}/${chapter.memo["number"]!!.string}"

    private fun entryURL(memo: JsonObject): String = "$baseUrl/${memo["type"]!!.string}/${memo["slug"]!!.string}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        val manga = getSMangaDetails(document, manga)
        val chapters = document.extractNextJs<ChapterListDto>()
            ?.toSChapterList(manga)
            ?: emptyList()

        return SMangaUpdate(manga, chapters)
    }

    private val mangaDetailsDescriptionRegex = """description":"([^"]+)""".toRegex()
    private val mangaDetailsGenreRegex = """genre":([^]]+])""".toRegex()
    private val mangaDetailsAuthorRegex = """author[^.]+name":"([^"]+)""".toRegex()
    private val cleanRegex = """\\{2,}""".toRegex()

    private fun getSMangaDetails(
        document: Document,
        manga: SManga,
    ): SManga = document.seriesDto()
        ?.toSManga()
        ?: manga.apply {
            document.selectFirst("script:containsData(ComicSeries)")?.data()
                ?.replace(cleanRegex, "")
                ?.let {
                    description = MarkdownDescription.Builder()
                        .description(mangaDetailsDescriptionRegex.find(it)?.groupValues?.last())
                        .build()
                    genre = mangaDetailsGenreRegex.find(it)?.groupValues?.last()?.parseAs<List<String>>()?.joinToString()
                    author = mangaDetailsAuthorRegex.find(it)?.groupValues?.last()
                }
        }

    private fun Document.seriesDto() = selectFirst("script[type]:containsData(ComicSeries)")?.data()?.parseAs<MangaDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val entryUrl = entryURL(chapter.memo)
        val rscHeaders = headersBuilder()
            .set("rsc", "1")
            .set("next-url", entryUrl.toHttpUrl().encodedPath)
            .build()
        val pages = client.get(chapterUrl, rscHeaders, Fetch.XHR.from(entryUrl)).extractNextJs<PagesDto>()
            ?: unlockChapter(chapterUrl, entryUrl, rscHeaders)

        return pages?.toPageList { encodedUrl ->
            decrypt(encodedUrl, getKey(encodedUrl, chapterUrl))
        } ?: emptyList()
    }

    // Following the gate link's redirects (link.amazon affiliate chain) failed the chapter
    private val noRedirectClient by lazy { client.newBuilder().followRedirects(false).build() }

    // The server only checks that the gate link was requested and that GATE_WAIT has passed since the start
    private suspend fun unlockChapter(chapterUrl: String, entryUrl: String, rscHeaders: Headers): PagesDto? {
        val startedAt = TimeSource.Monotonic.markNow()
        val gate = client.post(
            "$baseUrl/api/gate/start",
            Fetch.XHR.from(chapterUrl),
            GateStartDto(chapterUrl.toHttpUrl().encodedPath).toJsonRequestBody(),
        ).parseAs<GateResponseDto>().data
        noRedirectClient.get(gate.gateUrl, Fetch.DOCUMENT.from(chapterUrl), ensureSuccess = false).close()

        val callbackUrl = "$baseUrl/gate/callback".toHttpUrl().newBuilder()
            .addQueryParameter("token", gate.token)
            .build()
        delay(GATE_WAIT - startedAt.elapsedNow())
        repeat(GATE_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(GATE_RETRY_DELAY)
            val response = client.get(callbackUrl, rscHeaders, Fetch.XHR.from(entryUrl))
            val error = response.request.url.queryParameter("gate_error")
                ?: return response.extractNextJs<PagesDto>()
            response.close()
            if (error != "too_fast") throw Exception("Chapter gate failed: $error")
        }
        throw Exception("Chapter gate failed: too_fast")
    }

    private var key: KeyDto? = null

    private suspend fun getKey(payload: String, chapterUrl: String): String {
        val (v, e) = getParams(payload)
        key?.takeIf { it.v == v && it.e == e }?.let { return it.k }
        return client.get("$baseUrl/api/atfield/key?v=$v&e=$e", Fetch.XHR.from(chapterUrl)).parseAs<KeyDto>()
            .also { key = it }
            .k
    }

    companion object {
        // minWaitSeconds says 0, but callbacks before ~10 s after the start get gate_error=too_fast
        private val GATE_WAIT = 10.seconds
        private val GATE_RETRY_DELAY = 2.seconds
        private const val GATE_ATTEMPTS = 3
    }
}
