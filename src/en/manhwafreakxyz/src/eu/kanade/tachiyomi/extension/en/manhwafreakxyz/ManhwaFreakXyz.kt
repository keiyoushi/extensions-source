package eu.kanade.tachiyomi.extension.en.manhwafreakxyz

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhwaFreakXyz : Madara(
    "ManhwaFreak.xyz",
    "https://manhwafreak.xyz",
    "en",
) {
    // ===================== Popular ============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/$mangaSubString/${searchPage(page)}".toHttpUrl().newBuilder()
            .addQueryParameter("post_type", "wp-manga")
            .addQueryParameter("s", "")
            .addQueryParameter("sort", "most_viewed")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div[class*=unit item]"

    override val popularMangaUrlSelector = ".info a"

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            element.selectFirst("img:not(.flag-icon)")?.let {
                thumbnail_url = imageFromElement(it)
            }
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (nonce.isBlank()) {
            nonce = response.peekBody().let(::findNonceValue)
        }
        return super.popularMangaParse(response)
    }

    private fun Response.peekBody(): Document =
        Jsoup.parseBodyFragment(peekBody(Long.MAX_VALUE).string())

    override fun popularMangaNextPageSelector() = ".navigation .page-item:last-child:not(.disabled)"

    // ===================== Latest ============================

    override fun latestUpdatesRequest(page: Int): Request {
        val request = popularMangaRequest(page)
        val url = request.url.newBuilder()
            .setQueryParameter("sort", "recently_added")
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    // ===================== Search ============================

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        if (nonce.isBlank()) {
            nonce = findNonceValue()
        }

        val form = FormBody.Builder()
            .add("action", "live_search")
            .add("search", query)
            .add("nonce", nonce)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchDto = json.decodeFromStream<SearchDto>(response.body.byteStream())
        val mangas = searchDto.mangas.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.thumbnail
                setUrlWithoutDomain(it.url)
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    private var nonce: String = ""

    private fun findNonceValue(document: Document? = null): String {
        val dom = document ?: client.newCall(popularMangaRequest(1)).execute().asJsoup()
        return dom.select("script")
            .map(Element::data)
            .firstOrNull { it.contains("'nonce','") }
            ?.substringAfter("'nonce','")
            ?.substringBefore("'") ?: ""
    }

    // ===================== Manga Details ============================

    override val mangaDetailsSelectorTitle = ".serie-title"
    override val mangaDetailsSelectorAuthor = ".stat-label:contains(Author) + .stat-value"
    override val mangaDetailsSelectorArtist = ".stat-label:contains(Artist) + .stat-value"
    override val mangaDetailsSelectorStatus = ".stat-label:contains(Status) + .manga"
    override val mangaDetailsSelectorDescription = ".description-content"
    override val mangaDetailsSelectorThumbnail = ".main-cover img.cover"
    override val mangaDetailsSelectorGenre = ".genre-list .genre-link"

    // ===================== Chapters ============================

    override fun chapterListSelector() = ".list-body-hh li"

    override fun chapterDateSelector() = "a > span:not(:has(i))"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            name = name.split(" ")
                .take(2)
                .joinToString(" ")
        }
    }

    // ===================== Pages ============================

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select("canvas.manga-canvas").mapIndexed { index, canvas ->
            val imageUrl = canvas.attr("data-src")
                .let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
            Page(index, document.location(), imageUrl)
        }
    }

    override fun getFilterList() = FilterList()
}
