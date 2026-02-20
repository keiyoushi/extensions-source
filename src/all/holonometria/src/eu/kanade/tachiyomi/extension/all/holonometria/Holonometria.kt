package eu.kanade.tachiyomi.extension.all.holonometria

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Holonometria(
    override val lang: String,
    private val langPath: String = "$lang/",
) : ParsedHttpSource() {

    override val name = "HOLONOMETRIA"

    override val baseUrl = "https://holoearth.com"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/${langPath}alt/holonometria/manga/", headers)

    override fun popularMangaSelector() = ".manga__item"
    override fun popularMangaNextPageSelector() = null

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.select(".manga__title").text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/${langPath}alt/holonometria/manga/#${query.trim()}", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val search = response.request.url.fragment!!

        val entries = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)
            .filter { it.title.contains(search, true) }

        return MangasPage(entries, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".alt-nav__met-sub-link.is-current").text()
        thumbnail_url = document.select(".manga-detail__thumb img").attr("abs:src")
        description = document.select(".manga-detail__caption").text()
        val info = document.select(".manga-detail__person").html().split("<br>")
        author = info.firstOrNull { desc -> manga.any { desc.contains(it, true) } }
            ?.substringAfter("：")
            ?.substringAfter(":")
            ?.trim()
            ?.replace("&amp;", "&")
        artist = info.firstOrNull { desc -> script.any { desc.contains(it, true) } }
            ?.substringAfter("：")
            ?.substringAfter(":")
            ?.trim()
            ?.replace("&amp;", "&")
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/${manga.url}", headers)

    override fun chapterListSelector() = ".manga-detail__list .manga-detail__list-item"

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.select(".manga-detail__list-title").text()
        date_upload = dateFormat.tryParse(element.selectFirst(".manga-detail__list-date")?.text())
    }

    override fun pageListParse(document: Document): List<Page> = document.select(".manga-detail__swiper-wrapper img").mapIndexed { idx, img ->
        Page(idx, "", img.attr("abs:src"))
    }.reversed()

    companion object {
        private val manga = listOf("manga", "gambar", "漫画")
        private val script = listOf("script", "naskah", "脚本")

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd", Locale.ENGLISH)
        }
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
