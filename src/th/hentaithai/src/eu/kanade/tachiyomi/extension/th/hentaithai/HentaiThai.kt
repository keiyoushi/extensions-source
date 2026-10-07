package eu.kanade.tachiyomi.extension.th.hentaithai

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class HentaiThai : KeiSource() {

    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/top-doujin/").asJsoup()
        return MangasPage(document.select("a.doujin-item").map(::listingParse), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/").asJsoup()
        return MangasPage(document.select("a.doujin-item").map(::listingParse), false)
    }

    private fun listingParse(element: Element): SManga = SManga.create().apply {
        url = element.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        title = element.attr("title").ifBlank {
            element.selectFirst(".doujin-cover img")?.attr("alt").orEmpty()
        }
        thumbnail_url = element.selectFirst(".doujin-cover img")?.attr("src")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException("Search Feature won't work in extension")

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
                ?.attr("src")
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
