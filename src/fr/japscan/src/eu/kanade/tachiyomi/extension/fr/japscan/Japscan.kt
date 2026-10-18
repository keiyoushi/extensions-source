package eu.kanade.tachiyomi.extension.fr.japscan

import android.content.ComponentName
import android.content.Intent
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Japscan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 2.seconds)

    // Sometimes an adblock blocker will pop up, preventing the user from opening
    // a cloudflare protected page
    override fun getHomeUrl() = "$baseUrl/catalogue?sort=populaire"

    override suspend fun getPopularManga(page: Int) = getCatalogue(page, "sort" to "populaire")

    override suspend fun getLatestUpdates(page: Int) = getCatalogue(page, "sort" to "nouveautes")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        return getCatalogue(page, "q" to query.trim())
    }

    private suspend fun getCatalogue(page: Int, param: Pair<String, String>): MangasPage {
        val catalogueUrl = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter(param.first, param.second)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(catalogueUrl).asJsoup()
        val mangas = document.select("a[data-card-slug]").map { element ->
            SManga.create().apply {
                url = mangaPath(element.attr("href"))
                title = element.selectFirst("h3")?.text() ?: element.selectFirst("img")!!.attr("alt")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("nav[aria-label=Pagination] a:contains(Suivant)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] !in CHAPTER_PATH_TYPES) return null
        val mangaUrl = mangaPath(url.encodedPath)
        return parseMangaDetails(client.get(baseUrl + mangaUrl).asJsoup()).apply {
            this.url = mangaUrl
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        val details = parseMangaDetails(document).apply {
            url = manga.url
            title = manga.title
        }
        val chapterList = document.select("ol[data-chapter-list] a[data-chapter-slug]").map(::parseChapter)
        return SMangaUpdate(details, chapterList)
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("main h1")!!.text()
        thumbnail_url = document.selectFirst("main img[alt]:not([alt=''])")?.absUrl("src")
        val info = document.select("main dl > div").associate { it.selectFirst("dt")!!.text() to it.selectFirst("dd")!!.text() }
        author = info["Auteur"]
        status = parseStatus(info["Statut"].orEmpty())
        genre = (listOfNotNull(info["Type"]?.substringAfter(' ')) + document.select("main a[href*='genre=']").map { it.text() })
            .joinToString()
        description = document.selectFirst("main h2:contains(Synopsis) + p")?.text()
    }

    private fun parseStatus(status: String) = status.lowercase().let {
        when {
            it.contains("en cours") -> SManga.ONGOING
            it.contains("terminé") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapter(element: Element) = SChapter.create().apply {
        // Chapter links point at the reader's first page (/manga/<slug>/<n>/1); keep the
        // pre-redesign /manga/<slug>/<n>/ form so existing library entries keep their read state.
        url = element.attr("href").substringBeforeLast('/') + "/"
        name = element.selectFirst("[data-ch-num]")!!.text()
        chapter_number = CHAPTER_NUM_REGEX.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("span.block.text-muted")?.text()?.substringBefore(" · "), PARIS)
    }

    private val pageListMutex = Mutex()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        pageListMutex.withLock {
            // Must be read before the first suspension point, while the caller's frames are on the stack.
            val isReader = Exception().stackTrace.any { it.className.contains("reader") }
            val firstPageUrl = baseUrl + chapter.url.trimEnd('/') + "/1"

            var document = client.get(firstPageUrl, CacheControl.FORCE_NETWORK).asJsoup()
            if (document.selectFirst(TURNSTILE_SELECTOR) != null) {
                askUserToVerify(firstPageUrl, isReader)
                document = client.get(firstPageUrl, CacheControl.FORCE_NETWORK).asJsoup()
            }

            val pages = document.select("select[data-page-select] option").mapIndexed { i, option ->
                Page(i, url = option.absUrl("value"))
            }
            if (pages.isEmpty()) throw Exception("Aucune page trouvée")
            return pages
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        document.selectFirst("img[data-page-img]")?.absUrl("src")?.takeIf(String::isNotEmpty)?.let { return it }
        if (document.selectFirst(TURNSTILE_SELECTOR) != null) {
            throw Exception("Session expirée : ouvrez le chapitre en WebView pour passer la vérification.")
        }
        throw Exception("Image introuvable")
    }

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers.newBuilder().set("Referer", page.url).build())
        .build()

    private suspend fun turnstilePresent(url: String): Boolean = client.get(url, CacheControl.FORCE_NETWORK).use {
        it.body.string().contains(TURNSTILE_MARKER)
    }

    // The reader is gated by a Turnstile check that sets a server-side session; the user passes it
    // once in the app's WebView, whose cookies the HTTP client shares.
    private suspend fun askUserToVerify(url: String, isReader: Boolean) {
        val context = applicationContext
        try {
            val intent = Intent().apply {
                component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("url_key", url)
                putExtra("source_key", id)
                putExtra("title_key", "Passez la vérification, puis fermez la WebView.")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Suwayomi etc.
            throw Exception("Passez la vérification de ce chapitre depuis la WebView et réouvrez-le.")
        }
        repeat(VERIFY_MAX_POLLS) {
            delay(VERIFY_POLL_INTERVAL)
            if (!turnstilePresent(url)) {
                val closeIntent = Intent().apply {
                    val targetClass = if (isReader) {
                        "eu.kanade.tachiyomi.ui.reader.ReaderActivity"
                    } else {
                        "eu.kanade.tachiyomi.ui.main.MainActivity"
                    }
                    component = ComponentName(context, targetClass)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(closeIntent)
                return
            }
        }
        throw Exception("Passez la vérification, fermez la WebView et réouvrez le chapitre.")
    }

    // Catalogue links are /<type>/<slug>; keep the pre-redesign trailing slash used by library entries.
    private fun mangaPath(href: String): String {
        val segments = baseUrl.toHttpUrl().resolve(href)!!.pathSegments.filter(String::isNotEmpty)
        return "/${segments[0]}/${segments[1]}/"
    }

    companion object {
        private val CHAPTER_PATH_TYPES = setOf("manga", "manhua", "manhwa", "bd", "comic", "comics", "global-manga")
        private val CHAPTER_NUM_REGEX = Regex("""(?i)chapitre\s+([\d.]+)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        private val PARIS = ZoneId.of("Europe/Paris")

        private const val TURNSTILE_MARKER = "cf-turnstile"
        private const val TURNSTILE_SELECTOR = ".cf-turnstile"
        private val VERIFY_POLL_INTERVAL = 5.seconds
        private const val VERIFY_MAX_POLLS = 24
    }
}
