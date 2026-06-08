package eu.kanade.tachiyomi.multisrc.mangacatalog

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

// Based On the original manga maniac source
// MangaCatalog is a network of sites for single franshise sites

abstract class MangaCatalog(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    open val sourceList = listOf(
        Pair(name, baseUrl),
    ).sortedBy { it.first }.distinctBy { it.second }

    // Info

    override val supportsLatest: Boolean = false

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(sourceList.map { popularMangaFromPair(it.first, it.second) }, false),
    )

    private fun popularMangaFromPair(name: String, sourceurl: String): SManga = SManga.create().apply {
        title = name
        url = sourceurl
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val mangas = sourceList.filter {
            it.first.contains(query, ignoreCase = true)
        }.map {
            popularMangaFromPair(it.first, it.second)
        }
        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // Get Override

    override fun mangaDetailsRequest(manga: SManga): Request = GET(manga.url, headers)
    override fun chapterListRequest(manga: SManga): Request = GET(manga.url, headers)
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val info = document.select("div.bg-bg-secondary > div.px-6 > div.flex-col").text()
            title = document.select("div.container > h1").text()
            description = if ("Description" in info) info.substringAfter("Description").trim() else info
            thumbnail_url = document.select("div.flex > img").attr("abs:src")
        }
    }

    // Chapters

    open fun chapterListSelector(): String = "div.w-full > div.bg-bg-secondary > div.grid"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val name1 = element.select(".col-span-4 > a").text()
        val name2 = element.select(".text-xs:not(a)").text()
        name = if (name2.isEmpty()) {
            name1
        } else {
            "$name1 - $name2"
        }
        url = element.select(".col-span-4 > a").attr("abs:href")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[data-src]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:data-src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
