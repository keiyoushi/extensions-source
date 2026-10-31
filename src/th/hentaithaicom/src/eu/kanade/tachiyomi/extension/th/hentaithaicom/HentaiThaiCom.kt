package eu.kanade.tachiyomi.extension.th.hentaithai

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class HentaiThaiCom : KeiSource() {

    private val itemsPerPage = 24

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/top-doujin/").asJsoup()
        return MangasPage(document.select("a.doujin-item").map(::listingParse), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val startId = getStartId()
        val url = "$baseUrl/next-${startId - (page - 1) * itemsPerPage}"
        val document = client.get(url).asJsoup()
        val mangas = document.select("a.doujin-item").map(::listingParse)
        return MangasPage(mangas, mangas.size >= itemsPerPage)
    }

    private var startIdCache: Int? = null

    private suspend fun getStartId(): Int {
        startIdCache?.let { return it }
        val html = client.get(baseUrl).asJsoup().html()
        val id = Regex("""/next-(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        startIdCache = id
        return id
    }

    private fun listingParse(element: Element): SManga = SManga.create().apply {
        url = element.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = element.attr("title").ifBlank {
            element.selectFirst(".doujin-cover img")?.attr("alt").orEmpty()
        }
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst(".doujin-cover img")?.attr("src")?.ifEmpty { null }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException("Search is not available")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/${manga.url}").asJsoup()

        val updatedManga = manga.apply {
            val rawTitle = document.selectFirst("title")?.text()?.trim().orEmpty()
            check(rawTitle.isNotBlank()) { "Empty title for ${manga.url}" }
            title = rawTitle
            thumbnail_url = document.select("section#image-container figure.manga-page img")
                .firstOrNull { it.attr("src").contains("/thai/") }
                ?.attr("src")?.ifEmpty { null }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val updatedChapters = listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Oneshot"
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/${chapter.url}").asJsoup()
        .select("section#image-container figure.manga-page img")
        .map { it.attr("src") }
        .filter { it.contains("/thai/") }
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
