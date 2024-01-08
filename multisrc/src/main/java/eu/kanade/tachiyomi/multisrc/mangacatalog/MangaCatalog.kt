package eu.kanade.tachiyomi.multisrc.mangacatalog

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

// Based On the original manga maniac source
// MangaCatalog is a network of sites for single franshise sites

abstract class MangaCatalog(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    open val sourceList = listOf(
        Pair("$name", "$baseUrl"),
    ).sortedBy { it.first }.distinctBy { it.second }

    // Info

    override val supportsLatest: Boolean = false

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(sourceList.map { popularMangaFromPair(it.first, it.second) }, false))
    }
    private fun popularMangaFromPair(name: String, sourceurl: String): SManga = SManga.create().apply {
        title = name
        url = sourceurl
    }
    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")
    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")
    override fun popularMangaSelector(): String = throw Exception("Not used")
    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")
    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")
    override fun latestUpdatesSelector(): String = throw Exception("Not used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val mangas = mutableListOf<SManga>()
        sourceList.map {
            if (it.first.contains(query)) {
                mangas.add(popularMangaFromPair(it.first, it.second))
            }
        }
        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("Not used")
    override fun searchMangaNextPageSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element) = throw Exception("Not used")

    // Get Override

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.select("div.bg-bg-secondary > div.px-6 > div.flex-col").text()
        title = document.select("div.container > h1").text()
        description = if ("Description" in info) info.substringAfter("Description").trim() else info
        thumbnail_url = document.select("div.flex > img").attr("src")
    }
    // Chapters

    override fun chapterListSelector(): String = "div.w-full > div.bg-bg-secondary > div.grid"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val name1 = element.select(".col-span-4 > a").text()
        val name2 = element.select(".text-xs:not(a)").text()
        if (name2 == "") {
            name = name1
        } else {
            name = "$name1 - $name2"
        }
        url = element.select(".col-span-4 > a").attr("abs:href")
        date_upload = System.currentTimeMillis()
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> =
        document.select(".js-pages-container img.js-page,.img_container img").mapIndexed { index, img ->
            Page(index, "", img.attr("src"))
        }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
}
