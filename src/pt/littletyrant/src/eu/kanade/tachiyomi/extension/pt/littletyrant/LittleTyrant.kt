package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class LittleTyrant :
    Madara(
        "Little Tyrant",
        "https://tiraninha.world",
        "pt-BR",
        dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
    ) {

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val xorKey = request.header("X-Internal-XOR-Key")

                if (xorKey != null) {
                    val newRequest = request.newBuilder()
                        .removeHeader("X-Internal-XOR-Key")
                        .build()

                    val response = chain.proceed(newRequest)
                    return@Interceptor decoder.decrypt(response, xorKey)
                }

                chain.proceed(request)
            },
        )
        .rateLimit(3, 1)
        .build()

    private val decoder by lazy { Decoder() }

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Popular =================================

    override fun popularMangaSelector() = "[id*=manga-item-]"
    override val popularMangaUrlSelector = ".card-title a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst(popularMangaUrlSelector)!!.absUrl("href"))
    }

    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".mc-genres-pills a"
    override val mangaDetailsSelectorDescription = ".mc-description-box"
    override val mangaDetailsSelectorAuthor = ".mc-meta-grid .attr-item:has(.attr-label:contains(AUTOR)) .attr-value"
    override val mangaDetailsSelectorArtist = ".mc-meta-grid .attr-item:has(.attr-label:contains(ARTISTA)) .attr-value"
    override val mangaDetailsSelectorStatus = ".mc-meta-grid .attr-item:has(.attr-label:contains(STATUS)) .attr-value"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        author = author?.replace(COMMA_REGEX, ", ")?.takeUnless { it.contains("---") }
        artist = artist?.replace(COMMA_REGEX, ", ")?.takeUnless { it.contains("---") }
    }

    // =============================== Chapters =================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()
        val mangaId = document.selectFirst("a.wp-manga-action-button")!!.attr("data-post")
        val chapters = mutableListOf<SChapter>()
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        var offset = 0
        do {
            val form = FormBody.Builder()
                .add("action", "load_more_chapters")
                .add("manga_id", mangaId)
                .add("offset", offset.toString())
                .build()
            offset += 12
            val dto = client.newCall(POST(url, headers, form)).execute().parseAs<ChapterDto>()
            val chapterElements = dto.toJsoup(baseUrl).select(chapterListSelector())
            chapters += chapterElements.map(::chapterFromElement)
        } while (!dto.isEmpty())

        chapters.sortedByDescending(SChapter::chapter_number)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("span.mc-chapter-title")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".mc-chapter-date")?.text())
        // The source chapter list is out of order, so extract the number here for later sorting
        CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.last()?.toFloatOrNull()?.let {
            chapter_number = it
        }
        setUrlWithoutDomain(element.selectFirst("a.mc-chapter-link")!!.absUrl("href"))
    }

    // =============================== Pages =================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter))
        .asObservableSuccess()
        .flatMap { response ->
            val doc = response.asJsoup()
            val chapterUrl = doc.baseUri()
            launchIO { countViews(doc) }

            val timestamp = System.currentTimeMillis()
            val fetchHeaders = headers.newBuilder()
                .set("Accept", "*/*")
                .set("Referer", chapterUrl)
                .set("Sec-Fetch-Dest", "empty")
                .set("Sec-Fetch-Mode", "cors")
                .set("Sec-Fetch-Site", "same-origin")
                .set("X-Reader-Sec", "tiraninha-web")
                .build()

            val request = GET("$baseUrl/wp-content/themes/madara2/gatekeeper.php?t=$timestamp", fetchHeaders)

            client.newCall(request).asObservableSuccess().map { tokenResponse ->
                if (!tokenResponse.isSuccessful) {
                    tokenResponse.close()
                    error("Could not load pages (error ${tokenResponse.code}).")
                }

                val token = tokenResponse.parseAs<TokenDto>().token
                val parts = token.split(".")
                if (parts.size < 2) error("Invalid JWT token format")
                val xorKey = parts[1].substring(4, 20)

                val paths = decoder.extractPaths(doc)
                paths.mapIndexed { idx: Int, path: String ->
                    Page(idx, url = chapterUrl, imageUrl = "$baseUrl$path#key=$xorKey&token=$token")
                }
            }
        }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // =============================== Images =================================

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!
        val chapterUrl = page.url

        val fragment = url.substringAfter("#", "")
        val key = fragment.substringAfter("key=").substringBefore("&")
        val token = fragment.substringAfter("token=").substringBefore("&")

        val cleanUrl = url.substringBefore("#")
        val httpUrl = cleanUrl.toHttpUrl()

        val cookie = okhttp3.Cookie.parse(httpUrl, "lt_sec_val=$token; path=/")
        client.cookieJar.saveFromResponse(httpUrl, listOfNotNull(cookie))

        val imageHeaders = headers.newBuilder()
            .set("Accept", "*/*")
            .set("Referer", chapterUrl)
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "same-origin")
            .set("X-Reader-Sec", "tiraninha-web")
            .set("X-Internal-XOR-Key", key)
            .build()

        return GET(cleanUrl, imageHeaders)
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
        private val COMMA_REGEX = """,\s*""".toRegex()
    }
}
