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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Kiutaku : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    // ============================== Popular ===============================
    private fun getPage(page: Int) = (page - 1) * 20

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/hot".toHttpUrl().newBuilder()
            .addQueryParameter("start", getPage(page).toString())
            .build()
        val document = client.get(url).asJsoup()
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
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("start", getPage(page).toString())
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.blog > div.items-row").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("nav > a.pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.startsWith(PREFIX_SEARCH)) {
            val id = query.removePrefix(PREFIX_SEARCH)
            return MangasPage(listOf(getMangaByUrl("$baseUrl/$id".toHttpUrl())!!), false)
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("start", getPage(page).toString())
            .build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("div.blog > div.items-row").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("nav > a.pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) throw Exception("Unsupported url")
        val id = url.pathSegments.first()
        val document = client.get("$baseUrl/$id").asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain("$baseUrl/$id")
            initialized = true
        }
    }

    // =========================== Manga Details ============================
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
        title = document.selectFirst("div.article-header")?.text() ?: "Cosplay"
        genre = document.selectFirst("div.article-tags")
            ?.select("a.tag > span")
            ?.eachText()
            ?.joinToString { it.trimStart('#') }
    }

    // ============================== Chapters ==============================
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

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("div.article-fulltext img[src]")
        .mapIndexed { index, item -> Page(index, imageUrl = item.absUrl("src")) }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
