package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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
                val url = request.url

                if (url.fragment?.startsWith("token=") == true) {
                    val token = url.fragment!!.substringAfter("token=")

                    val cookie = Cookie.parse(url, "tr_token=$token; path=/")
                    client.cookieJar.saveFromResponse(url, listOfNotNull(cookie))

                    val response = chain.proceed(request)
                    return@Interceptor decoder.decrypt(response)
                }

                chain.proceed(request)
            },
        )
        .rateLimit(3, 1)
        .build()

    private val decoder = Decoder(client, baseUrl)

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Popular =================================

    override fun popularMangaSelector() = ".manga-grid .lt-item-node"
    override val popularMangaUrlSelector = ".card-title a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst(popularMangaUrlSelector)!!.absUrl("href"))
    }

    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".lt-pill-items a"
    override val mangaDetailsSelectorDescription = ".lt-text-desc p"
    override val mangaDetailsSelectorAuthor = ".lt-meta-grid .attr-item:contains(AUTOR) .attr-value"
    override val mangaDetailsSelectorArtist = ".lt-meta-grid .attr-item:contains(ARTISTA) .attr-value"
    override val mangaDetailsSelectorStatus = ".lt-meta-grid .attr-item:contains(STATUS) .attr-value"

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
        name = element.selectFirst("span.lt-ch-lbl")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".lt-ch-dt")?.text())
        // The source chapter list is out of order, so extract the number here for later sorting
        CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.last()?.toFloatOrNull()?.let {
            chapter_number = it
        }
        setUrlWithoutDomain(element.selectFirst("a.lt-ch-href")!!.absUrl("href"))
    }

    // =============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        launchIO { countViews(doc) }

        val imageUrls = decoder.getImageUrls(doc)

        return imageUrls.mapIndexed { idx: Int, url: String ->
            Page(idx, imageUrl = url)
        }
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
    }
}
