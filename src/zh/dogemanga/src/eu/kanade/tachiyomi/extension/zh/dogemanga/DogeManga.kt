package eu.kanade.tachiyomi.extension.zh.dogemanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class DogeManga : KeiSource() {

    // The hot and latest lists only go on from the last series of the previous page
    private var popularNextUrl: String? = null
    private var latestNextUrl: String? = null

    // Cloudflare answers 429 when browsing quickly, covers and pages included
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 5)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = getCards(if (page == 1) "$baseUrl/_search" else popularNextUrl!!)
        popularNextUrl = result.next
        return result.toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = getCards(if (page == 1) "$baseUrl/_search?s=1" else latestNextUrl!!)
        latestNextUrl = result.next
        return result.toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/_search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .apply { if (page > 1) addQueryParameter("o", ((page - 1) * SEARCH_PAGE_SIZE).toString()) }
            .build()
        return getCards(url.toString()).toMangasPage()
    }

    private suspend fun getCards(url: String): SearchDto = client.get(url, Fetch.XHR).parseAs()

    private fun SearchDto.toMangasPage(): MangasPage {
        val mangas = Jsoup.parseBodyFragment(mangaCards.joinToString(""), baseUrl).select(".site-card[data-manga-id]")
            .mapNotNull { card ->
                // Series the site no longer shows keep an empty card
                val title = card.selectFirst(".site-card__manga-title")?.text() ?: return@mapNotNull null
                SManga.create().apply {
                    url = card.attr("data-manga-id")
                    this.title = title
                    thumbnail_url = card.selectFirst("img.card-img-top")?.absUrl("src")
                }
            }
        return MangasPage(mangas, next != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "m") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return fetchMangaPage(id).toSManga(id)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/m/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = fetchMangaPage(manga.url)
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    // The interface language follows Accept-Language unless set: keep the status labels in English
    private suspend fun fetchMangaPage(id: String): Document = client.get("$baseUrl/m/$id?l=en", Fetch.DOCUMENT).asJsoup()

    private fun Document.toSManga(id: String) = SManga.create().apply {
        val card = selectFirst(".site-main-content > .site-card")!!
        url = id
        title = card.selectFirst(".site-card__manga-title")!!.text()
        author = card.select("h4 a").joinToString { it.text() }.ifEmpty { null }
        description = card.selectFirst(".site-card__brief")?.text()
        thumbnail_url = card.selectFirst("img.site-manga__cover-image")?.absUrl("src")
        val info = card.selectFirst("small")?.text().orEmpty()
        status = when {
            "status: Ongoing" in info -> SManga.ONGOING
            "status: Finished" in info -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.toSChapters() = select("#site-manga__tab-pane-all .site-manga-thumbnail__link").map { link ->
        SChapter.create().apply {
            // The links carry the interface language of the page: keep only the page id
            url = link.absUrl("href").toHttpUrl().pathSegments.last()
            name = link.selectFirst("span.text-center")!!.text()
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/p/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.site-reader__image[data-page-image-url]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-page-image-url")) }

    companion object {
        private const val SEARCH_PAGE_SIZE = 24
    }
}
