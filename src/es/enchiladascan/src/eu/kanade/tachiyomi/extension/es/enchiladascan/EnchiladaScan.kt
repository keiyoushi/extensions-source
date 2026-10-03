package eu.kanade.tachiyomi.extension.es.enchiladascan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Source
abstract class EnchiladaScan : KeiSource() {

    override val supportsLatest = false

    private val domainUrl: String
        get() = baseUrl.toHttpUrl().let { "${it.scheme}://${it.host}" }

    private var catalog: List<Manga>? = null

    private suspend fun fetchCatalog(): List<Manga> = catalog
        ?: client.get("$baseUrl/catalogo.json").parseAs<Catalog>().items.also { catalog = it }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangaList = fetchCatalog().map { it.toSManga(baseUrl) }
        return MangasPage(mangaList, false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangaList = fetchCatalog()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSManga(baseUrl) }

        return MangasPage(mangaList, false)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val container = document.selectFirst("main.container")!!

        manga.apply {
            title = container.selectFirst(".manga-title")!!.text()
            thumbnail_url = container.selectFirst(".manga-cover img")!!.attr("abs:src")
            author = container.selectFirst(".manga-meta-list > li:contains(Autor)")?.ownText()
            artist = container.selectFirst(".manga-meta-list > li:contains(Arte)")?.ownText()
            genre = container.selectFirst(".manga-meta-list > li:contains(Género)")?.ownText()
            status = parseStatus(container.selectFirst(".manga-meta-list > li:contains(Estado)")?.ownText())
            description = container.selectFirst(".manga-sinopsis")?.text()
        }

        val chapterList = document.select("ul#chaptersList > li").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                name = element.selectFirst(".cap-title")!!.text()
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter) = "$domainUrl${chapter.url}"

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (domainUrl + chapter.url.removeSuffix("/")).toHttpUrl()
        val segments = url.pathSegments
        val mangaSlug = segments[segments.size - 2]
        val chapterSlug = segments.last()

        return client.get("$baseUrl/assets/mangas/$mangaSlug/$chapterSlug/images.json")
            .parseAs<List<String>>()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    // Google Drive answers 403 when an Origin header is present
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Origin")
        .build()

    // ============================= Utilities =============================

    private fun parseStatus(text: String?): Int = when (text?.lowercase()) {
        "en publicación" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
