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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Kiutaku : KeiSource() {
    // Image host (mitaku.net) blocks hotlinking with a kiutaku.com Referer
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Referer")

    // ============================== Popular ===============================
    private fun getPage(page: Int) = (page - 1) * 20

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/hot?start=${getPage(page)}").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.blog > div.items-row").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("nav > a.pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.item-link")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        title = element.selectFirst("h2")?.text() ?: "Cosplay"
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/?start=${getPage(page)}").asJsoup())

    // =============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.first()
        val document = client.get("$baseUrl/$id").asJsoup()
        return mangaDetailsParse(
            document,
            SManga.create().apply {
                this.url = "/$id"
                initialized = true
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("start", getPage(page).toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = mangaDetailsParse(document, manga)

        val chapterList = document
            .select("nav.pagination:first-of-type a")
            .map(::chapterFromElement)
            .reversed()

        return SMangaUpdate(details, chapterList)
    }

    private fun mangaDetailsParse(document: Document, manga: SManga) = manga.apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        title = document.selectFirst("div.article-header")!!.text()
        genre = document.selectFirst("div.article-tags")
            ?.select("a.tag > span")
            ?.eachText()
            ?.joinToString { it.trimStart('#') }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select("div.bottom-articles .items-row")
        .map(::mangaFromElement)

    // ============================== Chapters ==============================
    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val text = element.text()
        name = "Page $text"
        chapter_number = text.toFloatOrNull() ?: 1F
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("div.article-fulltext img[src]")
        .mapIndexed { index, item ->
            Page(index, imageUrl = item.absUrl("src"))
        }
}
