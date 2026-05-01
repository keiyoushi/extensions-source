package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTek : HttpSource() {
    override val name = "MangaTek"
    override val baseUrl = "https://mangatek.com"
    override val lang = "ar"

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale("ar"))

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("p.text-base")?.text()
            genre = document
                .selectFirst("p > span:contains(التصنيفات:) + span")
                ?.text()?.replace("،", ",")
            status = document.selectFirst(".flex span.border.rounded")?.text().toStatus()
            thumbnail_url = document.selectFirst("img#mangaCover")?.imgAttr()
            author = document
                .selectFirst("p > span:contains(المؤلف:) + span")
                ?.ownText()
                ?.takeIf { it != "Unknown" }
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")

        val props = response.asJsoup()
            .selectFirst("astro-island[component-url*=MangaChaptersLoader]")
            ?.attr("props") ?: return emptyList()

        val data = props.parseAs<MangaWrapper>()
        val chapters = data.manga.value.mangaChapters.value.map { it.value }

        return chapters.map { ch ->
            SChapter.create().apply {
                name = ch.title.value?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.chapterNumber.value}"
                url = "/reader/$seriesSlug/${ch.chapterNumber.value}"
                date_upload = dateFormat.tryParse(ch.createdAt.value)
            }
        }
    }

    // Page
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".manga-page img").mapIndexed { i, element ->
            Page(i, imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }
}
