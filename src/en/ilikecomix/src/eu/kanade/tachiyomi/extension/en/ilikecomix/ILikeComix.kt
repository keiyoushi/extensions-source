package eu.kanade.tachiyomi.extension.en.ilikecomix

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class ILikeComix : KeiSource() {

    override val supportsLatest = false

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/king-porn-comics/" else "$baseUrl/king-porn-comics/page/$page/"
        val document = client.get(url).asJsoup()
        val mangas = document.select("article").mapNotNull(::parseEntry)
        return MangasPage(mangas, document.selectFirst("a.nextp") != null)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return MangasPage(emptyList(), false)
        val url = baseUrl.toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegments("page/$page") }
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("article").mapNotNull(::parseEntry)
        return MangasPage(mangas, document.selectFirst("a.nextp") != null)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val manga = SManga.create().apply { this.url = url.encodedPath.removePrefix("/") }
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val title = document.selectFirst("h1.post-title")?.text().orEmpty().trim()
        if (title.isBlank()) return null
        return parseMangaDetails(document, manga)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = parseMangaDetails(document, manga)
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Oneshot"
            chapter_number = 1f
        }
        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        val rawTitle = document.selectFirst("h1.post-title")?.text().orEmpty().trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = document.selectFirst("meta[property='og:image']")
            ?.attr("content")?.ifEmpty { null }
        description = document.selectFirst("meta[property='og:description']")
            ?.attr("content")?.ifEmpty { null }
        genre = document.select("a[rel='tag']").eachText()
            .joinToString(", ").ifEmpty { null }
        status = SManga.COMPLETED
    }

    private fun parseEntry(element: Element): SManga? {
        val link = element.selectFirst("h2.post-title a") ?: return null
        val rawTitle = link.text().trim()
        if (rawTitle.isBlank()) return null
        return SManga.create().apply {
            url = link.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
            title = rawTitle
            thumbnail_url = element.selectFirst("figure.post-image img")
                ?.attr("abs:src")?.ifEmpty { null }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val figures = document.select("figure[data-pswp-src]")
            .map { it.attr("abs:data-pswp-src") }
        val images = document.select(".entry-content img")
            .mapNotNull { img ->
                img.attr("abs:data-pswp-src")
                    .ifBlank { img.attr("abs:data-src") }
                    .ifBlank { img.attr("abs:data-lazy-src") }
                    .ifBlank { img.attr("abs:src") }
                    .ifBlank { null }
            }
        return (figures + images)
            .filter { it.startsWith("http") && "/comic/" in it }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}
