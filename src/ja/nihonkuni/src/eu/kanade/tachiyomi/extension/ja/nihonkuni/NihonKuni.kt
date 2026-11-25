package eu.kanade.tachiyomi.extension.ja.nihonkuni

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class NihonKuni : FMReader() {

    override val infoElementSelector = "div.manga-detail-container"
    override val mangaDetailsSelectorGenre = "li:has(.fa-tags) a.btn-danger"
    override val mangaDetailsSelectorDescription = ".description-text-content"
    override val mangaDetailsSelectorThumbnail = "img.manga-cover-image"
    override val altNameSelector = "li:has(.fa-clone) .info-field-value"
    override val altName = "Alternative Name: "

    // h1 title is base64 and uppercased, the breadcrumb keeps the list casing
    override fun parseMangaTitle(document: Document): String? = document.selectFirst(".breadcrumb-item.active")?.text()
        ?: document.selectFirst("h1.manga-main-title")
            ?.attr("data-enc")
            ?.let { String(Base64.decode(it, Base64.DEFAULT)) }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addCookie("smartlink_shown" to "1")

    // source is picky about URL format
    private fun mangaUrl(sortBy: String, page: Int) = "$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&magazine=&sort=$sortBy&sort_type=DESC"

    override fun popularMangaUrl(page: Int) = mangaUrl("views", page)

    override fun latestUpdatesUrl(page: Int) = mangaUrl("last_update", page)

    override fun popularMangaSelector() = "div.manga-grid div.manga-card"

    override fun popularMangaNextPageSelector() = ".page-link.next"

    override fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).first()?.hasAttr("href") ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst(".manga-title")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
        thumbnail_url = getImgAttr(element.selectFirst(".manga-cover"))
    }

    override fun getImgAttr(element: Element?): String? = when {
        element == null -> null

        element.hasAttr("data-original") -> element.attr("abs:data-original")

        element.hasAttr("data-src") -> element.attr("abs:data-src")

        element.hasAttr("data-bg") -> element.attr("abs:data-bg")

        element.hasAttr("data-srcset") -> element.attr("abs:data-srcset")

        element.hasAttr("style") -> element.attr("style").substringAfter("url(")
            .substringBefore(")").trim('\'', '"')

        else -> element.attr("abs:src")
    }

    // Map /manga/<slug>-<id>.html to the list's /manga-<slug>.html
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangaTitle = parseMangaTitle(document) ?: return null
        val slug = document.selectFirst(".h0rating[slug]")?.attr("slug")
        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(url.toString())
            slug?.let { this.url = "/manga-$it.html" }
            title = mangaTitle
        }
    }

    // Drop the SEO blurb, alt names and "Show more" around the synopsis
    override fun mangaDetailsParse(document: Document): SManga {
        document.select(".description-text-content > p, .description-text-content > div").remove()
        return super.mangaDetailsParse(document)
    }

    // Not abs:href, the page redirects under /manga/ and would change stored /read-*.html URLs
    override suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> = mangaPage.select("#chapters_raw_data > a").map {
        SChapter.create().apply {
            url = "/" + it.attr("href").removePrefix("/")
            name = it.selectFirst(".chapter-name")!!.text()
            date_upload = parseRelativeDate(it.attr("data-time").trim())
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("img[id~=page\\d+]")

        return images.mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }
}
