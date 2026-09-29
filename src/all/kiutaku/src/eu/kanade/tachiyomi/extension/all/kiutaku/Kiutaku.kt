package eu.kanade.tachiyomi.extension.all.kiutaku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Kiutaku : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/hot".toHttpUrl().newBuilder()
            .addQueryParameter("start", getPage(page).toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("start", getPage(page).toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("start", getPage(page).toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("div.blog > div.items-row").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("nav > a.pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.item-link")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")?.ifEmpty { null }
        title = element.selectFirst("h2")!!.text()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.firstOrNull() ?: return null
        val document = client.get("$baseUrl/$id").asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain("$baseUrl/$id")
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        title = document.selectFirst("div.article-header")!!.text()
        genre = document.selectFirst("div.article-tags")
            ?.select("a.tag > span")
            ?.eachText()
            ?.joinToString { it.trimStart('#') }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("nav.pagination:first-of-type a")
        .map(::chapterFromElement)
        .reversed()

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val text = element.text()
        name = "Page $text"
        chapter_number = text.toFloatOrNull() ?: 1F
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("div.article-fulltext img[src]")
        .mapIndexed { index, item -> Page(index, imageUrl = item.absUrl("src")) }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select("div.bottom-articles .items-row")
        .map(::mangaFromElement)

    private fun getPage(page: Int) = (page - 1) * 20
}
