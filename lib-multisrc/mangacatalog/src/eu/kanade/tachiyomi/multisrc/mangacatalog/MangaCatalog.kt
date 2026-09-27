package eu.kanade.tachiyomi.multisrc.mangacatalog

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Based On the original manga maniac source
// MangaCatalog is a network of sites for single franshise sites

abstract class MangaCatalog : KeiSource() {

    open val sourceList = listOf(
        Pair(name, baseUrl),
    ).sortedBy { it.first }.distinctBy { it.second }

    // Info

    override val supportsLatest = false

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(sourceList.map { popularMangaFromPair(it.first, it.second) }, false)

    private fun popularMangaFromPair(name: String, sourceurl: String): SManga = SManga.create().apply {
        title = name
        url = sourceurl
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = sourceList.filter {
            it.first.contains(query, ignoreCase = true)
        }.map {
            popularMangaFromPair(it.first, it.second)
        }
        return MangasPage(mangas, false)
    }

    // Manga and chapter URLs are stored as absolute URLs
    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    // Details

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.select("div.bg-bg-secondary > div.px-6 > div.flex-col").text()
        title = document.select("div.container > h1").text()
        description = if ("Description" in info) info.substringAfter("Description").trim() else info
        thumbnail_url = document.select("div.flex > img").attr("abs:src")
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

    private fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector()).map { chapterFromElement(it) }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

    open fun pageListParse(document: Document): List<Page> = document.select("img[data-src]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:data-src")) }
}
