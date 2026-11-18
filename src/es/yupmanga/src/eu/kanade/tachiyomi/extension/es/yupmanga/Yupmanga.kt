package eu.kanade.tachiyomi.extension.es.yupmanga

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Yupmanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1)

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/top", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(client.get("$baseUrl/?page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.length < 3) {
            throw Exception("El término de búsqueda debe tener al menos 3 caracteres.")
        }
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        document.selectFirst("main > div.container > div[class^=bg-red]:has(p)")?.let {
            throw Exception("Límite de solicitudes alcanzado. Intente de nuevo en unos minutos.")
        }
        return parseSeriesList(document)
    }

    private fun parseSeriesList(document: Document): MangasPage {
        val mangas = document.selectFirst("div.grid:has(div.comic-card)")
            ?.select("div.comic-card")
            ?.mapNotNull { element ->
                val href = element.selectFirst("a[href]")?.attr("abs:href") ?: return@mapNotNull null
                val id = href.toHttpUrlOrNull()?.queryParameter("id") ?: return@mapNotNull null

                val title = element.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                SManga.create().apply {
                    this.title = title
                    url = id
                    thumbnail_url = element.selectFirst("img.object-cover")?.attr("abs:src")
                }
            } ?: emptyList()
        val hasNextPage = document.selectFirst("div.flex > a:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series.php?id=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga.url) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return manga.apply {
            val container = document.selectFirst("main > div.container")
                ?: throw Exception("No se pudo encontrar la información del manga")

            title = container.selectFirst("h1")?.text()
                ?: throw Exception("Título del manga no encontrado")
            description = MarkdownDescription.Builder()
                .description(container.selectFirst("p#synopsisText")?.text())
                .build()
            author = container.selectFirst("i[title=Autor] + span")?.text()
            status = container.selectFirst("span:has(i[title=Estado])").parseStatus()
            genre = container.select("a.genre-tag").joinToString { genre ->
                genre.text().replaceFirstChar { it.uppercase() }
            }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "activo" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "abandonado" -> SManga.CANCELLED
        "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()

        var page = 1
        do {
            val url = "$baseUrl/ajax/load_chapters.php".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", mangaId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("order", "newest_first")
                .build()

            val chapterListDto = client.get(url, apiHeaders, Fetch.XHR).parseAs<ChapterListDto>()

            val doc = Jsoup.parseBodyFragment(chapterListDto.html, baseUrl)
            allChapters.addAll(parseChapterList(doc, mangaId))

            page++
        } while (chapterListDto.hasNextPage())

        return allChapters
    }

    // A chapter is kept by its number key: the data-chapter id changes on each visit of the site
    private fun parseChapterList(document: Document, mangaId: String): List<SChapter> = document.select("div.comic-card").mapNotNull { element ->
        val chapterKey = element.selectFirst("a[data-chapter-key]")?.attr("data-chapter-key")
            ?.takeIf(CHAPTER_KEY_REGEX::matches)
            ?: return@mapNotNull null
        val chapterName = element.selectFirst("h3")?.text()
            ?: return@mapNotNull null

        SChapter.create().apply {
            name = chapterName
            url = "$chapterKey#$mangaId"
            chapter_number = chapterKey.toFloat()
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = if (chapter.url.startsWith("/ajax/")) {
            "$baseUrl${chapter.url}".toHttpUrlOrNull()?.queryParameter("s") ?: ""
        } else {
            chapter.url.substringAfter("#")
        }
        return "$baseUrl/series.php?id=$mangaId"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterKey = chapter.url.substringBefore("#")
        val seriesId = chapter.url.substringAfter("#", "")
        // Chapters listed before the site keyed them by number hold an id that it no longer accepts
        if (!CHAPTER_KEY_REGEX.matches(chapterKey) || seriesId.isEmpty()) throw Exception(OUTDATED)

        // Each visit of the series page renders anew the hidden element whose styles the challenge reads
        val seriesUrl = getChapterUrl(chapter)
        val seriesPage = client.get(seriesUrl, Fetch.DOCUMENT).body.string()
        val challenge = client.post("$baseUrl/ajax/get_challenge.php", apiHeaders, Fetch.XHR.from(seriesUrl), chapterForm(chapterKey, seriesId).build())
            .parseAs<ChallengeDto>()
        val challengeId = challenge.challengeId
        val challengeJs = challenge.challengeJs
        if (!challenge.success || challengeId == null || challengeJs == null) throw Exception("Error fetching challenge")

        val form = chapterForm(chapterKey, seriesId)
            .add("challenge_id", challengeId)
            .add("answer", solveChallenge(challengeJs, seriesUrl, seriesPage))
            .build()
        val response = client.post("$baseUrl/ajax/open_chapter.php", apiHeaders, Fetch.XHR.from(seriesUrl), form, ensureSuccess = false)
        val access = response.parseAs<TokenDto>()
        // Guests can only open a limited number of chapters
        if (response.code == 429) throw Exception(access.message ?: "Límite de lectura alcanzado")
        val token = access.token?.takeIf { access.success } ?: throw Exception(access.error ?: OUTDATED)
        val chapterId = access.chapterId ?: throw Exception(OUTDATED)

        val readerUrl = "$baseUrl/reader_v2.php".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chapterId)
            .addQueryParameter("token", token)
            .addQueryParameter("page", "1")
            .build()
        var reader = client.get(readerUrl, Fetch.DOCUMENT.from(seriesUrl)).asJsoup()
        // The reader is sometimes behind an ad page, which lets it through once its countdown is over
        if (reader.location().toHttpUrl().encodedPath == "/box-modal.php") {
            passInterstitial(reader)
            reader = client.get(readerUrl, Fetch.DOCUMENT.from(reader.location())).asJsoup()
        }

        // In 'Manga' mode only the first image is in the DOM, so the pages come from the inline config script
        val totalPages = reader.selectFirst("script:containsData(totalPages:)")?.data()
            ?.let { TOTAL_PAGES_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: throw Exception("No se pudo cargar el capítulo")
        val images = "$baseUrl/image-proxy-v2.php".toHttpUrl()
        return (1..totalPages).map { page ->
            val url = images.newBuilder()
                .addQueryParameter("chapter", chapterId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("token", token)
                .addQueryParameter("context", "reader")
                .build()
            Page(page - 1, imageUrl = url.toString())
        }
    }

    private fun chapterForm(chapterKey: String, seriesId: String) = FormBody.Builder()
        .add("chapter_key", chapterKey)
        .add("s", seriesId)

    // The ad page's ticket only counts once its countdown has run, which the site checks on its server
    private suspend fun passInterstitial(page: Document) {
        val html = page.html()
        val ticket = AD_TICKET_REGEX.find(html)?.groupValues?.get(1)?.parseAs<AdTicketDto>()
            ?: throw Exception("No se pudo cargar el capítulo")
        delay((COUNTDOWN_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 8).seconds)
        repeat(3) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("identifier", "chapter_entrance")
                .addFormDataPart("chapter", ticket.chapter)
                .addFormDataPart("nonce", ticket.nonce)
                .build()
            val response = client.post("$baseUrl/ajax/complete_ad_interstitial.php", apiHeaders, Fetch.XHR.from(page.location()), body, ensureSuccess = false)
            response.close()
            // 409: the server's clock hasn't reached the end of the countdown yet
            if (response.code != 409) return
            delay(1500.milliseconds)
        }
    }

    // One challenge WebView at a time
    private val challengeMutex = Mutex()

    // The challenge reads the computed styles of the series page's hidden elements, so it runs on that page
    private suspend fun solveChallenge(challengeJs: String, seriesUrl: String, seriesPage: String): String = challengeMutex.withLock {
        val bridge = "y" + Random.nextLong().toULong().toString(36)
        val script = "(${javaClass.getResource("/assets/challenge.js")!!.readText()})(${challengeJs.toJsonString()})" +
            ".then((answer) => $bridge.postMessage(answer), () => $bridge.postMessage(''))"
        val answer = try {
            runWebView<String> {
                interceptRequest { request ->
                    // Only the page's own inline styles matter: everything it loads gets an empty answer
                    if (request.isForMainFrame || request.url.host == null) {
                        null
                    } else {
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                webMessageBridge(bridge, setOf(baseUrl)) { resolve(it) }
                onPageFinished { evaluateJs(script) }
                loadData(seriesUrl, seriesPage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        answer.ifEmpty { throw Exception("Error solving challenge") }
    }

    companion object {
        private const val OUTDATED = "Información desactualizada. Refresque la lista de capítulos."
        private val CHAPTER_KEY_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val TOTAL_PAGES_REGEX = Regex("""totalPages:\s*(\d+)""")
        private val AD_TICKET_REGEX = Regex("""adTicket\s*=\s*(\{[^}]*\})""")
        private val COUNTDOWN_REGEX = Regex("""let\s+countdown\s*=\s*(\d+)""")
    }
}
