package eu.kanade.tachiyomi.extension.pt.hentaicomics

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
abstract class HentaiComics : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/top-hentais/").asJsoup()
        return MangasPage(document.select("article.w_item_b").map(::listingParse), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.post").map(::postParse)
        val hasNextPage = document.selectFirst("a[href*=\"/page/${page + 1}/\"]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun postParse(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href]")!!
        url = link.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = link.attr("title").ifBlank {
            element.selectFirst("h2.link")?.text().orEmpty()
        }.trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst("img")?.attr("src")?.ifEmpty { null }
    }

    private fun listingParse(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href]")!!
        url = link.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = link.attr("title").ifBlank {
            element.selectFirst("h2")?.text().orEmpty()
        }.trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst(".image img")?.attr("src")?.ifEmpty { null }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()
        return MangasPage(document.select("div.post").map(::postParse), false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/${manga.url}").asJsoup()

        val updatedManga = manga.apply {
            val rawTitle = document.selectFirst("h1.post-title")?.text()?.trim().orEmpty()
            check(rawTitle.isNotBlank()) { "Empty title for ${manga.url}" }
            title = rawTitle
            thumbnail_url = document.select("div.single-post img[src*=\"/wp-content/uploads/\"]")
                .firstOrNull()
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
        .select("div.single-post img[src*=\"/wp-content/uploads/\"]")
        .map { it.attr("src") }
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
