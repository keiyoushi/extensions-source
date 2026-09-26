package eu.kanade.tachiyomi.extension.en.readcomicsonline

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.multisrc.mmrcms.UriFilter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ReadComicsOnline : MMRCMS() {

    override val itemPath = "comic"

    override val chapterString = ""

    override val fetchFilterOptions = false

    override val detailsTitleSelector = "h1.text-2xl"

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    override fun popularMangaUrl(page: Int) = "$baseUrl/comic-list?sort=views&page=$page"

    override fun popularMangaSelector(): String = "div.comic-list-layout .grid > .group"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst("a.block.text-sm.font-semibold")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        title = anchor.text()
        thumbnail_url = guessCover(url, element.selectFirst("img")?.attr("src"))
    }

    override fun popularMangaNextPageSelector(): String? = "nav a[rel=next]"

    override fun latestUpdatesUrl(page: Int) = "$baseUrl/comic-list?sort=latest&page=$page"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector(): String = "div a:has(img)"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val resDoc = client.get(searchMangaUrl(page, query, filters)).asJsoup()
        val mangas = resDoc.select(searchMangaSelector()).map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("p")!!.text()
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = resDoc.selectFirst("span a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList): String = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("advanced-search")
        addQueryParameter("name", query)
        addQueryParameter("page", page.toString())
        filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
    }.build().toString()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.text-2xl")?.text() ?: ""
        thumbnail_url = guessCover(document.location(), document.selectFirst("img.w-full.rounded-xl")?.imgAttr())
        description = document.selectFirst("p.mt-5.text-sm")?.text() ?: ""

        document.selectFirst("div.flex.flex-wrap.gap-2 span.rounded-full")?.let {
            status = when (it.text().lowercase()) {
                in detailStatusComplete -> SManga.COMPLETED
                in detailStatusOngoing -> SManga.ONGOING
                in detailStatusDropped -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
        genre = document.select("dl div:contains(Genres:) a").joinToString { it.text() }
        author = document.select("div:has(span:contains(Author:)) > a").joinToString { it.text() }
    }

    override fun chapterListSelector(): String = ".overflow-hidden.border-ink-600 > a"

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val chapterName = element.selectFirst(".text-brand-400")?.text() ?: element.text()
        name = cleanChapterName(mangaTitle, chapterName)

        date_upload = dateFormat.tryParseDate(element.selectFirst(".text-slate-500")?.text())
    }
    override fun pageListParse(document: Document): List<Page> = document.select("#reader-all img").mapIndexed { i, img ->
        Page(i, imageUrl = img.imgAttr())
    }
}
