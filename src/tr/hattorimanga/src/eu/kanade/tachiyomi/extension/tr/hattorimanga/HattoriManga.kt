package eu.kanade.tachiyomi.extension.tr.hattorimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class HattoriManga : ParsedHttpSource() {
    override val name: String = "Hattori Manga"

    override val baseUrl: String = "https://hattorimanga.com"

    override val lang: String = "tr"

    override val supportsLatest: Boolean = true
    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException()

    override val versionId: Int = 2

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .build()

    override fun chapterListSelector() =
        throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val slug = manga.url.substringAfterLast('/')
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val dto = fetchChapterPageableList(slug, page, manga)
            chapters += dto.chapters.map {
                SChapter.create().apply {
                    name = it.title
                    date_upload = it.date.toDate()
                    url = "${manga.url}/${it.chapterSlug}"
                }
            }
            page = dto.currentPage + 1
        } while (dto.hasNextPage())

        return Observable.just(chapters.sortedBy { it.name }.reversed())
    }

    private fun fetchChapterPageableList(slug: String, page: Int, manga: SManga): HMChapterDto =
        client.newCall(GET("$baseUrl/load-more-chapters/$slug?page=$page", headers))
            .execute()
            .parseAs<HMChapterDto>()

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapters")

    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page)).asObservableSuccess()
            .map {
                val mangas = it.parseAs<HMLatestUpdateDto>().chapters.map {
                    SManga.create().apply {
                        val manga = it.manga
                        title = manga.title
                        thumbnail_url = "$baseUrl/storage/${manga.thumbnail}"
                        setUrlWithoutDomain("$baseUrl/manga/${manga.slug}")
                    }
                }.distinctBy { it.title }

                MangasPage(mangas, false)
            }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h3")!!.text()
        thumbnail_url = document.selectFirst(".set-bg")?.absUrl("data-setbg")
        author = document.selectFirst(".anime-details-widget li span:contains(Yazar) + span")?.text()
        description = document.selectFirst(".anime-details-text p")?.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".image-wrapper img").mapIndexed { index, element ->
            Page(index, imageUrl = "$baseUrl${element.attr("data-src")}")
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Oturum açmanız, WebView'ı açmanız ve oturum açmanız gerekir")
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h5")!!.text()
        thumbnail_url = element.selectFirst(".img-con")?.absUrl("data-setbg")
        genre = element.select(".product-card-con ul li").joinToString { it.text() }
        val script = element.attr("onclick")
        setUrlWithoutDomain(REGEX_MANGA_URL.find(script)!!.groups.get("url")!!.value)
    }

    override fun popularMangaNextPageSelector() = ".pagination .page-item:last-child:not(.disabled)"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaSelector() = ".product-card.grow-box"

    override fun searchMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaSelector(): String {
        throw UnsupportedOperationException()
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    companion object {
        val REGEX_MANGA_URL = """='(?<url>[^']+)""".toRegex()
        val PREFIX_SEARCH = "slug:"
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    }
}
