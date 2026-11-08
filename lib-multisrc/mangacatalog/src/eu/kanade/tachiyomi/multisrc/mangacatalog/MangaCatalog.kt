package eu.kanade.tachiyomi.multisrc.mangacatalog

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Based On the original manga maniac source
// MangaCatalog is a network of sites for single franshise sites

abstract class MangaCatalog : KeiSource() {

    open val sourceList = listOf(
        name to baseUrl,
    ).sortedBy { it.first }.distinctBy { it.second }

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(sourceList.map { popularMangaFromPair(it.first, it.second) }, false)

    private fun popularMangaFromPair(name: String, sourceurl: String): SManga = SManga.create().apply {
        title = name
        url = sourceurl
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Romaji titles are only in the slugs
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val slugQuery = query.trim().replace(' ', '-')
        val mangas = sourceList.filter { (name, mangaUrl) ->
            name.contains(query, ignoreCase = true) ||
                mangaUrl.toHttpUrl().pathSegments.getOrNull(1)?.contains(slugQuery, ignoreCase = true) == true
        }.map { (name, mangaUrl) ->
            popularMangaFromPair(name, mangaUrl)
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val path = url.encodedPath.removeSuffix("/")
        return sourceList.firstOrNull { it.second.toHttpUrl().encodedPath.removeSuffix("/") == path }
            ?.let { (name, mangaUrl) -> popularMangaFromPair(name, mangaUrl) }
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
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("div.bg-bg-secondary > div.px-6 > div.flex-col")?.textOrNull()
        title = document.selectFirst("div.container > h1")!!.text()
        description = info?.substringAfter("Description")?.trim()
        thumbnail_url = document.selectFirst("div.flex > img")?.attrOrNull("abs:src")
    }

    open fun chapterListSelector(): String = "div.w-full > div.bg-bg-secondary > div.grid"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst(".col-span-4 > a")!!
        val subtitle = element.selectFirst(".text-xs:not(a)")?.textOrNull()
        name = listOfNotNull(link.text(), subtitle).joinToString(" - ")
        url = link.absUrl("href")
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector()).map { chapterFromElement(it) }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup())

    // First page in src, the lazy-loaded rest in data-src
    open fun pageListParse(document: Document): List<Page> = document.select("img.js-page:not(noscript img), img.pages__img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.attrOrNull("abs:data-src") ?: img.absUrl("src")) }
}
