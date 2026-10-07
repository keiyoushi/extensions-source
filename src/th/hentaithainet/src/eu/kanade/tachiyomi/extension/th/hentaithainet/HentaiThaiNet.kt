package eu.kanade.tachiyomi.extension.th.hentaithainet

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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiThaiNet : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page-$page"
        val document = client.get(url).asJsoup()
        val thumbnails = document.parseThumbnails()
        val mangas = document.select("a.col-6[href*=\"/t\"]").map { it.listingParse(thumbnails) }
        val hasNextPage = document.selectFirst("a[href*=\"page-${page + 1}\"]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    private fun Document.parseThumbnails(): Map<String, String> = select("style").flatMap { style ->
        THUMBNAIL_REGEX.findAll(style.data()).map { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }.toMap()

    private fun Element.listingParse(thumbnails: Map<String, String>): SManga = SManga.create().apply {
        url = attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = attr("title").ifBlank {
            selectFirst("h3.font_name")?.text().orEmpty()
        }.trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        val postClass = classNames().firstOrNull { it.startsWith("post_") }
            ?: selectFirst("[class*=\"post_\"]")?.classNames()?.firstOrNull { it.startsWith("post_") }
        thumbnail_url = postClass?.let { thumbnails[it] }?.ifEmpty { null }
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
            thumbnail_url = document.select("img[src*=\"/thai/\"]")
                .firstOrNull()
                ?.attr("src")?.ifEmpty { null }
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
        .select("img[src*=\"/thai/\"]")
        .map { it.attr("src") }
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    companion object {
        private val THUMBNAIL_REGEX = Regex("""\.(post_\d+)\s*\{\s*background-image:\s*url\('([^']+)'\)""")
    }
}
